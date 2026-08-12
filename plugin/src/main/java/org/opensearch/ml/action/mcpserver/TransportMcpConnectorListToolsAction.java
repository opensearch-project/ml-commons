/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.ToolUtils.getToolName;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.cleanUpResource;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMCPToolSpecsFromConnectorWithPropagatingFailures;

import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpConnectorListToolsAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpConnectorListToolsResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.list.McpToolInfo;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpConnectorListToolsAction extends HandledTransportAction<ActionRequest, MLMcpConnectorListToolsResponse> {

    final private Client client;
    final private SdkClient sdkClient;
    final private EncryptorImpl encryptor;
    final private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public TransportMcpConnectorListToolsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        EncryptorImpl encryptor,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLMcpConnectorListToolsAction.NAME, transportService, actionFilters, MLMcpConnectorListToolsRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpConnectorListToolsResponse> listener) {
        if (!mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }
        MLMcpConnectorListToolsRequest listRequest = MLMcpConnectorListToolsRequest.fromActionRequest(request);
        String connectorId = listRequest.getConnectorId();
        String tenantId = listRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }

        connectorAccessControlHelper
            .validateConnectorAccess(sdkClient, client, connectorId, tenantId, mlFeatureEnabledSetting, ActionListener.wrap(allowed -> {
                if (!allowed) {
                    listener
                        .onFailure(
                            new OpenSearchStatusException("You don't have permission to access this connector", RestStatus.FORBIDDEN)
                        );
                    return;
                }
                fetchToolSpecsFromConnector(connectorId, tenantId, ActionListener.wrap(toolSpecs -> {
                    try {
                        if (toolSpecs.isEmpty()) {
                            log.debug("No tools defined for connector: {}", connectorId);
                        }
                        List<McpToolInfo> toolInfos = toolSpecs.stream().map(this::toMcpToolInfo).toList();
                        listener.onResponse(MLMcpConnectorListToolsResponse.builder().tools(toolInfos).build());
                    } finally {
                        cleanUpResource(toolSpecs);
                    }
                }, e -> {
                    log.error("Failed to list tools for MCP connector: {}", connectorId, e);
                    listener.onFailure(e);
                }));
            }, listener::onFailure));
    }

    /**
     * Fetches tool specs for the given connector.
     */
    protected void fetchToolSpecsFromConnector(String connectorId, String tenantId, ActionListener<List<MLToolSpec>> toolSpecsListener) {
        getMCPToolSpecsFromConnectorWithPropagatingFailures(connectorId, tenantId, sdkClient, client, encryptor, toolSpecsListener);
    }

    /**
     * Converts MLToolSpec to REST-facing McpToolInfo.
     */
    private McpToolInfo toMcpToolInfo(MLToolSpec spec) {
        String name = getToolName(spec);
        String type = spec.getType();
        String description = spec.getDescription();
        Map<String, String> attributes = spec.getAttributes();
        String inputSchema = attributes == null ? null : attributes.get(TOOL_INPUT_SCHEMA_FIELD);
        return McpToolInfo.builder().name(name).type(type).description(description).inputSchema(inputSchema).build();
    }
}
