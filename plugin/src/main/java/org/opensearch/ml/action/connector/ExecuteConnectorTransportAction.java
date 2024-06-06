/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorAction;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorRequest;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ExecuteConnectorTransportAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {

    Client client;
    ClusterService clusterService;
    ScriptService scriptService;
    NamedXContentRegistry xContentRegistry;

    ConnectorAccessControlHelper connectorAccessControlHelper;
    EncryptorImpl encryptor;

    @Inject
    public ExecuteConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        ScriptService scriptService,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        EncryptorImpl encryptor
    ) {
        super(MLExecuteConnectorAction.NAME, transportService, actionFilters, MLConnectorDeleteRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.encryptor = encryptor;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> actionListener) {
        MLExecuteConnectorRequest executeConnectorRequest = MLExecuteConnectorRequest.fromActionRequest(request);
        String connectorId = executeConnectorRequest.getConnectorId();
        String connectorAction = ConnectorAction.ActionType.EXECUTE.name();

        if (clusterService.state().metadata().hasIndex(ML_CONNECTOR_INDEX)) {
            ActionListener<Connector> listener = ActionListener.wrap(connector -> {
                if (connectorAccessControlHelper.validateConnectorAccess(client, connector)) {
                    connector.decrypt(connectorAction, (credential) -> encryptor.decrypt(credential));
                    RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                        .initInstance(connector.getProtocol(), connector, Connector.class);
                    connectorExecutor.setScriptService(scriptService);
                    connectorExecutor.setClusterService(clusterService);
                    connectorExecutor.setClient(client);
                    connectorExecutor.setXContentRegistry(xContentRegistry);
                    connectorExecutor
                        .executeAction(connectorAction, executeConnectorRequest.getMlInput(), ActionListener.wrap(taskResponse -> {
                            actionListener.onResponse(taskResponse);
                        }, e -> { actionListener.onFailure(e); }));
                }
            }, e -> {
                log.error("Failed to get connector " + connectorId, e);
                actionListener.onFailure(e);
            });
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                connectorAccessControlHelper.getConnector(client, connectorId, ActionListener.runBefore(listener, threadContext::restore));
            }
        } else {
            actionListener.onFailure(new ResourceNotFoundException("Can't find connector " + connectorId));
        }
    }

}
