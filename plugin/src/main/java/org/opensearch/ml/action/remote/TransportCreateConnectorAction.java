/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.template.APISchema;
import org.opensearch.ml.common.connector.template.DetachedConnector;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.exceptions.MetaDataException;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportCreateConnectorAction extends HandledTransportAction<ActionRequest, MLCreateConnectorResponse> {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final MLEngine mlEngine;
    private final MLModelManager mlModelManager;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;

    private String trustedConnectorEndpointsRegex;

    @Inject
    public TransportCreateConnectorAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        MLEngine mlEngine,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        Settings settings,
        ClusterService clusterService,
        MLModelManager mlModelManager
    ) {
        super(MLCreateConnectorAction.NAME, transportService, actionFilters, MLCreateConnectorRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.mlEngine = mlEngine;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlModelManager = mlModelManager;
        trustedConnectorEndpointsRegex = ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX, it -> trustedConnectorEndpointsRegex = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateConnectorResponse> listener) {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.fromActionRequest(request);
        MLCreateConnectorInput mlCreateConnectorInput = mlCreateConnectorRequest.getMlCreateConnectorInput();
        if (MLCreateConnectorInput.DRY_RUN_CONNECTOR_NAME.equals(mlCreateConnectorInput.getName())) {
            MLCreateConnectorResponse response = new MLCreateConnectorResponse(
                MLCreateConnectorInput.DRY_RUN_CONNECTOR_NAME
            );
            listener.onResponse(response);
            return;
        }
        String connectorName = mlCreateConnectorInput.getName();
        try {
            if (mlCreateConnectorInput.getConnectorTemplate() == null) {
                throw new IllegalArgumentException("Invalid Connector template, Actions are missing");
            }
            validateConnectorURL(mlCreateConnectorInput);
            User user = RestActionUtils.getUserContext(client);
            Instant now = Instant.now();
            DetachedConnector connector = DetachedConnector
                .builder()
                .name(connectorName)
                .version(mlCreateConnectorInput.getVersion())
                .description(mlCreateConnectorInput.getDescription())
                .protocol(mlCreateConnectorInput.getProtocol())
                .parameterStr(toJson(mlCreateConnectorInput.getParameters()))
                .credentialStr(toJson(mlCreateConnectorInput.getCredential()))
                .predictAPI(getAPIStringValue(mlCreateConnectorInput.getConnectorTemplate().getPredictSchema()))
                .metadataAPI(getAPIStringValue(mlCreateConnectorInput.getConnectorTemplate().getMetadataSchema()))
                .createdTime(now)
                .lastUpdateTime(now)
                .build();
            if (connectorAccessControlHelper.accessControlNotEnabled(user)) {
                validateSecurityDisabledOrConnectorAccessControlDisabled(mlCreateConnectorInput);
                indexConnector(connector, listener);
            } else {
                validateRequest4AccessControl(mlCreateConnectorInput, user);
                if (Boolean.TRUE.equals(mlCreateConnectorInput.getAddAllBackendRoles())) {
                    mlCreateConnectorInput.setBackendRoles(user.getBackendRoles());
                }
                connector.setBackendRoles(mlCreateConnectorInput.getBackendRoles());
                connector.setOwner(user);
                connector.setAccess(mlCreateConnectorInput.getAccess());
                indexConnector(connector, listener);
            }
        } catch (MetaDataException e) {
            log.error("The masterKey for credential encryption is missing in connector creation");
            listener.onFailure(e);
        }
        catch (Exception e) {
            log.error("Failed to create connector " + connectorName, e);
            listener.onFailure(e);
        }
    }

    private void indexConnector(DetachedConnector connector, ActionListener<MLCreateConnectorResponse> listener) {
        mlModelManager.checkMasterKey(mlEngine);
        connector.encrypt(mlEngine::encrypt);
        log.info("connector created, indexing into the connector system index");
        mlIndicesHandler.initMLConnectorIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                listener.onFailure(new RuntimeException("No response to create ML Connector index"));
                return;
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<IndexResponse> indexResponseListener = ActionListener.wrap(r -> {
                    log.info("Connector saved into index, result:{}, connector id: {}", r.getResult(), r.getId());
                    MLCreateConnectorResponse response = new MLCreateConnectorResponse(r.getId());
                    listener.onResponse(response);
                }, listener::onFailure);

                IndexRequest indexRequest = new IndexRequest(ML_CONNECTOR_INDEX);
                indexRequest.source(connector.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.runBefore(indexResponseListener, context::restore));
            } catch (Exception e) {
                log.error("Failed to save ML connector", e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to init ML connector index", e);
            listener.onFailure(e);
        }));
    }

    private void validateRequest4AccessControl(MLCreateConnectorInput input, User user) {
        Boolean isAddAllBackendRoles = input.getAddAllBackendRoles();
        if (connectorAccessControlHelper.isAdmin(user)) {
            if (Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("Admin can't add all backend roles");
            }
        }
        AccessMode accessMode = input.getAccess();
        if (accessMode == null) {
            input.setAccess(AccessMode.RESTRICTED);
            accessMode = AccessMode.RESTRICTED;
        }
        if (AccessMode.PUBLIC == accessMode || AccessMode.PRIVATE == accessMode) {
            if (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("You can specify backend roles only for a connector with the restricted access mode.");
            }
        }
        if (AccessMode.RESTRICTED == accessMode) {
            if (Boolean.TRUE.equals(isAddAllBackendRoles)) {
                if (!CollectionUtils.isEmpty(input.getBackendRoles())) {
                    throw new IllegalArgumentException("You can't specify backend roles and add all backend roles to true at same time.");
                }
                if (CollectionUtils.isEmpty(user.getBackendRoles())) {
                    throw new IllegalArgumentException("You must have at least one backend role to create a connector.");
                }
            } else {
                // check backend_roles parameter
                if (CollectionUtils.isEmpty(input.getBackendRoles())) {
                    throw new IllegalArgumentException(
                        "You must specify at least one backend role or make the connector public/private for registering it."
                    );
                } else if (!connectorAccessControlHelper.isAdmin(user)
                    && !new HashSet<>(user.getBackendRoles()).containsAll(input.getBackendRoles())) {
                    throw new IllegalArgumentException("You don't have the backend roles specified.");
                }
            }
        }
    }

    private void validateSecurityDisabledOrConnectorAccessControlDisabled(MLCreateConnectorInput input) {
        if (input.getAccess() != null || input.getAddAllBackendRoles() != null || input.getBackendRoles() != null) {
            throw new IllegalArgumentException(
                "You cannot specify connector access control parameters because the Security plugin or connector access control is disabled on your cluster."
            );
        }
    }

    private String getAPIStringValue(APISchema apiSchema) {
        if (apiSchema == null) {
            return null;
        }
        return apiSchema.toString();
    }

    private void validateConnectorURL(MLCreateConnectorInput mlCreateConnectorInput) {
        Map<String, String> parameters = mlCreateConnectorInput.getParameters();

        String predictAPISchema = getAPIStringValue(mlCreateConnectorInput.getConnectorTemplate().getPredictSchema());
        String metadataAPISchema = getAPIStringValue(mlCreateConnectorInput.getConnectorTemplate().getMetadataSchema());
        Map<String, String> predictAPIMap = StringUtils.fromJson(predictAPISchema);
        Map<String, String> metadataAPIMap = StringUtils.fromJson(metadataAPISchema);
        String predictUrl = predictAPIMap.get(APISchema.URL_FIELD);
        String metadataUrl = metadataAPIMap.get(APISchema.URL_FIELD);

        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        String finalPredictUrl = substitutor.replace(predictUrl);
        String finalMetadataUrl = substitutor.replace(metadataUrl);

        Pattern pattern = Pattern.compile(trustedConnectorEndpointsRegex);
        Matcher predictUrlMatcher = pattern.matcher(finalPredictUrl);
        Matcher metadataUrlMatcher = pattern.matcher(finalMetadataUrl);

        if (!predictUrlMatcher.matches()) {
            throw new IllegalArgumentException(
                "Connector URL is not matching the trusted connector endpoint regex, regex is: "
                    + trustedConnectorEndpointsRegex
                    + ",URL is: "
                    + finalPredictUrl
            );
        }
        if (!metadataUrlMatcher.matches()) {
            throw new IllegalArgumentException(
                "Connector URL is not matching the trusted connector endpoint regex, regex is: "
                    + trustedConnectorEndpointsRegex
                    + ",URL is: "
                    + finalMetadataUrl
            );
        }
    }
}
