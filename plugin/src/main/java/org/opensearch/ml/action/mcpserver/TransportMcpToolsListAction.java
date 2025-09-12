/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;

import java.util.List;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsListAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpToolsListRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpToolsListResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpToolsListAction extends HandledTransportAction<ActionRequest, MLMcpToolsListResponse> {

    private final McpToolsHelper mcpStatelessToolsHelper;
    TransportService transportService;
    ClusterService clusterService;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportMcpToolsListAction(
        TransportService transportService,
        ClusterService clusterService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        McpToolsHelper mcpStatelessToolsHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMcpToolsListAction.NAME, transportService, actionFilters, MLMcpToolsListRequest::new);
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mcpStatelessToolsHelper = mcpStatelessToolsHelper;
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsListResponse> listener) {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        ActionListener<List<McpToolRegisterInput>> searchListener = ActionListener
            .wrap(r -> { listener.onResponse(new MLMcpToolsListResponse(r)); }, e -> {
                log.error("Failed to list MCP tools", e);
                listener.onFailure(e);
            });
        mcpStatelessToolsHelper.searchAllTools(searchListener);
    }
}
