/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpMessageDispatchAction;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Log4j2
public class TransportMcpMessageDispatchedAction extends HandledTransportAction<ActionRequest, AcknowledgedResponse> {

    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;

    @Inject
    public TransportMcpMessageDispatchedAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter
    ) {
        super(MLMcpMessageDispatchAction.NAME, transportService, actionFilters, MLMcpMessageRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<AcknowledgedResponse> listener) {
        MLMcpMessageRequest mlMcpMessageRequest = MLMcpMessageRequest.fromActionRequest(request);
        final StreamingRestChannel channel = McpAsyncServerHolder.CHANNELS.get(mlMcpMessageRequest.getSessionId());
        Mono
            .from(
                McpAsyncServerHolder.mcpServerTransportProvider
                    .handleMessage(mlMcpMessageRequest.getSessionId(), mlMcpMessageRequest.getRequestBody())
            )
            .doOnSuccess(y -> {
                listener.onResponse(new AcknowledgedResponse(true));
            })
            .onErrorResume(e -> Mono.fromRunnable(() -> {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, new Exception(e)));
                    listener.onFailure(new Exception(e));
                } catch (IOException ex) {
                    log.error("Failed to send exception response to client during message handling due to IOException");
                }
            }))
            .subscribe();
    }

}
