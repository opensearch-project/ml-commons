/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_CONTENT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_ROLE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALL_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_ASSISTANT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_TOOL;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_USER;
import static org.opensearch.ml.common.utils.StringUtils.getStringField;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_MESSAGE_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.NEW_CHAT_HISTORY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.spi.memory.Memory;
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
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
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
                encryptor
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

            for (int i = 0; i < messageArray.size(); i++) {
                JsonElement msgElement = messageArray.get(i);
                if (msgElement.isJsonObject()) {
                    JsonObject msg = msgElement.getAsJsonObject();
                    String role = getStringField(msg, "role");
                    String content = getStringField(msg, "content");
                    boolean hasToolCalls = msg.has("toolCalls");
                    boolean hasToolCallId = msg.has("toolCallId");
                    log
                        .debug(
                            "AG-UI: Message[{}] - role: {}, hasToolCalls: {}, hasToolCallId: {}, content preview: {}",
                            i,
                            role,
                            hasToolCalls,
                            hasToolCallId,
                            content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content
                        );
                }
            }

            if (messageArray.size() <= 1) {
                return;
            }

            // Check for tool result messages and extract them
            // Also track assistant messages with tool calls
            List<Map<String, String>> toolResults = new ArrayList<>();
            List<Integer> toolCallMessageIndices = new ArrayList<>();
            List<Integer> toolResultMessageIndices = new ArrayList<>();
            List<String> assistantToolCallMessages = new ArrayList<>();
            int lastToolResultIndex = -1;

            for (int i = 0; i < messageArray.size(); i++) {
                JsonElement messageElement = messageArray.get(i);
                if (messageElement.isJsonObject()) {
                    JsonObject message = messageElement.getAsJsonObject();
                    String role = getStringField(message, AGUI_FIELD_ROLE);

                    // Track and extract assistant messages with tool calls
                    if (AGUI_ROLE_ASSISTANT.equals(role) && message.has(AGUI_FIELD_TOOL_CALLS)) {
                        toolCallMessageIndices.add(i);

                        // Extract tool calls from AG-UI message (AG-UI uses OpenAI-compatible format)
                        JsonElement toolCallsElement = message.get(AGUI_FIELD_TOOL_CALLS);
                        if (toolCallsElement != null && toolCallsElement.isJsonArray()) {
                            // Pass the JSON array directly to FunctionCalling for format conversion
                            String toolCallsJson = gson.toJson(toolCallsElement);

                            FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
                            String assistantMessage = "";

                            if (functionCalling != null) {
                                // Use FunctionCalling to format the message in the correct LLM format
                                assistantMessage = functionCalling.formatAGUIToolCalls(toolCallsJson);
                                log.debug("AG-UI: Formatted assistant message using {}", functionCalling.getClass().getSimpleName());
                            } else {
                                log.error("AG-UI: Invalid function calling configuration: {}", llmInterface);
                            }

                            assistantToolCallMessages.add(assistantMessage);
                            log.debug("AG-UI: Extracted assistant message at index {}", i);
                            log.debug("AG-UI: Assistant message JSON: {}", assistantMessage);
                        }
                    }

                    if (AGUI_ROLE_TOOL.equals(role)) {
                        String content = getStringField(message, AGUI_FIELD_CONTENT);
                        String toolCallId = getStringField(message, AGUI_FIELD_TOOL_CALL_ID);

                        if (content != null && toolCallId != null) {
                            Map<String, String> toolResult = new HashMap<>();
                            toolResult.put("tool_call_id", toolCallId);
                            toolResult.put("content", content);
                            toolResults.add(toolResult);
                            toolResultMessageIndices.add(i);
                            lastToolResultIndex = i;
                        }
                    }
                }
            }

            // Only process the MOST RECENT tool execution
            // Check if there are any assistant messages after the last tool result
            boolean hasAssistantAfterToolResult = false;
            if (lastToolResultIndex >= 0) {
                for (int i = lastToolResultIndex + 1; i < messageArray.size(); i++) {
                    JsonElement messageElement = messageArray.get(i);
                    if (messageElement.isJsonObject()) {
                        JsonObject message = messageElement.getAsJsonObject();
                        String role = getStringField(message, AGUI_FIELD_ROLE);
                        if (AGUI_ROLE_ASSISTANT.equals(role)) {
                            hasAssistantAfterToolResult = true;
                            break;
                        }
                    }
                }
            }

            boolean toolResultsAreRecent = !toolResults.isEmpty() && !hasAssistantAfterToolResult;

            if (!toolResults.isEmpty() && toolResultsAreRecent) {
                // Only include the MOST RECENT tool execution (last tool call + result pair)
                // Find the assistant message that corresponds to the last tool result
                String lastToolCallMessage = null;

                // The last tool result should correspond to the last assistant message with tool_calls
                if (!assistantToolCallMessages.isEmpty() && !toolCallMessageIndices.isEmpty()) {
                    lastToolCallMessage = assistantToolCallMessages.getLast();
                }

                // Only include the last tool result
                Map<String, String> lastToolResult = toolResults.getLast();
                List<Map<String, String>> recentToolResults = List.of(lastToolResult);

                String toolResultsJson = gson.toJson(recentToolResults);
                params.put(AGUI_PARAM_TOOL_CALL_RESULTS, toolResultsJson);

                // Only pass the most recent assistant message with tool_calls
                if (lastToolCallMessage != null) {
                    params.put(AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES, gson.toJson(List.of(lastToolCallMessage)));
                }
            } else if (!toolResults.isEmpty()) {
                log
                    .info(
                        "AG-UI: Found {} tool results but they are not recent (last at index {}, total messages: {}), "
                            + "skipping from interactions",
                        toolResults.size(),
                        lastToolResultIndex,
                        messageArray.size()
                    );
            }

            String chatHistoryQuestionTemplate = params.get(CHAT_HISTORY_QUESTION_TEMPLATE);
            String chatHistoryResponseTemplate = params.get(CHAT_HISTORY_RESPONSE_TEMPLATE);

            if (chatHistoryQuestionTemplate == null || chatHistoryResponseTemplate == null) {

                StringBuilder chatHistoryBuilder = new StringBuilder();

                for (int i = 0; i < messageArray.size() - 1; i++) {
                    JsonElement messageElement = messageArray.get(i);
                    if (messageElement.isJsonObject()) {
                        JsonObject message = messageElement.getAsJsonObject();
                        String role = getStringField(message, AGUI_FIELD_ROLE);
                        String content = getStringField(message, AGUI_FIELD_CONTENT);

                        // Skip tool messages - they're not part of chat history
                        if (AGUI_ROLE_TOOL.equals(role)) {
                            continue;
                        }

                        // Skip assistant messages with tool_calls - they're not part of chat history
                        if (AGUI_ROLE_ASSISTANT.equals(role) && message.has(AGUI_FIELD_TOOL_CALLS)) {
                            continue;
                        }

                        // Include user messages and assistant messages with content (final answers)
                        if ((AGUI_ROLE_USER.equals(role) || AGUI_ROLE_ASSISTANT.equals(role)) && content != null && !content.isEmpty()) {
                            if (chatHistoryBuilder.length() > 0) {
                                chatHistoryBuilder.append("\n");
                            }
                            chatHistoryBuilder.append(role.equals(AGUI_ROLE_USER) ? "Human: " : "Assistant: ").append(content);
                        }
                    }
                }

                if (chatHistoryBuilder.length() > 0) {
                    params.put(NEW_CHAT_HISTORY, chatHistoryBuilder.toString());
                }
            } else {
                List<String> chatHistory = new ArrayList<>();

                for (int i = 0; i < messageArray.size() - 1; i++) {
                    JsonElement messageElement = messageArray.get(i);
                    if (messageElement.isJsonObject()) {
                        JsonObject message = messageElement.getAsJsonObject();
                        String role = getStringField(message, AGUI_FIELD_ROLE);
                        String content = getStringField(message, AGUI_FIELD_CONTENT);

                        // Skip tool messages - they're never part of chat history
                        if (AGUI_ROLE_TOOL.equals(role)) {
                            continue;
                        }

                        if (AGUI_ROLE_USER.equals(role) && content != null && !content.isEmpty()) {
                            // When we have recent tool results, skip the user message that triggered the tool call
                            // This is the user message right before the assistant message with tool calls
                            if (toolResultsAreRecent && !toolCallMessageIndices.isEmpty()) {
                                int firstToolCallIndex = toolCallMessageIndices.get(0);
                                // Skip user messages that are at or after the first tool call
                                // (they're part of the current tool execution cycle, not historical chat)
                                if (i >= firstToolCallIndex - 1) {
                                    continue;
                                }
                            }

                            Map<String, String> messageParams = new HashMap<>();
                            messageParams.put("question", processTextDoc(content));
                            StringSubstitutor substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                            String chatMessage = substitutor.replace(chatHistoryQuestionTemplate);
                            chatHistory.add(chatMessage);
                        } else if (AGUI_ROLE_ASSISTANT.equals(role)) {
                            // Skip ALL assistant messages with tool_calls - they're never part of chat history
                            // (matching backend behavior where only final answers are in chat history)
                            if (message.has(AGUI_FIELD_TOOL_CALLS)) {
                                // Skip - not part of chat history
                            } else if (content != null && !content.isEmpty()) {
                                // Regular assistant message with content (final answer)
                                Map<String, String> messageParams = new HashMap<>();
                                messageParams.put("response", processTextDoc(content));
                                StringSubstitutor substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                                String chatMessage = substitutor.replace(chatHistoryResponseTemplate);
                                chatHistory.add(chatMessage);
                            }
                        }
                    }
                }

                if (!chatHistory.isEmpty()) {
                    params.put(NEW_CHAT_HISTORY, String.join(", ", chatHistory) + ", ");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process AG-UI messages to chat history", e);
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
        }
    }
}
