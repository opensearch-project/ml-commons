/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;

import java.util.HashSet;
import java.util.List;

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
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
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

    private volatile List<String> trustedConnectorEndpointsRegex;

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
        if (mlCreateConnectorInput.isDryRun()) {
            MLCreateConnectorResponse response = new MLCreateConnectorResponse(MLCreateConnectorInput.DRY_RUN_CONNECTOR_NAME);
            listener.onResponse(response);
            return;
        }
        String connectorName = mlCreateConnectorInput.getName();
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            mlCreateConnectorInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
            RestActionUtils.connectorValidationInManagedServiceWithCreateConnectorInput(mlCreateConnectorInput);
            Connector connector = Connector.createConnector(builder, mlCreateConnectorInput.getProtocol());
            connector.validateConnectorURL(trustedConnectorEndpointsRegex);

            User user = RestActionUtils.getUserContext(client);
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
        } catch (Exception e) {
            log.error("Failed to create connector " + connectorName, e);
            listener.onFailure(e);
        }
    }

    private void indexConnector(Connector connector, ActionListener<MLCreateConnectorResponse> listener) {
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
            if (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(isAddAllBackendRoles)) {
                input.setAccess(AccessMode.RESTRICTED);
                accessMode = AccessMode.RESTRICTED;
            } else {
                input.setAccess(AccessMode.PRIVATE);
                accessMode = AccessMode.PRIVATE;
            }
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
        if (input.getAccess() != null || input.getAddAllBackendRoles() != null || !CollectionUtils.isEmpty(input.getBackendRoles())) {
            throw new IllegalArgumentException(
                "You cannot specify connector access control parameters because the Security plugin or connector access control is disabled on your cluster."
            );
        }
    }

}
