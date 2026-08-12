/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpConnectorListToolsAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLMcpConnectorListToolsAction extends BaseRestHandler {

    private static final String ML_MCP_CONNECTOR_LIST_TOOLS_ACTION = "ml_mcp_connector_list_tools_action";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLMcpConnectorListToolsAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_MCP_CONNECTOR_LIST_TOOLS_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.GET,
                    String.format(Locale.ROOT, "%s/connectors/{%s}/tools", ML_BASE_URI, PARAMETER_CONNECTOR_ID)
                )
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        MLMcpConnectorListToolsRequest listRequest = getRequest(request);
        return channel -> client.execute(MLMcpConnectorListToolsAction.INSTANCE, listRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLMcpConnectorListToolsRequest getRequest(RestRequest request) {
        String connectorId = getParameterId(request, PARAMETER_CONNECTOR_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return MLMcpConnectorListToolsRequest.builder().connectorId(connectorId).tenantId(tenantId).build();
    }
}
