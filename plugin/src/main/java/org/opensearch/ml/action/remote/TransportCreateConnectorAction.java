/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.connector.template.DetachedConnector.CONNECTOR_PROTOCOL_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.toJson;

import java.time.Instant;
import java.util.HashSet;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.template.ConnectorState;
import org.opensearch.ml.common.connector.template.DetachedConnector;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportCreateConnectorAction extends HandledTransportAction<ActionRequest, MLCreateConnectorResponse> {
    public static final String CONNECTOR_NAME_FIELD = "connector_name";
    public static final String CONNECTOR_DESCRIPTION_FIELD = "description";
    public static final String CONNECTOR_VERSION_FIELD = "version";
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final MLEngine mlEngine;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public TransportCreateConnectorAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        MLEngine mlEngine,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLCreateConnectorAction.NAME, transportService, actionFilters, MLCreateConnectorRequest::new);
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.mlEngine = mlEngine;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateConnectorResponse> listener) {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.fromActionRequest(request);
        MLCreateConnectorInput mlCreateConnectorInput = mlCreateConnectorRequest.getMlCreateConnectorInput();
        if (mlCreateConnectorInput.getConnectorTemplate() == null) {
            throw new IllegalArgumentException("Invalid Connector template, APIs are missing");
        }
        String connectorName = mlCreateConnectorInput.getMetadata().get(CONNECTOR_NAME_FIELD);
        try {
            User user = RestActionUtils.getUserContext(client);
            Instant now = Instant.now();
            DetachedConnector connector = DetachedConnector
                .builder()
                .name(connectorName)
                .version(mlCreateConnectorInput.getMetadata().get(CONNECTOR_VERSION_FIELD))
                .description(mlCreateConnectorInput.getMetadata().get(CONNECTOR_DESCRIPTION_FIELD))
                .protocol(mlCreateConnectorInput.getMetadata().get(CONNECTOR_PROTOCOL_FIELD))
                .parameterStr(toJson(mlCreateConnectorInput.getParameters()))
                .credentialStr(toJson(mlCreateConnectorInput.getCredential()))
                .predictAPI(mlCreateConnectorInput.getConnectorTemplate().getPredictSchema().toString())
                .metadataAPI(mlCreateConnectorInput.getConnectorTemplate().getMetadataSchema().toString())
                .connectorState(ConnectorState.CREATED)
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
        } catch (IllegalArgumentException illegalArgumentException) {
            log.error("Failed to create connector " + connectorName, illegalArgumentException);
            listener.onFailure(illegalArgumentException);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error("Failed to create connector " + connectorName, e);
            listener.onFailure(e);
        }
    }

    private void indexConnector(DetachedConnector connector, ActionListener<MLCreateConnectorResponse> listener) {
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
                    MLCreateConnectorResponse response = new MLCreateConnectorResponse(r.getId(), ConnectorState.CREATED.name());
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
        } else {
            AccessMode accessMode = input.getAccess();
            if (accessMode == null) {
                input.setAccess(AccessMode.RESTRICTED);
                accessMode = AccessMode.RESTRICTED;
            }
            if (AccessMode.PUBLIC == accessMode || AccessMode.PRIVATE == accessMode) {
                if (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(isAddAllBackendRoles)) {
                    throw new IllegalArgumentException(
                        "You can specify backend roles only for a connector with the restricted access mode."
                    );
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
                        throw new IllegalArgumentException("You must specify at least one backend role or make the connector public/private for registering it.");
                    } else if (!new HashSet<>(user.getBackendRoles()).containsAll(input.getBackendRoles())) {
                        throw new IllegalArgumentException("You don't have the backend roles specified.");
                    }
                }
            }
        }
    }

    private void validateSecurityDisabledOrConnectorAccessControlDisabled(MLCreateConnectorInput input) {
        if (input.getAccess() != null || input.getAddAllBackendRoles() != null || input.getBackendRoles() != null) {
            throw new IllegalArgumentException(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
            );
        }
    }
}
