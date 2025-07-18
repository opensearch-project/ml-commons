/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRemoveAction;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

/**
 * This class is to remove mcp tools both in system index and MCP server memory in cluster nodes.
 * The system index data will be removed first and only when it succeeds, the mcp tools in memory will be removed.
 */
@ExperimentalApi
@Log4j2
public class RestMLMcpToolsRemoveAction extends BaseRestHandler {
    private static final String ML_REGISTER_MCP_TOOLS_ACTION = "ml_remove_mcp_tools_action";
    private final ClusterService clusterService;
    private final String REMOVE_PATH = String.format(Locale.ROOT, "%s/mcp/tools/_remove", ML_BASE_URI);

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLMcpToolsRemoveAction(ClusterService clusterService, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
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
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }
        MLMcpToolsRemoveNodesRequest removeNodesRequest = getRequest(request);
        ActionRequestValidationException exception = new ActionRequestValidationException();
        if (CollectionUtils.isEmpty(removeNodesRequest.getMcpTools())) {
            exception.addValidationError("tools list can not be null");
            throw exception;
        }
        return channel -> client.execute(MLMcpToolsRemoveAction.INSTANCE, removeNodesRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLMcpToolsRemoveNodesRequest getRequest(RestRequest request) throws IOException {
        return MLMcpToolsRemoveNodesRequest
            .parse(request.contentParser(), clusterService.state().nodes().getNodes().keySet().toArray(new String[0]));
    }
}
