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
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsListAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpToolsListRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@ExperimentalApi
@Log4j2
public class RestMLMcpToolsListAction extends BaseRestHandler {
    private static final String ML_LIST_MCP_TOOLS_ACTION = "ml_mcp_tools_list_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLMcpToolsListAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_LIST_MCP_TOOLS_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/mcp/tools/_list", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            throw new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE);
        }

        return channel -> client.execute(MLMcpToolsListAction.INSTANCE, new MLMcpToolsListRequest(), new RestToXContentListener<>(channel));
    }
}
