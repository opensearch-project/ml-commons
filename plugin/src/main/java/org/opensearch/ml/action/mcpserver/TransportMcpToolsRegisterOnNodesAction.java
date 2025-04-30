/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.transport.mcpserver.requests.register.McpTool.SCHEMA_FIELD;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRegisterOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodeRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpTools;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpRegisterNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpRegisterNodesResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for registering tools on nodes.
 */
@Log4j2
public class TransportMcpToolsRegisterOnNodesAction extends
    TransportNodesAction<MLMcpToolsRegisterNodesRequest, MLMcpRegisterNodesResponse, MLMcpToolsRegisterNodeRequest, MLMcpRegisterNodeResponse> {
    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    ToolFactoryWrapper toolFactoryWrapper;

    private static final String INPUT_SCHEMA = "input_schema";

    @Inject
    public TransportMcpToolsRegisterOnNodesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ToolFactoryWrapper toolFactoryWrapper
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
            MLMcpRegisterNodeResponse.class
        );
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.toolFactoryWrapper = toolFactoryWrapper;
    }

    @Override
    protected MLMcpRegisterNodesResponse newResponse(
        MLMcpToolsRegisterNodesRequest nodesRequest,
        List<MLMcpRegisterNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLMcpRegisterNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLMcpToolsRegisterNodeRequest newNodeRequest(MLMcpToolsRegisterNodesRequest request) {
        return new MLMcpToolsRegisterNodeRequest(request.getMcpTools());
    }

    @Override
    protected MLMcpRegisterNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLMcpRegisterNodeResponse(in);
    }

    @Override
    protected MLMcpRegisterNodeResponse nodeOperation(MLMcpToolsRegisterNodeRequest request) {
        return registerToolsOnNode(request.getMcpTools());
    }

    private MLMcpRegisterNodeResponse registerToolsOnNode(McpTools mcpTools) {
        Flux.fromStream(mcpTools.getTools().stream()).flatMap(tool -> {
            // check if user request contains tools that not in our system.
            String toolName = Optional.ofNullable(tool.getName()).orElse(tool.getType());
            Tool.Factory factory = toolFactoryWrapper.getToolsFactories().get(toolName);
            Tool actualTool = factory.create(tool.getParameters());
            Map<String, Object> mSchema = Optional
                .ofNullable(tool.getAttributes())
                .map(x -> (Map<String, Object>) x.get(SCHEMA_FIELD))
                .orElse(
                    Optional
                        .ofNullable(actualTool.getAttributes())
                        .map(x -> StringUtils.fromJson(((String) x.get(INPUT_SCHEMA)), INPUT_SCHEMA))
                        .orElse(ImmutableMap.of())
                );
            String schema = StringUtils.gson.toJson(mSchema);
            String description = Optional.ofNullable(tool.getDescription()).orElse(factory.getDefaultDescription());
            McpServerFeatures.AsyncToolSpecification toolSpecification = new McpServerFeatures.AsyncToolSpecification(
                new McpSchema.Tool(toolName, String.valueOf(description), schema),
                (exchange, arguments) -> Mono.create(sink -> {
                    ActionListener<String> actionListener = ActionListener
                        .wrap(r -> sink.success(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(r)), false)), e -> {
                            log.error("Failed to execute tool, tool name: {}", toolName, e);
                            sink.error(e);
                        });
                    actualTool.run(StringUtils.getParameterMap(arguments), actionListener);
                })
            );
            return McpAsyncServerHolder.asyncServer
                .removeTool(toolName)
                .onErrorResume(e -> Mono.empty())
                .then(McpAsyncServerHolder.asyncServer.addTool(toolSpecification));
        })
            .doOnComplete(() -> { log.debug("Successfully register tools on node: {}", clusterService.localNode().getId()); })
            .doOnError(e -> {
                log.error("Failed to register tools on node: {}", clusterService.localNode().getId(), e);
            })
            .subscribe();
        return new MLMcpRegisterNodeResponse(clusterService.localNode(), true);
    }

}
