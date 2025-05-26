/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;
import static org.opensearch.threadpool.ThreadPool.Names.SAME;

import java.io.IOException;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpMessageAction;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpMessageDispatchAction;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpMessageAction extends HandledTransportAction<ActionRequest, AcknowledgedResponse> {

    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    NamedXContentRegistry xContentRegistry;
    private volatile boolean mcpServerEnabled;

    @Inject
    public TransportMcpMessageAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLMcpMessageAction.NAME, transportService, actionFilters, MLMcpMessageRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<AcknowledgedResponse> listener) {
        if (!mcpServerEnabled) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        MLMcpMessageRequest mlMcpMessageRequest = MLMcpMessageRequest.fromActionRequest(request);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<AcknowledgedResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            transportService
                .sendRequest(
                    clusterService.state().nodes().getNodes().get(mlMcpMessageRequest.getNodeId()),
                    MLMcpMessageDispatchAction.NAME,
                    mlMcpMessageRequest,
                    new TransportResponseHandler<AcknowledgedResponse>() {
                        @Override
                        public AcknowledgedResponse read(StreamInput streamInput) throws IOException {
                            return new AcknowledgedResponse(streamInput);
                        }

                        @Override
                        public void handleResponse(AcknowledgedResponse acknowledgedResponse) {
                            restoreListener.onResponse(acknowledgedResponse);
                        }

                        @Override
                        public void handleException(TransportException e) {
                            log
                                .error(
                                    "Failed to process the MCP message request during sending it to corresponding node, sessionId is: {}, request is: {}",
                                    mlMcpMessageRequest.getSessionId(),
                                    mlMcpMessageRequest.getRequestBody(),
                                    e
                                );
                            restoreListener.onFailure(e);
                        }

                        @Override
                        public String executor() {
                            return SAME;
                        }
                    }
                );

        } catch (Exception e) {
            log
                .error(
                    "Failed to send the MCP message request to corresponding node, sessionId is: {}, request is: {}",
                    mlMcpMessageRequest.getSessionId(),
                    mlMcpMessageRequest.getRequestBody(),
                    e
                );
            listener.onFailure(e);
        }

    }
}
