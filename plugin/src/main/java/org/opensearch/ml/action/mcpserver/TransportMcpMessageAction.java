/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.threadpool.ThreadPool.Names.SAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
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
    DiscoveryNodeHelper nodeFilter;

    @Inject
    public TransportMcpMessageAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter
    ) {
        super(MLMcpMessageAction.NAME, transportService, actionFilters, MLMcpMessageRequest::new);
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

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            Map<String, DiscoveryNode> nodes = new HashMap<>(clusterService.state().nodes().getNodes());
            nodes.remove(clusterService.localNode().getId());
            transportService
                .sendRequest(
                    clusterService.state().nodes().getNodes().get(nodes.keySet().stream().findFirst().get()),
                    MLMcpMessageDispatchAction.NAME,
                    mlMcpMessageRequest,
                    new TransportResponseHandler<AcknowledgedResponse>() {
                        @Override
                        public AcknowledgedResponse read(StreamInput streamInput) throws IOException {
                            return new AcknowledgedResponse(streamInput);
                        }

                        @Override
                        public void handleResponse(AcknowledgedResponse acknowledgedResponse) {
                            listener.onResponse(acknowledgedResponse);
                        }

                        @Override
                        public void handleException(TransportException e) {
                            System.out.println("got exception:" + e.getMessage());
                            log.error("got exception: ", e);
                        }

                        @Override
                        public String executor() {
                            return SAME;
                        }
                    }
                );

        } catch (Exception e) {
            listener.onFailure(e);
        }

    }
}
