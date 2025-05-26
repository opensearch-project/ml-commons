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
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRegisterOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for registering tools on nodes.
 */
@Log4j2
public class TransportMcpToolsRegisterOnNodesAction extends
    TransportNodesAction<MLMcpToolsRegisterNodesRequest, MLMcpToolsRegisterNodesResponse, MLMcpToolsRegisterNodeRequest, MLMcpToolsRegisterNodeResponse> {
    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    ToolFactoryWrapper toolFactoryWrapper;
    McpToolsHelper mcpToolsHelper;

    @Inject
    public TransportMcpToolsRegisterOnNodesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ToolFactoryWrapper toolFactoryWrapper,
        McpToolsHelper mcpToolsHelper
    ) {
        super(
            MLMcpToolsRegisterOnNodesAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLMcpToolsRegisterNodesRequest::new,
            MLMcpToolsRegisterNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLMcpToolsRegisterNodeResponse.class
        );
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.toolFactoryWrapper = toolFactoryWrapper;
        this.mcpToolsHelper = mcpToolsHelper;
    }

    @Override
    protected MLMcpToolsRegisterNodesResponse newResponse(
        MLMcpToolsRegisterNodesRequest nodesRequest,
        List<MLMcpToolsRegisterNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLMcpToolsRegisterNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLMcpToolsRegisterNodeRequest newNodeRequest(MLMcpToolsRegisterNodesRequest request) {
        return new MLMcpToolsRegisterNodeRequest(request.getMcpTools());
    }

    @Override
    protected MLMcpToolsRegisterNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLMcpToolsRegisterNodeResponse(in);
    }

    @Override
    protected MLMcpToolsRegisterNodeResponse nodeOperation(MLMcpToolsRegisterNodeRequest request) {
        return registerToolsOnNode(request.getMcpTools());
    }

    /**
     * The underlying MCP SDK guaranteed the multi-thread safety for addTool.
     * The coordinator might fail to receive the register response due to network issue, we need to handle the retry request from coordinator
     * by remove the tools first then register.
     * @param mcpTools
     * @return
     */
    private MLMcpToolsRegisterNodeResponse registerToolsOnNode(List<RegisterMcpTool> mcpTools) {
        AtomicReference<Throwable> exception = new AtomicReference<>();
        Flux.fromStream(mcpTools.stream()).flatMap(tool -> {
            if (!McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.containsKey(tool.getName())) {
                return McpAsyncServerHolder
                    .getMcpAsyncServerInstance()
                    .addTool(mcpToolsHelper.createToolSpecification(tool))
                    .doOnSuccess(x -> McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put(tool.getName(), tool.getVersion()));

            }
            return Mono.empty();
        }).doOnError(e -> {
            log
                .error(
                    "Failed to register tool: {} in MCP server memory on node: {}",
                    mcpTools.stream().map(RegisterMcpTool::getName).toList(),
                    clusterService.localNode().getId()
                );
            exception.set(e);
        }).doOnComplete(() -> { log.debug("Successfully register tools on node: {}", clusterService.localNode().getId()); }).subscribe();
        if (exception.get() != null) {
            String errorMsg = exception.get().getMessage();
            throw new FailedNodeException(clusterService.localNode().getId(), errorMsg, new OpenSearchException(errorMsg));
        }
        return new MLMcpToolsRegisterNodeResponse(clusterService.localNode(), true);
    }

}
