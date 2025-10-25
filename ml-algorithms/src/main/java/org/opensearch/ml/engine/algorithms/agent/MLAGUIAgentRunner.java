/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_ARGUMENTS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_CONTENT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_FUNCTION;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_NAME;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_ROLE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALL_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TYPE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_ASSISTANT_TOOL_CALL_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_ASSISTANT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_TOOL;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_USER;
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
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
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
        AGUIEventCollector eventCollector = new AGUIEventCollector();
        eventCollector.startRun();

        try {
            String llmInterface = params.get(LLM_INTERFACE);
            if (llmInterface == null && mlAgent.getParameters() != null) {
                llmInterface = mlAgent.getParameters().get(LLM_INTERFACE);
            }

            FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
            if (functionCalling != null) {
                functionCalling.configure(params);
            }

            processAGUIMessages(mlAgent, params, llmInterface);
            processAGUIContext(mlAgent, params);

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

            String messageId = eventCollector.startTextMessage(AGUI_ROLE_ASSISTANT);
            ActionListener<Object> aguiListener = ActionListener.wrap(result -> {
                try {
                    processAgentResult(result, eventCollector, messageId);
                    eventCollector.endTextMessage(messageId);
                    eventCollector.finishRun(result);

                    String eventsJson = eventCollector.getEventsAsJson();
                    listener.onResponse(eventsJson);
                } catch (Exception e) {
                    log.error("Error processing AG-UI events", e);
                    listener.onFailure(e);
                }
            }, error -> {
                log.error("Error in underlying agent execution", error);
                eventCollector.finishRun(null);
                listener.onFailure(error);
            });

            conversationalRunner.run(mlAgent, params, aguiListener, channel);

        } catch (Exception e) {
            log.error("Error starting AG-UI agent execution", e);
            eventCollector.finishRun(null);
            listener.onFailure(e);
        }
    }

    private void processAgentResult(Object result, AGUIEventCollector eventCollector, String messageId) {
        log
            .info(
                "AG-UI: processAgentResult called with result type: {}, result: {}",
                result != null ? result.getClass().getSimpleName() : "null",
                result
            );

        if (result instanceof ModelTensorOutput) {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) result;
            // Extract tool calls and text responses from the tensor output
            processTensorOutput(tensorOutput, eventCollector);
        } else if (result instanceof String) {
            String resultString = (String) result;
            // Check if this is a frontend tool call response
            if (resultString.startsWith("FRONTEND_TOOL_CALL: ")) {
                log.debug("AG-UI: Detected frontend tool call response, processing...");
                processFrontendToolCall(resultString, eventCollector);
            } else {
                log.debug("AG-UI: String result is not a frontend tool call");
            }
        }
        List<Object> messages = new ArrayList<>();
        String responseText = extractResponseText(result);
        messages.add(Map.of(AGUI_FIELD_ID, messageId, AGUI_FIELD_ROLE, AGUI_ROLE_ASSISTANT, AGUI_FIELD_CONTENT, responseText));
        eventCollector.addMessagesSnapshot(messages);
    }

    private String extractResponseText(Object result) {
        if (result instanceof ModelTensorOutput) {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) result;
            if (tensorOutput.getMlModelOutputs() != null) {
                for (var modelTensors : tensorOutput.getMlModelOutputs()) {
                    if (modelTensors.getMlModelTensors() != null) {
                        for (var tensor : modelTensors.getMlModelTensors()) {
                            if ("response".equals(tensor.getName())) {
                                if (tensor.getDataAsMap() != null && tensor.getDataAsMap().containsKey("response")) {
                                    Object responseValue = tensor.getDataAsMap().get("response");
                                    return responseValue != null ? responseValue.toString() : "";
                                }
                                if (tensor.getResult() != null) {
                                    return tensor.getResult();
                                }
                            }
                        }
                    }
                }
            }
        } else if (result instanceof String) {
            return (String) result;
        }

        return result != null ? result.toString() : "";
    }

    private void processTensorOutput(ModelTensorOutput tensorOutput, AGUIEventCollector eventCollector) {
        log
            .info(
                "AG-UI: processTensorOutput called with {} model outputs",
                tensorOutput.getMlModelOutputs() != null ? tensorOutput.getMlModelOutputs().size() : 0
            );

        if (tensorOutput.getMlModelOutputs() != null) {
            tensorOutput.getMlModelOutputs().forEach(modelTensors -> {
                if (modelTensors.getMlModelTensors() != null) {
                    modelTensors.getMlModelTensors().forEach(tensor -> { processModelTensor(tensor, eventCollector); });
                }
            });
        }
    }

    private void processModelTensor(ModelTensor tensor, AGUIEventCollector eventCollector) {
        String tensorName = tensor.getName();
        if ("response".equals(tensorName)) {
            // Check if tensor has dataAsMap (structured response with tool calls)
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                processToolCallsFromDataMap(dataMap, eventCollector);
            } else if (tensor.getResult() != null) {
                // Handle text result that might contain tool call information
                String result = tensor.getResult();
                processTextResponseForToolCalls(result, eventCollector);
            }
        }
    }

    private void processToolCallsFromDataMap(Map<String, ?> dataMap, AGUIEventCollector eventCollector) {
        // Look for tool_calls in the structured response
        Object toolCallsObj = dataMap.get("tool_calls");
        log
            .info(
                "AG-UI: toolCallsObj type: {}, value: {}",
                toolCallsObj != null ? toolCallsObj.getClass().getSimpleName() : "null",
                toolCallsObj
            );
        if (toolCallsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;

            for (Map<String, Object> toolCall : toolCalls) {
                String toolCallId = (String) toolCall.get("id");
                String toolName = null;
                String arguments = null;

                // Extract tool name and arguments from function object
                Object functionObj = toolCall.get("function");
                if (functionObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> function = (Map<String, Object>) functionObj;
                    toolName = (String) function.get("name");
                    arguments = (String) function.get("arguments");
                }

                if (toolCallId != null && toolName != null) {
                    // Generate AG-UI tool call events
                    String generatedToolCallId = eventCollector.startToolCall(toolName, null);

                    if (arguments != null && !arguments.isEmpty()) {
                        eventCollector.addToolCallArgs(generatedToolCallId, arguments);
                    }

                    eventCollector.endToolCall(generatedToolCallId);

                    log
                        .info(
                            "AG-UI: Successfully generated tool call events for tool={}, originalId={}, generatedId={}",
                            toolName,
                            toolCallId,
                            generatedToolCallId
                        );
                }
            }
        }
    }

    private void processFrontendToolCall(String frontendToolCallResponse, AGUIEventCollector eventCollector) {
        log.debug("AG-UI: Processing frontend tool call response: {}", frontendToolCallResponse);
        try {
            // Extract the JSON part after "FRONTEND_TOOL_CALL: "
            String jsonPart = frontendToolCallResponse.substring("FRONTEND_TOOL_CALL: ".length());
            log.debug("AG-UI: Extracted JSON part: {}", jsonPart);

            JsonElement element = gson.fromJson(jsonPart, JsonElement.class);

            if (element.isJsonObject()) {
                JsonObject toolCallObj = element.getAsJsonObject();
                String toolName = toolCallObj.get("tool").getAsString();
                String toolInput = toolCallObj.get("input").getAsString();

                log.debug("AG-UI: Processing frontend tool call - tool: {}, input: {}", toolName, toolInput);

                // Generate AG-UI events for the frontend tool call
                String toolCallId = eventCollector.startToolCall(toolName, null);
                eventCollector.addToolCallArgs(toolCallId, toolInput);
                eventCollector.endToolCall(toolCallId);
            } else {
                log.warn("AG-UI: JSON element is not an object: {}", element);
            }
        } catch (Exception e) {
            log.error("Failed to process frontend tool call response: {}", frontendToolCallResponse, e);
        }
    }

    private void processTextResponseForToolCalls(String result, AGUIEventCollector eventCollector) {
        // Try to parse JSON response that might contain tool calls
        try {
            JsonElement element = gson.fromJson(result, JsonElement.class);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("tool_calls")) {
                    JsonElement toolCallsElement = obj.get("tool_calls");
                    if (toolCallsElement.isJsonArray()) {
                        for (JsonElement toolCallElement : toolCallsElement.getAsJsonArray()) {
                            if (toolCallElement.isJsonObject()) {
                                JsonObject toolCall = toolCallElement.getAsJsonObject();
                                String toolCallId = getStringField(toolCall, "id");
                                JsonElement functionElement = toolCall.get("function");

                                if (functionElement != null && functionElement.isJsonObject()) {
                                    JsonObject function = functionElement.getAsJsonObject();
                                    String toolName = getStringField(function, "name");
                                    String arguments = getStringField(function, "arguments");

                                    if (toolCallId != null && toolName != null) {
                                        eventCollector.startToolCall(toolName, null);

                                        if (arguments != null && !arguments.isEmpty()) {
                                            eventCollector.addToolCallArgs(toolCallId, arguments);
                                        }

                                        eventCollector.endToolCall(toolCallId);
                                        log
                                            .debug(
                                                "AG-UI: Generated tool call events from text response for tool={}, id={}",
                                                toolName,
                                                toolCallId
                                            );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Not a tool call response, just regular text - no special processing needed
        }
    }

    private void processAGUIMessages(MLAgent mlAgent, Map<String, String> params, String llmInterface) {
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

                        // Convert to OpenAI format for interactions
                        JsonElement toolCallsElement = message.get(AGUI_FIELD_TOOL_CALLS);
                        if (toolCallsElement != null && toolCallsElement.isJsonArray()) {
                            List<Map<String, Object>> toolCalls = new ArrayList<>();
                            for (JsonElement tcElement : toolCallsElement.getAsJsonArray()) {
                                if (tcElement.isJsonObject()) {
                                    JsonObject tc = tcElement.getAsJsonObject();
                                    Map<String, Object> toolCall = new HashMap<>();

                                    // OpenAI format: id, type, and function at the same level
                                    String toolCallId = getStringField(tc, AGUI_FIELD_ID);
                                    String toolCallType = getStringField(tc, AGUI_FIELD_TYPE);

                                    toolCall.put(AGUI_FIELD_ID, toolCallId);
                                    toolCall.put(AGUI_FIELD_TYPE, toolCallType != null ? toolCallType : "function");

                                    JsonElement functionElement = tc.get(AGUI_FIELD_FUNCTION);
                                    if (functionElement != null && functionElement.isJsonObject()) {
                                        JsonObject func = functionElement.getAsJsonObject();
                                        Map<String, String> function = new HashMap<>();
                                        function.put(AGUI_FIELD_NAME, getStringField(func, AGUI_FIELD_NAME));
                                        function.put(AGUI_FIELD_ARGUMENTS, getStringField(func, AGUI_FIELD_ARGUMENTS));
                                        toolCall.put(AGUI_FIELD_FUNCTION, function);
                                    }
                                    toolCalls.add(toolCall);
                                }
                            }

                            // Create assistant message in the appropriate format based on LLM interface
                            String assistantMessage;
                            boolean isBedrockConverse = llmInterface != null && llmInterface.toLowerCase().contains("bedrock");

                            if (isBedrockConverse) {
                                // Bedrock format: {"role": "assistant", "content": [{"toolUse": {...}}]}
                                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                                for (Map<String, Object> toolCall : toolCalls) {
                                    Map<String, Object> toolUse = new HashMap<>();
                                    toolUse.put("toolUseId", toolCall.get("id"));

                                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                                    if (function != null) {
                                        toolUse.put("name", function.get("name"));

                                        // Parse arguments JSON string to object
                                        String argumentsJson = (String) function.get("arguments");
                                        try {
                                            Object argumentsObj = gson.fromJson(argumentsJson, Object.class);
                                            toolUse.put("input", argumentsObj);
                                        } catch (Exception e) {
                                            log.warn("AG-UI: Failed to parse tool arguments as JSON: {}", argumentsJson, e);
                                            toolUse.put("input", Map.of());
                                        }
                                    }

                                    contentBlocks.add(Map.of("toolUse", toolUse));
                                }

                                Map<String, Object> bedrockMsg = new HashMap<>();
                                bedrockMsg.put(AGUI_FIELD_ROLE, AGUI_ROLE_ASSISTANT);
                                bedrockMsg.put(AGUI_FIELD_CONTENT, contentBlocks);
                                assistantMessage = gson.toJson(bedrockMsg);
                            } else {
                                // OpenAI format: {"role": "assistant", "tool_calls": [...]}
                                Map<String, Object> assistantMsg = new HashMap<>();
                                assistantMsg.put(AGUI_FIELD_ROLE, AGUI_ROLE_ASSISTANT);
                                assistantMsg.put("tool_calls", toolCalls);
                                assistantMessage = gson.toJson(assistantMsg);
                                log.debug("AG-UI: Created OpenAI-format assistant message with {} tool calls", toolCalls.size());
                            }

                            assistantToolCallMessages.add(assistantMessage);
                            log.debug("AG-UI: Extracted assistant message with {} tool calls at index {}", toolCalls.size(), i);
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
                int lastToolCallIndex = -1;
                String lastToolCallMessage = null;

                // The last tool result should correspond to the last assistant message with tool_calls
                if (!assistantToolCallMessages.isEmpty() && !toolCallMessageIndices.isEmpty()) {
                    lastToolCallIndex = toolCallMessageIndices.get(toolCallMessageIndices.size() - 1);
                    lastToolCallMessage = assistantToolCallMessages.get(assistantToolCallMessages.size() - 1);
                }

                // Only include the last tool result
                Map<String, String> lastToolResult = toolResults.get(toolResults.size() - 1);
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

    private void processAGUIContext(MLAgent mlAgent, Map<String, String> params) {
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

    private String getStringField(JsonObject obj, String fieldName) {
        JsonElement element = obj.get(fieldName);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

}
