/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRemoveOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpRemoveNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpRemoveNodesResponse;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import io.modelcontextprotocol.spec.McpError;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;

/**
 * This class is responsible for removing MCP tools on nodes in the cluster.
 * It extends the TransportNodesAction class and handles the removal of MCP tools
 */
@Log4j2
public class TransportMcpToolsRemoveOnNodesAction extends
    TransportNodesAction<MLMcpToolsRemoveNodesRequest, MLMcpRemoveNodesResponse, MLMcpToolsRemoveNodeRequest, MLMcpRemoveNodeResponse> {
    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportMcpToolsRemoveOnNodesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(
            MLMcpToolsRemoveOnNodesAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLMcpToolsRemoveNodesRequest::new,
            MLMcpToolsRemoveNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLMcpRemoveNodeResponse.class
        );
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected MLMcpRemoveNodesResponse newResponse(
        MLMcpToolsRemoveNodesRequest nodesRequest,
        List<MLMcpRemoveNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLMcpRemoveNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLMcpToolsRemoveNodeRequest newNodeRequest(MLMcpToolsRemoveNodesRequest request) {
        return new MLMcpToolsRemoveNodeRequest(request.getTools());
    }

    @Override
    protected MLMcpRemoveNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLMcpRemoveNodeResponse(in);
    }

    @Override
    protected MLMcpRemoveNodeResponse nodeOperation(MLMcpToolsRemoveNodeRequest request) {
        return removeMcpTools(request.getTools());
    }

    private MLMcpRemoveNodeResponse removeMcpTools(List<String> tools) {
        AtomicReference<List<String>> errors = new AtomicReference<>();
        errors.set(new ArrayList<>());
        Flux.fromStream(tools.stream()).flatMap(toolName -> McpAsyncServerHolder.asyncServer.removeTool(toolName).doOnError(e -> {
            errors.get().add(toolName);
        })).doOnError(e -> { log.error(e.getMessage(), e); }).doOnComplete(() -> {
            log.debug("Successfully removed tools on node: {}", clusterService.localNode().getId());
        }).subscribe();
        if (!errors.get().isEmpty()) {
            String errMsg = String.format(Locale.ROOT, "Tools: %s not found", errors.get());
            throw new FailedNodeException(clusterService.localNode().getId(), errMsg, new McpError(errMsg));
        } else {
            return new MLMcpRemoveNodeResponse(clusterService.localNode(), true);
        }
    }

}
