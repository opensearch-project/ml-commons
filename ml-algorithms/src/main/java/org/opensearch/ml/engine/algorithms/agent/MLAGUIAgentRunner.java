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
        log.info("Starting AG-UI agent execution for conversational agent: {}", mlAgent.getName());

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

            conversationalRunner.run(mlAgent, params, aguiListener, channel);

        } catch (Exception e) {
            log.error("Error starting AG-UI agent execution", e);
            eventCollector.finishRun(null);
            listener.onFailure(e);
        }
    }

    private void processAgentResult(Object result, AGUIEventCollector eventCollector, String messageId) {
        if (result instanceof ModelTensorOutput) {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) result;
            // Extract tool calls and text responses from the tensor output
            processTensorOutput(tensorOutput, eventCollector);
        } else if (result instanceof String) {
            log.debug("AG-UI: Processing string result: {}", result);
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

        if ("response".equals(tensorName) && tensor.getResult() != null) {
            String result = tensor.getResult();
            log.debug("AG-UI: Processing tensor result: {}", result);

            if (result.contains("tool_call") || result.contains("function_call")) {
                String toolCallId = eventCollector.startToolCall("mock_tool", null);
                eventCollector.endToolCall(toolCallId);
                eventCollector.addToolCallResult(toolCallId, "Mock tool result");
            }
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

                        if (content != null && !content.isEmpty()) {
                            Map<String, String> messageParams = new HashMap<>();

                            if ("user".equals(role)) {
                                messageParams.put("question", processTextDoc(content));
                                StringSubstitutor substitutor = new StringSubstitutor(messageParams, CHAT_HISTORY_MESSAGE_PREFIX, "}");
                                String chatMessage = substitutor.replace(chatHistoryQuestionTemplate);
                                chatHistory.add(chatMessage);
                            } else if ("assistant".equals(role)) {
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
