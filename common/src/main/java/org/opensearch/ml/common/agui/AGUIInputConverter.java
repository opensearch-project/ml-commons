/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_CONTENT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_ROLE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALL_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOL_CALL_RESULTS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_ROLE_USER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AGUIInputConverter {

    private static final Gson gson = new Gson();

    public static boolean isAGUIInput(String inputJson) {
        try {
            JsonObject jsonObj = JsonParser.parseString(inputJson).getAsJsonObject();

            // Check required fields exist
            if (!jsonObj.has(AGUI_FIELD_THREAD_ID)
                || !jsonObj.has(AGUI_FIELD_RUN_ID)
                || !jsonObj.has(AGUI_FIELD_MESSAGES)
                || !jsonObj.has(AGUI_FIELD_TOOLS)) {
                return false;
            }

            // Validate messages is an array
            JsonElement messages = jsonObj.get(AGUI_FIELD_MESSAGES);
            if (!messages.isJsonArray()) {
                return false;
            }

            // Validate tools is an array
            JsonElement tools = jsonObj.get(AGUI_FIELD_TOOLS);
            if (!tools.isJsonArray()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.debug("Failed to parse input as JSON for AG-UI detection", e);
            return false;
        }
    }

    public static AgentMLInput convertFromAGUIInput(String aguiInputJson, String agentId, String tenantId, boolean isAsync) {
        try {
            JsonObject aguiInput = JsonParser.parseString(aguiInputJson).getAsJsonObject();

            String threadId = getStringField(aguiInput, AGUI_FIELD_THREAD_ID);
            String runId = getStringField(aguiInput, AGUI_FIELD_RUN_ID);
            JsonElement state = aguiInput.get(AGUI_FIELD_STATE);
            JsonElement messages = aguiInput.get(AGUI_FIELD_MESSAGES);
            JsonElement tools = aguiInput.get(AGUI_FIELD_TOOLS);
            JsonElement context = aguiInput.get(AGUI_FIELD_CONTEXT);
            JsonElement forwardedProps = aguiInput.get(AGUI_FIELD_FORWARDED_PROPS);

            Map<String, String> parameters = new HashMap<>();
            parameters.put(AGUI_PARAM_THREAD_ID, threadId);
            parameters.put(AGUI_PARAM_RUN_ID, runId);

            if (state != null) {
                parameters.put(AGUI_PARAM_STATE, gson.toJson(state));
            }

            if (messages != null) {
                parameters.put(AGUI_PARAM_MESSAGES, gson.toJson(messages));
                extractUserQuestion(messages, parameters);
            }

            if (tools != null) {
                parameters.put(AGUI_PARAM_TOOLS, gson.toJson(tools));
            }

            if (context != null) {
                parameters.put(AGUI_PARAM_CONTEXT, gson.toJson(context));
            }

            if (forwardedProps != null) {
                parameters.put(AGUI_PARAM_FORWARDED_PROPS, gson.toJson(forwardedProps));
            }
            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
            AgentMLInput agentMLInput = new AgentMLInput(agentId, tenantId, FunctionName.AGENT, inputDataSet, isAsync);

            log.debug("Converted AG-UI input to ML-Commons format for agent: {}", agentId);
            return agentMLInput;

        } catch (Exception e) {
            log.error("Failed to convert AG-UI input to ML-Commons format", e);
            throw new IllegalArgumentException("Invalid AG-UI input format", e);
        }
    }

    private static String getStringField(JsonObject obj, String fieldName) {
        JsonElement element = obj.get(fieldName);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    private static void extractUserQuestion(JsonElement messages, Map<String, String> parameters) {
        if (messages == null || !messages.isJsonArray()) {
            throw new IllegalArgumentException("Invalid AG-UI messages");
        }

        try {
            // Find the last user message to use as the current question
            String lastUserMessage = null;
            String toolCallResults = null;

            for (JsonElement messageElement : messages.getAsJsonArray()) {
                if (messageElement.isJsonObject()) {
                    JsonObject message = messageElement.getAsJsonObject();
                    JsonElement roleElement = message.get(AGUI_FIELD_ROLE);
                    JsonElement contentElement = message.get(AGUI_FIELD_CONTENT);
                    JsonElement toolCallIdElement = message.get(AGUI_FIELD_TOOL_CALL_ID);

                    if (roleElement != null
                        && AGUI_ROLE_USER.equals(roleElement.getAsString())
                        && contentElement != null
                        && !contentElement.isJsonNull()) {

                        String content = contentElement.getAsString();

                        // Check if this is a tool call result (has toolCallId field)
                        if (toolCallIdElement != null && !toolCallIdElement.isJsonNull()) {
                            // This is a tool call result from frontend
                            String toolCallId = toolCallIdElement.getAsString();

                            // Create tool result structure
                            JsonObject toolResult = new JsonObject();
                            toolResult.addProperty("tool_call_id", toolCallId);
                            toolResult.addProperty("content", content);

                            toolCallResults = gson.toJson(List.of(toolResult));
                            log.debug("Extracted tool call result from AG-UI messages: toolCallId={}, content={}", toolCallId, content);
                        } else {
                            // Regular user message
                            lastUserMessage = content;
                        }
                    }
                }
            }

            // Set appropriate parameters based on what was found
            if (toolCallResults != null) {
                parameters.put(AGUI_PARAM_TOOL_CALL_RESULTS, toolCallResults);
                log.debug("Detected AG-UI tool call results: {}", toolCallResults);
            } else if (lastUserMessage != null) {
                parameters.put("question", lastUserMessage);
                log.debug("Extracted user question from AG-UI messages: {}", lastUserMessage);
            } else {
                throw new IllegalArgumentException("No user message found in AG-UI messages");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid AG-UI message format", e);
        }
    }
}
