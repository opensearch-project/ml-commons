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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRemoveOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@ExperimentalApi
@Log4j2
public class RestMLRemoveMcpToolsAction extends BaseRestHandler {
    private static final String ML_REGISTER_MCP_TOOLS_ACTION = "ml_remove_mcp_tools_action";
    private final ClusterService clusterService;
    private final String REMOVE_PATH = String.format(Locale.ROOT, "%s/mcp/tools/_remove", ML_BASE_URI);

    private volatile boolean mcpServerEnabled;

    public RestMLRemoveMcpToolsAction(ClusterService clusterService) {
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
        return ImmutableList.of(new Route(RestRequest.Method.POST, REMOVE_PATH));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mcpServerEnabled) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        List<String> tools = getRequest(request);
        ActionRequestValidationException exception = new ActionRequestValidationException();
        if (CollectionUtils.isEmpty(tools)) {
            exception.addValidationError("tools list can not be null");
            throw exception;
        }
        return channel -> {
            MLMcpToolsRemoveNodesRequest removeNodesRequest = new MLMcpToolsRemoveNodesRequest(
                clusterService.state().nodes().getNodes().keySet().toArray(new String[0]),
                tools
            );
            client.execute(MLMcpToolsRemoveOnNodesAction.INSTANCE, removeNodesRequest, new RestToXContentListener<>(channel));
        };
    }

    @VisibleForTesting
    List<String> getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
        List<String> tools = new ArrayList<>();
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            tools.add(parser.text());
        }
        return tools;
    }
}
