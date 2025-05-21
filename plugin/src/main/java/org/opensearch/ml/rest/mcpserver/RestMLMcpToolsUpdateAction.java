/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsUpdateAction;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@ExperimentalApi
@Log4j2
public class RestMLMcpToolsUpdateAction extends BaseRestHandler {
    private static final String ML_UPDATE_MCP_TOOLS_ACTION = "ml_mcp_tools_update_action";
    private volatile boolean mcpServerEnabled;
    private final ClusterService clusterService;

    /**
     * Constructor
     */
    public RestMLMcpToolsUpdateAction(ClusterService clusterService) {
        this.clusterService = clusterService;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    public String getName() {
        return ML_UPDATE_MCP_TOOLS_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/mcp/tools/_update", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mcpServerEnabled) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        MLMcpToolsUpdateNodesRequest updateNodesRequest = getRequest(request);
        ActionRequestValidationException exception = new ActionRequestValidationException();
        if (CollectionUtils.isEmpty(updateNodesRequest.getMcpTools())) {
            exception.addValidationError("tools list can not be null");
            throw exception;
        }
        return channel -> client.execute(MLMcpToolsUpdateAction.INSTANCE, getRequest(request), new RestToXContentListener<>(channel));
    }

    private MLMcpToolsUpdateNodesRequest getRequest(RestRequest request) throws IOException {
        return MLMcpToolsUpdateNodesRequest
            .parse(request.contentParser(), clusterService.state().nodes().getNodes().keySet().toArray(new String[0]));
    }
}
