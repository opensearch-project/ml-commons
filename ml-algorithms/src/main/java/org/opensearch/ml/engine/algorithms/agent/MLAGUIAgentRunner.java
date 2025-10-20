/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

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
        log.info("AG-UI Debug: Starting AG-UI agent execution for conversational agent: {}", mlAgent.getName());
        log.debug("AG-UI Debug: MLAGUIAgentRunner received params keys: {}", params.keySet());

        // Log specific AG-UI parameters
        log.debug("AG-UI Debug: agui_tools in params = {}", params.get("agui_tools") != null ? "present" : "null");
        log.debug("AG-UI Debug: agui_messages in params = {}", params.get("agui_messages") != null ? "present" : "null");
        log.debug("AG-UI Debug: question in params = {}", params.get("question") != null ? "present" : "null");

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

            processAGUIMessages(mlAgent, params);
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

            String messageId = eventCollector.startTextMessage("assistant");
            ActionListener<Object> aguiListener = ActionListener.wrap(result -> {
                try {
                    processAgentResult(result, eventCollector, messageId);
                    eventCollector.endTextMessage(messageId);
                    eventCollector.finishRun(result);

                    String eventsJson = eventCollector.getEventsAsJson();
                    log.debug("AG-UI events generated: {}", eventsJson);

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

            log.debug("AG-UI Debug: Calling conversationalRunner.run with params keys: {}", params.keySet());
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
            log.info("AG-UI: Processing string result: {}", result);

            // Check if this is a frontend tool call response
            if (resultString.startsWith("FRONTEND_TOOL_CALL: ")) {
                log.info("AG-UI: Detected frontend tool call response, processing...");
                processFrontendToolCall(resultString, eventCollector);
            } else {
                log.info("AG-UI: String result is not a frontend tool call");
            }
        } else {
            log.debug("AG-UI: Processing generic result of type: {}", result != null ? result.getClass() : "null");
        }
        List<Object> messages = new ArrayList<>();
        String responseText = extractResponseText(result);
        messages.add(Map.of("id", messageId, "role", "assistant", "content", responseText));
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
        log.info("AG-UI: processModelTensor called with tensor name: {}", tensorName);

        if ("response".equals(tensorName)) {
            // Check if tensor has dataAsMap (structured response with tool calls)
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                processToolCallsFromDataMap(dataMap, eventCollector);
            } else if (tensor.getResult() != null) {
                // Handle text result that might contain tool call information
                String result = tensor.getResult();
                log.debug("AG-UI: Processing tensor result: {}", result);
                processTextResponseForToolCalls(result, eventCollector);
            }
        }
    }

    private void processToolCallsFromDataMap(Map<String, ?> dataMap, AGUIEventCollector eventCollector) {
        log.info("AG-UI: processToolCallsFromDataMap called with dataMap keys: {}", dataMap.keySet());

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
                    log.info("AG-UI: Processing tool call - toolName: {}, toolCallId: {}, arguments: {}", toolName, toolCallId, arguments);

                    // Generate AG-UI tool call events
                    String generatedToolCallId = eventCollector.startToolCall(toolName, null);
                    log.info("AG-UI: Started tool call, generated ID: {}", generatedToolCallId);

                    if (arguments != null && !arguments.isEmpty()) {
                        eventCollector.addToolCallArgs(generatedToolCallId, arguments);
                        log.info("AG-UI: Added tool call args for ID: {}", generatedToolCallId);
                    }

                    eventCollector.endToolCall(generatedToolCallId);
                    log.info("AG-UI: Ended tool call for ID: {}", generatedToolCallId);

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
        log.info("AG-UI: Processing frontend tool call response: {}", frontendToolCallResponse);
        try {
            // Extract the JSON part after "FRONTEND_TOOL_CALL: "
            String jsonPart = frontendToolCallResponse.substring("FRONTEND_TOOL_CALL: ".length());
            log.info("AG-UI: Extracted JSON part: {}", jsonPart);

            JsonElement element = gson.fromJson(jsonPart, JsonElement.class);

            if (element.isJsonObject()) {
                JsonObject toolCallObj = element.getAsJsonObject();
                String toolName = toolCallObj.get("tool").getAsString();
                String toolInput = toolCallObj.get("input").getAsString();

                log.info("AG-UI: Processing frontend tool call - tool: {}, input: {}", toolName, toolInput);

                // Generate AG-UI events for the frontend tool call
                String toolCallId = eventCollector.startToolCall(toolName, null);
                log.info("AG-UI: Started tool call with ID: {}", toolCallId);

                eventCollector.addToolCallArgs(toolCallId, toolInput);
                log.info("AG-UI: Added tool call args for ID: {}", toolCallId);

                eventCollector.endToolCall(toolCallId);
                log.info("AG-UI: Ended tool call for ID: {}", toolCallId);

                log.info("AG-UI: Successfully generated frontend tool call events for tool: {} with id: {}", toolName, toolCallId);
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
            log.debug("AG-UI: Response is not JSON with tool calls, treating as regular text: {}", e.getMessage());
            // Not a tool call response, just regular text - no special processing needed
        }
    }

    private void processAGUIMessages(MLAgent mlAgent, Map<String, String> params) {
        String aguiMessagesJson = params.get("agui_messages");
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
                    String role = getStringField(message, "role");
                    
                    // Track and extract assistant messages with tool calls
                    if ("assistant".equals(role) && message.has("toolCalls")) {
                        toolCallMessageIndices.add(i);
                        
                        // Convert to OpenAI format for interactions
                        JsonElement toolCallsElement = message.get("toolCalls");
                        if (toolCallsElement != null && toolCallsElement.isJsonArray()) {
                            List<Map<String, Object>> toolCalls = new ArrayList<>();
                            for (JsonElement tcElement : toolCallsElement.getAsJsonArray()) {
                                if (tcElement.isJsonObject()) {
                                    JsonObject tc = tcElement.getAsJsonObject();
                                    Map<String, Object> toolCall = new HashMap<>();
                                    
                                    // OpenAI format: id, type, and function at the same level
                                    String toolCallId = getStringField(tc, "id");
                                    String toolCallType = getStringField(tc, "type");
                                    
                                    toolCall.put("id", toolCallId);
                                    toolCall.put("type", toolCallType != null ? toolCallType : "function");
                                    
                                    JsonElement functionElement = tc.get("function");
                                    if (functionElement != null && functionElement.isJsonObject()) {
                                        JsonObject func = functionElement.getAsJsonObject();
                                        Map<String, String> function = new HashMap<>();
                                        function.put("name", getStringField(func, "name"));
                                        function.put("arguments", getStringField(func, "arguments"));
                                        toolCall.put("function", function);
                                    }
                                    toolCalls.add(toolCall);
                                    log.debug("AG-UI: Extracted tool call - id: {}, type: {}", toolCallId, toolCallType);
                                }
                            }
                            
                            // Create assistant message with tool_calls in OpenAI format
                            Map<String, Object> assistantMsg = new HashMap<>();
                            assistantMsg.put("role", "assistant");
                            assistantMsg.put("tool_calls", toolCalls);
                            String assistantMessage = gson.toJson(assistantMsg);
                            assistantToolCallMessages.add(assistantMessage);
                            log.debug("AG-UI: Extracted assistant message with {} tool calls at index {}", toolCalls.size(), i);
                            log.debug("AG-UI: Assistant message JSON: {}", assistantMessage);
                        }
                    }
                    
                    if ("tool".equals(role)) {
                        String content = getStringField(message, "content");
                        String toolCallId = getStringField(message, "toolCallId");
                        
                        if (content != null && toolCallId != null) {
                            Map<String, String> toolResult = new HashMap<>();
                            toolResult.put("tool_call_id", toolCallId);
                            toolResult.put("content", content);
                            toolResults.add(toolResult);
                            toolResultMessageIndices.add(i);
                            lastToolResultIndex = i;
                            log.info("AG-UI: Extracted tool result for toolCallId: {} at index {}", toolCallId, i);
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
                        String role = getStringField(message, "role");
                        if ("assistant".equals(role)) {
                            hasAssistantAfterToolResult = true;
                            log.debug("AG-UI: Found assistant message at index {} after tool result at index {}", i, lastToolResultIndex);
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
                params.put("agui_tool_call_results", toolResultsJson);
                
                // Only pass the most recent assistant message with tool_calls
                if (lastToolCallMessage != null) {
                    params.put("agui_assistant_tool_call_messages", gson.toJson(List.of(lastToolCallMessage)));
                    log.debug("AG-UI: Added most recent assistant tool call message (index {}) to params", lastToolCallIndex);
                }
                
                log.info("AG-UI: Found most recent tool result at index {} (out of {} total tool results), added to params", 
                    lastToolResultIndex, toolResults.size());
                log.debug("AG-UI: Recent tool result JSON: {}", toolResultsJson);
                log.debug("AG-UI: Recent tool result - toolCallId: {}, content length: {}", 
                    lastToolResult.get("tool_call_id"), 
                    lastToolResult.get("content") != null ? lastToolResult.get("content").length() : 0);
            } else if (!toolResults.isEmpty()) {
                log.info("AG-UI: Found {} tool results but they are not recent (last at index {}, total messages: {}), " +
                    "skipping from interactions", 
                    toolResults.size(), lastToolResultIndex, messageArray.size());
            }

            String chatHistoryQuestionTemplate = params.get(CHAT_HISTORY_QUESTION_TEMPLATE);
            String chatHistoryResponseTemplate = params.get(CHAT_HISTORY_RESPONSE_TEMPLATE);

            if (chatHistoryQuestionTemplate == null || chatHistoryResponseTemplate == null) {

                StringBuilder chatHistoryBuilder = new StringBuilder();
                
                for (int i = 0; i < messageArray.size() - 1; i++) {
                    JsonElement messageElement = messageArray.get(i);
                    if (messageElement.isJsonObject()) {
                        JsonObject message = messageElement.getAsJsonObject();
                        String role = getStringField(message, "role");
                        String content = getStringField(message, "content");

                        // Skip tool messages - they're not part of chat history
                        if ("tool".equals(role)) {
                            continue;
                        }

                        // Skip assistant messages with tool_calls - they're not part of chat history
                        if ("assistant".equals(role) && message.has("toolCalls")) {
                            log.debug("AG-UI: Skipping assistant message with tool_calls at index {} (not included in chat history)", i);
                            continue;
                        }

                        // Include user messages and assistant messages with content (final answers)
                        if (("user".equals(role) || "assistant".equals(role)) && content != null && !content.isEmpty()) {
                            if (chatHistoryBuilder.length() > 0) {
                                chatHistoryBuilder.append("\n");
                            }
                            chatHistoryBuilder.append(role.equals("user") ? "Human: " : "Assistant: ").append(content);
                        }
                    }
                }

                if (chatHistoryBuilder.length() > 0) {
                    params.put(NEW_CHAT_HISTORY, chatHistoryBuilder.toString());
                    log.debug("Processed AG-UI messages to fallback string format: {} characters", chatHistoryBuilder.length());
                }
            } else {
                List<String> chatHistory = new ArrayList<>();
                
                for (int i = 0; i < messageArray.size() - 1; i++) {
                    JsonElement messageElement = messageArray.get(i);
                    if (messageElement.isJsonObject()) {
                        JsonObject message = messageElement.getAsJsonObject();
                        String role = getStringField(message, "role");
                        String content = getStringField(message, "content");

                        // Skip tool messages - they're never part of chat history
                        if ("tool".equals(role)) {
                            log.debug("AG-UI: Skipping tool message at index {} (not included in chat history)", i);
                            continue;
                        }

                        if ("user".equals(role) && content != null && !content.isEmpty()) {
                            Map<String, String> messageParams = new HashMap<>();
                            messageParams.put("question", processTextDoc(content));
                            StringSubstitutor substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                            String chatMessage = substitutor.replace(chatHistoryQuestionTemplate);
                            chatHistory.add(chatMessage);
                        } else if ("assistant".equals(role)) {
                            // Skip ALL assistant messages with tool_calls - they're never part of chat history
                            // (matching backend behavior where only final answers are in chat history)
                            if (message.has("toolCalls")) {
                                log.debug("AG-UI: Skipping assistant message with tool_calls at index {} (not included in chat history)", i);
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
                    log.debug("Processed AG-UI messages using chat history templates: {} messages", chatHistory.size());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process AG-UI messages to chat history", e);
        }
    }

    private void processAGUIContext(MLAgent mlAgent, Map<String, String> params) {
        String aguiContextJson = params.get("agui_context");

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
