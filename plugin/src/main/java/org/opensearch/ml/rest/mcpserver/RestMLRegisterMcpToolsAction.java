/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRegisterOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpTools;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@ExperimentalApi
@Log4j2
public class RestMLRegisterMcpToolsAction extends BaseRestHandler {
    private static final String ML_REGISTER_MCP_TOOLS_ACTION = "ml_register_mcp_tools_action";
    private final Map<String, Tool.Factory> toolFactories;
    private ClusterService clusterService;
    private volatile boolean mcpServerEnabled;

    /**
     * Constructor
     */
    public RestMLRegisterMcpToolsAction(Map<String, Tool.Factory> toolFactories, ClusterService clusterService) {
        this.toolFactories = toolFactories;
        this.clusterService = clusterService;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    public String getName() {
        return ML_REGISTER_MCP_TOOLS_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/mcp/tools/_register", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mcpServerEnabled) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        ActionRequestValidationException exception = new ActionRequestValidationException();
        MLMcpToolsRegisterNodesRequest registerNodesRequest = getRequest(request);
        if (CollectionUtils.isEmpty(registerNodesRequest.getMcpTools().getTools())) {
            exception.addValidationError("tools list can not be null");
            throw exception;
        }
        Set<String> buildInToolNames = toolFactories.values().stream().map(Tool.Factory::getDefaultType).collect(Collectors.toSet());
        Set<String> unrecognizedTools = registerNodesRequest
            .getMcpTools()
            .getTools()
            .stream()
            .map(McpTool::getType)
            .filter(type -> !buildInToolNames.contains(type))
            .collect(Collectors.toSet());
        if (!unrecognizedTools.isEmpty()) {
            exception.addValidationError(String.format(Locale.ROOT, "Unrecognized tool in request: %s", unrecognizedTools));
            throw exception;
        }
        return channel -> client
            .execute(MLMcpToolsRegisterOnNodesAction.INSTANCE, registerNodesRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLMcpToolsRegisterNodesRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        return new MLMcpToolsRegisterNodesRequest(
            clusterService.state().nodes().getNodes().keySet().toArray(new String[0]),
            McpTools.parse(parser)
        );
    }
}
