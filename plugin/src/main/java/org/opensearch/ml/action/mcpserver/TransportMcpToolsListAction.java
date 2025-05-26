/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

import java.util.List;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsListAction;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpToolsListResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpToolsListAction extends HandledTransportAction<ActionRequest, MLMcpToolsListResponse> {

    private final McpToolsHelper mcpToolsHelper;
    TransportService transportService;
    ClusterService clusterService;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    private volatile boolean mcpServerEnabled;

    @Inject
    public TransportMcpToolsListAction(
        TransportService transportService,
        ClusterService clusterService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        McpToolsHelper mcpToolsHelper
    ) {
        super(MLMcpToolsListAction.NAME, transportService, actionFilters, streamInput -> new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }
        });
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mcpToolsHelper = mcpToolsHelper;
        this.transportService = transportService;
        this.clusterService = clusterService;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsListResponse> listener) {
        if (!mcpServerEnabled) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        ActionListener<List<RegisterMcpTool>> searchListener = ActionListener
            .wrap(r -> { listener.onResponse(new MLMcpToolsListResponse(r)); }, e -> {
                log.error("Failed to list MCP tools", e);
                listener.onFailure(e);
            });
        mcpToolsHelper.searchAllTools(searchListener);
    }
}
