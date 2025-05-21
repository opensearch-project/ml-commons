/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.OpenSearchException;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsUpdateOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.UpdateMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for Updating tools on nodes.
 */
@Log4j2
public class TransportMcpToolsUpdateOnNodesAction extends
    TransportNodesAction<MLMcpToolsUpdateNodesRequest, MLMcpToolsUpdateNodesResponse, MLMcpToolsUpdateNodeRequest, MLMcpToolsUpdateNodeResponse> {
    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    ToolFactoryWrapper toolFactoryWrapper;
    McpToolsHelper mcpToolsIndexSearchHelper;

    @Inject
    public TransportMcpToolsUpdateOnNodesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ToolFactoryWrapper toolFactoryWrapper,
        McpToolsHelper mcpToolsIndexSearchHelper
    ) {
        super(
            MLMcpToolsUpdateOnNodesAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLMcpToolsUpdateNodesRequest::new,
            MLMcpToolsUpdateNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLMcpToolsUpdateNodeResponse.class
        );
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.toolFactoryWrapper = toolFactoryWrapper;
        this.mcpToolsIndexSearchHelper = mcpToolsIndexSearchHelper;
    }

    @Override
    protected MLMcpToolsUpdateNodesResponse newResponse(
        MLMcpToolsUpdateNodesRequest nodesRequest,
        List<MLMcpToolsUpdateNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLMcpToolsUpdateNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLMcpToolsUpdateNodeRequest newNodeRequest(MLMcpToolsUpdateNodesRequest request) {
        return new MLMcpToolsUpdateNodeRequest(request.getMcpTools());
    }

    @Override
    protected MLMcpToolsUpdateNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLMcpToolsUpdateNodeResponse(in);
    }

    @Override
    protected MLMcpToolsUpdateNodeResponse nodeOperation(MLMcpToolsUpdateNodeRequest request) {
        return UpdateToolsOnNode(request.getMcpTools());
    }

    /**
     * The underlying MCP SDK guaranteed the multi-thread safety for addTool.
     * The coordinator might fail to receive the Update response due to network issue, we need to handle the retry request from coordinator
     * by remove the tools first then Update.
     * @param mcpTools
     * @return
     */
    private MLMcpToolsUpdateNodeResponse UpdateToolsOnNode(List<UpdateMcpTool> mcpTools) {
        AtomicReference<Throwable> exception = new AtomicReference<>();
        Flux.fromStream(mcpTools.stream()).flatMap(tool -> {
            McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.remove(tool.getName());
            McpAsyncServerHolder.getMcpAsyncServerInstance().removeTool(tool.getName()).onErrorResume(e -> Mono.empty()).subscribe();
            McpAsyncServerHolder
                .getMcpAsyncServerInstance()
                .addTool(mcpToolsIndexSearchHelper.createToolSpecification(tool))
                .doOnSuccess(x -> McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put(tool.getName(), tool.getVersion()))
                .doOnError(e -> {
                    log
                        .error(
                            "Failed to Update tool: {} in MCP server memory on node: {}",
                            tool.getName(),
                            clusterService.localNode().getId()
                        );
                })
                .subscribe();

            return Mono.empty();
        }).doOnComplete(() -> { log.debug("Successfully Update tools on node: {}", clusterService.localNode().getId()); }).doOnError(e -> {
            exception.set(e);
            log.error("Failed to Update tools on node: {}", clusterService.localNode().getId(), e);
        }).subscribe();
        if (exception.get() != null) {
            String errorMsg = exception.get().getMessage();
            throw new FailedNodeException(clusterService.localNode().getId(), errorMsg, new OpenSearchException(errorMsg));
        }
        return new MLMcpToolsUpdateNodeResponse(clusterService.localNode(), true);
    }

}
