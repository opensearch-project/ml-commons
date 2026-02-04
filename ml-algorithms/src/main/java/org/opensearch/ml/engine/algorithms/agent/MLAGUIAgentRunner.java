/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;
import static org.opensearch.ml.common.utils.StringUtils.getStringField;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agui.AGUIInputConverter;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLAGUIAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;
    private final SdkClient sdkClient;
    private final Encryptor encryptor;
    private final HookRegistry hookRegistry;

    public MLAGUIAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor
    ) {
        this(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap, sdkClient, encryptor, null);
    }

    public MLAGUIAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor,
        HookRegistry hookRegistry
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.hookRegistry = hookRegistry;
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel) {
        try {
            String llmInterface = params.get(LLM_INTERFACE);
            if (llmInterface == null && mlAgent.getParameters() != null) {
                llmInterface = mlAgent.getParameters().get(LLM_INTERFACE);
            }

            FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
            if (functionCalling != null) {
                functionCalling.configure(params);
            }

            processAGUIMessages(params, llmInterface);
            processAGUIContext(params);

            params.put("agent_type", "ag_ui");

            MLAgentRunner conversationalRunner = new MLChatAgentRunner(
                client,
                settings,
                clusterService,
                xContentRegistry,
                toolFactories,
                memoryFactoryMap,
                sdkClient,
                encryptor,
                hookRegistry
            );

            // Execute with streaming - events are generated in RestMLExecuteStreamAction
            conversationalRunner.run(mlAgent, params, listener, channel);

        } catch (Exception e) {
            log.error("Error starting AG-UI agent execution", e);
            listener.onFailure(e);
        }
    }

    private void processAGUIMessages(Map<String, String> params, String llmInterface) {
        String aguiMessagesJson = params.get(AGUI_PARAM_MESSAGES);
        if (aguiMessagesJson == null || aguiMessagesJson.isEmpty()) {
            return;
        }

        try {
            JsonElement messagesElement = gson.fromJson(aguiMessagesJson, JsonElement.class);

            if (!messagesElement.isJsonArray()) {
                log.warn("AG-UI messages is not a JSON array");
                return;
            }

            JsonArray messageArray = messagesElement.getAsJsonArray();

            if (messageArray.size() <= 1) {
                return;
            }

            // Extract pending tool execution (most recent without assistant response after it)
            List<Map<String, String>> pendingToolResults = AGUIInputConverter.extractToolResults(messageArray);
            List<String> pendingToolCalls = AGUIInputConverter.extractToolCalls(messageArray);

            // Process pending tool execution if present (goes to _interactions)
            if (!pendingToolResults.isEmpty()) {
                processToolExecution(pendingToolResults, pendingToolCalls, llmInterface, params);
            }

            // Extract and process historical completed tool executions (goes to _chat_history)
            processHistoricalToolExecutions(messageArray, llmInterface, params);
        } catch (Exception e) {
            log.error("Failed to process AG-UI messages", e);
            throw new IllegalArgumentException("Failed to process AG-UI messages", e);
        }
    }

    /**
     * Processes tool execution by formatting tool calls and setting parameters.
     */
    private void processToolExecution(
        List<Map<String, String>> allToolResults,
        List<String> toolCalls,
        String llmInterface,
        Map<String, String> params
    ) {
        // Format ALL tool calls using FunctionCalling
        List<String> formattedToolCallMessages = new ArrayList<>();
        FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);

        for (String toolCallJson : toolCalls) {
            if (functionCalling != null) {
                String formattedMessage = functionCalling.formatAGUIToolCalls(toolCallJson);
                formattedToolCallMessages.add(formattedMessage);
            } else {
                log.error("AG-UI: Invalid function calling configuration: {}", llmInterface);
            }
        }

        params.put(AGUI_PARAM_TOOL_CALL_RESULTS, gson.toJson(allToolResults));
        log.debug("AG-UI: Set {} tool results", allToolResults.size());

        if (!formattedToolCallMessages.isEmpty()) {
            params.put(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES, gson.toJson(formattedToolCallMessages));
            log.debug("AG-UI: Set {} assistant tool call messages", formattedToolCallMessages.size());
        }
    }

    /**
     * Extracts historical completed tool executions and adds them to _chat_history.
     * Formats them with FunctionCalling in proper LLM format (assistant toolUse -> user toolResult pairs).
     */
    private void processHistoricalToolExecutions(JsonArray messageArray, String llmInterface, Map<String, String> params) {
        FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
        if (functionCalling == null) {
            log.debug("AG-UI: No function calling interface, skipping historical tool execution processing");
            return;
        }

        List<String> historicalMessages = new ArrayList<>();

        // Find the last pending tool call index
        int lastPendingToolCallIndex = -1;
        for (int i = messageArray.size() - 1; i >= 0; i--) {
            JsonElement msgElement = messageArray.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, "role");

                if ("assistant".equalsIgnoreCase(role) && msg.has("toolCalls")) {
                    boolean hasAssistantAfter = false;
                    for (int j = i + 1; j < messageArray.size(); j++) {
                        JsonObject laterMsg = messageArray.get(j).getAsJsonObject();
                        String laterRole = getStringField(laterMsg, "role");
                        if ("assistant".equalsIgnoreCase(laterRole)) {
                            hasAssistantAfter = true;
                            break;
                        }
                    }
                    if (!hasAssistantAfter) {
                        lastPendingToolCallIndex = i;
                        break;
                    }
                }
            }
        }

        // Extract all completed tool executions (before the pending one)
        for (int i = 0; i < messageArray.size(); i++) {
            JsonElement msgElement = messageArray.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, "role");

                // Stop at the pending tool call
                if (i == lastPendingToolCallIndex) {
                    break;
                }

                if ("assistant".equalsIgnoreCase(role) && msg.has("toolCalls")) {
                    JsonElement toolCallsElement = msg.get("toolCalls");
                    if (toolCallsElement != null && toolCallsElement.isJsonArray()) {
                        // Format the assistant tool call message
                        String toolCallsJson = gson.toJson(toolCallsElement);
                        String formattedAssistantMessage = functionCalling.formatAGUIToolCalls(toolCallsJson);
                        historicalMessages.add(formattedAssistantMessage);

                        // Find corresponding tool results
                        List<Map<String, Object>> toolResults = new ArrayList<>();
                        for (int j = i + 1; j < messageArray.size(); j++) {
                            JsonElement resultElement = messageArray.get(j);
                            if (resultElement.isJsonObject()) {
                                JsonObject resultMsg = resultElement.getAsJsonObject();
                                String resultRole = getStringField(resultMsg, "role");

                                if ("tool".equalsIgnoreCase(resultRole)) {
                                    String content = getStringField(resultMsg, "content");
                                    String toolCallId = getStringField(resultMsg, "toolCallId");

                                    if (content != null && toolCallId != null) {
                                        Map<String, Object> toolResult = new HashMap<>();
                                        toolResult.put("tool_call_id", toolCallId);
                                        toolResult.put("tool_result", Map.of("text", content));
                                        toolResults.add(toolResult);
                                    }
                                } else if ("assistant".equalsIgnoreCase(resultRole)) {
                                    // Reached next assistant message, stop collecting results
                                    break;
                                }
                            }
                        }

                        // Format the tool results as user message
                        if (!toolResults.isEmpty()) {
                            List<org.opensearch.ml.engine.function_calling.LLMMessage> llmMessages = functionCalling
                                .supply(toolResults);
                            for (org.opensearch.ml.engine.function_calling.LLMMessage llmMessage : llmMessages) {
                                historicalMessages.add(llmMessage.getResponse());
                            }
                        }
                    }
                }
            }
        }

        // Add historical tool executions to chat history
        if (!historicalMessages.isEmpty()) {
            String existingChatHistory = params.get("_chat_history");
            String historicalToolExecutions = String.join(", ", historicalMessages);
            if (existingChatHistory != null && !existingChatHistory.isEmpty()) {
                params.put("_chat_history", existingChatHistory + ", " + historicalToolExecutions + ", ");
            } else {
                params.put("_chat_history", historicalToolExecutions + ", ");
            }
            log.debug("AG-UI: Added {} historical tool execution messages to chat history", historicalMessages.size());
        }
    }

    private void processAGUIContext(Map<String, String> params) {
        String aguiContextJson = params.get(AGUI_PARAM_CONTEXT);

        if (aguiContextJson == null || aguiContextJson.isEmpty()) {
            return;
        }

        try {
            JsonElement contextElement = gson.fromJson(aguiContextJson, JsonElement.class);

            if (!contextElement.isJsonArray()) {
                log.warn("AG-UI context is not a JSON array");
                return;
            }

            JsonArray contextArray = contextElement.getAsJsonArray();
            if (contextArray.size() == 0) {
                return;
            }

            StringBuilder contextBuilder = new StringBuilder();

            for (JsonElement contextItemElement : contextArray) {
                if (contextItemElement.isJsonObject()) {
                    JsonObject contextItem = contextItemElement.getAsJsonObject();
                    String description = getStringField(contextItem, "description");
                    String value = getStringField(contextItem, "value");

                    if (description != null && value != null) {
                        contextBuilder.append("- ").append(description).append(": ").append(value).append("\n");
                    }
                }
            }

            if (contextBuilder.length() > 0) {
                params.put(CONTEXT, contextBuilder.toString());
            }

        } catch (Exception e) {
            log.error("Failed to process AG-UI context", e);
            throw new IllegalArgumentException("Failed to process AG-UI context", e);
        }
    }
}
