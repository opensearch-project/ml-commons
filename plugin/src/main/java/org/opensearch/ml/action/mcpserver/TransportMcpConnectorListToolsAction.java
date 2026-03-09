/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.utils.ToolUtils.getToolName;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMCPToolSpecsFromConnector;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpConnectorListToolsAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpConnectorListToolsResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.list.McpToolInfo;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.gson.reflect.TypeToken;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpConnectorListToolsAction extends HandledTransportAction<ActionRequest, MLMcpConnectorListToolsResponse> {

    final private static String TYPE = "type";
    final private static String PROPERTIES = "properties";
    final private static String DEFAULT_TYPE = "object";

    final private Client client;
    final private SdkClient sdkClient;
    final private EncryptorImpl encryptor;
    final private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportMcpConnectorListToolsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        EncryptorImpl encryptor,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMcpConnectorListToolsAction.NAME, transportService, actionFilters, MLMcpConnectorListToolsRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpConnectorListToolsResponse> listener) {
        MLMcpConnectorListToolsRequest listRequest = MLMcpConnectorListToolsRequest.fromActionRequest(request);
        String connectorId = listRequest.getConnectorId();
        String tenantId = listRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }

        fetchToolSpecsFromConnector(connectorId, tenantId, ActionListener.wrap(toolSpecs -> {
            if (toolSpecs == null || toolSpecs.isEmpty()) {
                log.error("No tools defined for connector : {}", connectorId);
                listener.onFailure(new MLException("No tools defined for connector"));
                return;
            }
            List<McpToolInfo> toolInfos = new ArrayList<>();
            for (MLToolSpec spec : toolSpecs) {
                toolInfos.add(toMcpToolInfo(spec));
            }
            listener.onResponse(MLMcpConnectorListToolsResponse.builder().tools(toolInfos).build());
        }, e -> {
            log.error("Failed to list tools for MCP connector: {}", connectorId, e);
            listener.onFailure(e);
        }));
    }

    /**
     * Fetches tool specs for the given connector.
     */
    protected void fetchToolSpecsFromConnector(String connectorId, String tenantId, ActionListener<List<MLToolSpec>> toolSpecsListener) {
        getMCPToolSpecsFromConnector(connectorId, tenantId, sdkClient, client, encryptor, toolSpecsListener);
    }

    /**
     * Converts MLToolSpec to REST-facing McpToolInfo.
     * Parses input_schema (JSON Schema) from attributes into a simple arguments map (name -> type string).
     */
    private McpToolInfo toMcpToolInfo(MLToolSpec spec) {
        String name = getToolName(spec);
        String type = spec.getType();
        String description = spec.getDescription();
        Map<String, String> arguments = parseInputSchemaToArguments(spec.getAttributes());
        return McpToolInfo.builder().name(name).type(type).description(description).arguments(arguments).build();
    }

    /**
     * Parses JSON Schema input_schema from attributes into a map of argument name -> type string.
     * Expects schema like {"type":"object","properties":{"query":{"type":"string"},"id":{"type":"integer"}}}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseInputSchemaToArguments(Map<String, String> attributes) {
        if (attributes == null) {
            return Collections.emptyMap();
        }
        String schemaStr = attributes.get(TOOL_INPUT_SCHEMA_FIELD);
        if (schemaStr == null || schemaStr.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Type type = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> schema = StringUtils.gson.fromJson(schemaStr, type);
            if (schema == null) {
                return Collections.emptyMap();
            }
            Object propertiesObj = schema.get(PROPERTIES);
            if (!(propertiesObj instanceof Map)) {
                return Collections.emptyMap();
            }
            Map<String, Object> properties = (Map<String, Object>) propertiesObj;
            Map<String, String> arguments = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String propName = entry.getKey();
                Object propSpec = entry.getValue();
                String typeStr = DEFAULT_TYPE;
                if (propSpec instanceof Map) {
                    Object t = ((Map<?, ?>) propSpec).get(TYPE);
                    if (t != null) {
                        typeStr = t.toString();
                    }
                }
                arguments.put(propName, typeStr);
            }
            return arguments;
        } catch (Exception e) {
            log.debug("Failed to parse input_schema for arguments", e);
            return Collections.emptyMap();
        }
    }
}
