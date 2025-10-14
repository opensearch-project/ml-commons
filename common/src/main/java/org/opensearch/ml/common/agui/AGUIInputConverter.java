/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.util.HashMap;
import java.util.Map;

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
            return jsonObj.has("threadId") && jsonObj.has("runId") && jsonObj.has("messages") && jsonObj.has("tools");
        } catch (Exception e) {
            log.debug("Failed to parse input as JSON for AG-UI detection", e);
            return false;
        }
    }

    public static AgentMLInput convertFromAGUIInput(String aguiInputJson, String agentId, String tenantId, boolean isAsync) {
        try {
            JsonObject aguiInput = JsonParser.parseString(aguiInputJson).getAsJsonObject();

            String threadId = getStringField(aguiInput, "threadId");
            String runId = getStringField(aguiInput, "runId");
            JsonElement state = aguiInput.get("state");
            JsonElement messages = aguiInput.get("messages");
            JsonElement tools = aguiInput.get("tools");
            JsonElement context = aguiInput.get("context");
            JsonElement forwardedProps = aguiInput.get("forwardedProps");

            Map<String, String> parameters = new HashMap<>();
            parameters.put("agui_thread_id", threadId);
            parameters.put("agui_run_id", runId);

            if (state != null) {
                parameters.put("agui_state", gson.toJson(state));
            }

            if (messages != null) {
                parameters.put("agui_messages", gson.toJson(messages));
                extractUserQuestion(messages, parameters);
            }

            if (tools != null) {
                parameters.put("agui_tools", gson.toJson(tools));
            }

            if (context != null) {
                parameters.put("agui_context", gson.toJson(context));
            }

            if (forwardedProps != null) {
                parameters.put("agui_forwarded_props", gson.toJson(forwardedProps));
            }
            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
            AgentMLInput agentMLInput = new AgentMLInput(
                agentId,
                tenantId,
                org.opensearch.ml.common.FunctionName.AGENT,
                inputDataSet,
                isAsync
            );

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

    public static JsonObject reconstructAGUIInput(Map<String, String> parameters) {
        JsonObject aguiInput = new JsonObject();

        try {
            String threadId = parameters.get("agui_thread_id");
            String runId = parameters.get("agui_run_id");
            String stateJson = parameters.get("agui_state");
            String messagesJson = parameters.get("agui_messages");
            String toolsJson = parameters.get("agui_tools");
            String contextJson = parameters.get("agui_context");
            String forwardedPropsJson = parameters.get("agui_forwarded_props");

            if (threadId != null)
                aguiInput.addProperty("threadId", threadId);
            if (runId != null)
                aguiInput.addProperty("runId", runId);
            if (stateJson != null)
                aguiInput.add("state", JsonParser.parseString(stateJson));
            if (messagesJson != null)
                aguiInput.add("messages", JsonParser.parseString(messagesJson));
            if (toolsJson != null)
                aguiInput.add("tools", JsonParser.parseString(toolsJson));
            if (contextJson != null)
                aguiInput.add("context", JsonParser.parseString(contextJson));
            if (forwardedPropsJson != null)
                aguiInput.add("forwardedProps", JsonParser.parseString(forwardedPropsJson));

        } catch (Exception e) {
            log.error("Failed to reconstruct AG-UI input from parameters", e);
        }

        return aguiInput;
    }

    private static void extractUserQuestion(JsonElement messages, Map<String, String> parameters) {
        if (messages == null || !messages.isJsonArray()) {
            throw new IllegalArgumentException("Invalid AG-UI messages");
        }

        try {
            // Find the last user message to use as the current question
            String lastUserMessage = null;
            for (JsonElement messageElement : messages.getAsJsonArray()) {
                if (messageElement.isJsonObject()) {
                    JsonObject message = messageElement.getAsJsonObject();
                    JsonElement roleElement = message.get("role");
                    JsonElement contentElement = message.get("content");

                    if (roleElement != null
                        && "user".equals(roleElement.getAsString())
                        && contentElement != null
                        && !contentElement.isJsonNull()) {
                        lastUserMessage = contentElement.getAsString();
                    }
                }
            }

            if (lastUserMessage != null) {
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
