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
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALLS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_FIELD_TOOL_CALL_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_FORWARDED_PROPS;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;
import static org.opensearch.ml.common.utils.StringUtils.getStringField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
                || !jsonObj.has(AGUI_FIELD_STATE)
                || !jsonObj.has(AGUI_FIELD_MESSAGES)
                || !jsonObj.has(AGUI_FIELD_TOOLS)
                || !jsonObj.has(AGUI_FIELD_CONTEXT)
                || !jsonObj.has(AGUI_FIELD_FORWARDED_PROPS)) {
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

            // Validate context is an array
            JsonElement context = jsonObj.get(AGUI_FIELD_CONTEXT);
            if (!context.isJsonArray()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to parse input as JSON for AG-UI detection", e);
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

            // Convert AG-UI messages to standard Message format
            if (messages != null && messages.isJsonArray()) {
                JsonArray messagesArray = messages.getAsJsonArray();
                List<Message> agentMessages = convertAGUIMessages(messagesArray);

                // Create AgentInput from converted messages
                AgentInput agentInput = new AgentInput(agentMessages);
                agentMLInput.setAgentInput(agentInput);
            }

            log.debug("Converted AG-UI input to ML-Commons format for agent: {}", agentId);
            return agentMLInput;

        } catch (Exception e) {
            log.error("Failed to convert AG-UI input to ML-Commons format", e);
            throw new IllegalArgumentException("Invalid AG-UI input format", e);
        }
    }

    /**
     * Converts AG-UI messages to standard Message format.
     * Skips tool-related messages as they are handled via _chat_history and _interactions.
     */
    private static List<Message> convertAGUIMessages(JsonArray aguiMessages) {
        List<Message> messages = new ArrayList<>();

        // First pass: identify the last pending tool execution
        int lastPendingToolCallIndex = -1;
        for (int i = aguiMessages.size() - 1; i >= 0; i--) {
            JsonElement msgElement = aguiMessages.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);

                if ("assistant".equalsIgnoreCase(role) && msg.has(AGUI_FIELD_TOOL_CALLS)) {
                    // Check if there's an assistant response after this
                    boolean hasAssistantAfter = false;
                    for (int j = i + 1; j < aguiMessages.size(); j++) {
                        JsonObject laterMsg = aguiMessages.get(j).getAsJsonObject();
                        String laterRole = getStringField(laterMsg, AGUI_FIELD_ROLE);
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

        // Second pass: convert messages, excluding the pending tool execution
        for (int i = 0; i < aguiMessages.size(); i++) {
            JsonElement msgElement = aguiMessages.get(i);
            JsonObject aguiMsg = msgElement.getAsJsonObject();
            String role = aguiMsg.get(AGUI_FIELD_ROLE).getAsString();

            // Skip the pending assistant tool call message (will be in _interactions)
            if (i == lastPendingToolCallIndex) {
                continue;
            }

            // Skip tool results that belong to the pending execution
            if ("tool".equalsIgnoreCase(role) && lastPendingToolCallIndex >= 0 && i > lastPendingToolCallIndex) {
                continue;
            }

            // Convert tool messages to user messages (for historical completed tool executions)
            if ("tool".equalsIgnoreCase(role)) {
                role = "user"; // Tool results are sent as user messages in Bedrock
            }

            // Skip assistant messages with only toolCalls and no content
            if ("assistant".equalsIgnoreCase(role) && aguiMsg.has(AGUI_FIELD_TOOL_CALLS)) {
                JsonElement contentElement = aguiMsg.get(AGUI_FIELD_CONTENT);
                boolean hasContent = contentElement != null
                    && !contentElement.isJsonNull()
                    && (!contentElement.isJsonPrimitive() || !contentElement.getAsString().isEmpty());

                if (!hasContent) {
                    continue;
                }
            }

            List<ContentBlock> contentBlocks = parseContent(aguiMsg.get(AGUI_FIELD_CONTENT));
            Message message = new Message(role, contentBlocks);
            messages.add(message);
        }

        return messages;
    }

    /**
     * Parses AG-UI content field into ContentBlock objects.
     */
    private static List<ContentBlock> parseContent(JsonElement contentElement) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        if (contentElement == null || contentElement.isJsonNull()) {
            return contentBlocks;
        }

        if (contentElement.isJsonPrimitive()) {
            // Simple text content
            ContentBlock textBlock = new ContentBlock();
            textBlock.setType(ContentType.TEXT);
            textBlock.setText(contentElement.getAsString());
            contentBlocks.add(textBlock);
        } else if (contentElement.isJsonArray()) {
            // Multi-modal content array
            JsonArray contentArray = contentElement.getAsJsonArray();
            for (JsonElement element : contentArray) {
                if (element.isJsonObject()) {
                    ContentBlock block = parseContentBlock(element.getAsJsonObject());
                    if (block != null) {
                        contentBlocks.add(block);
                    }
                }
            }
        }

        return contentBlocks;
    }

    /**
     * Parses a single InputContent block from AG-UI format.
     */
    private static ContentBlock parseContentBlock(JsonObject blockObj) {
        if (!blockObj.has("type")) {
            return null;
        }

        String type = blockObj.get("type").getAsString();

        if ("text".equals(type)) {
            ContentBlock block = new ContentBlock();
            block.setType(ContentType.TEXT);
            if (blockObj.has("text")) {
                block.setText(blockObj.get("text").getAsString());
            }
            return block;
        } else if ("binary".equals(type)) {
            return parseBinaryContent(blockObj);
        }

        return null;
    }

    /**
     * Parses AG-UI BinaryInputContent for images (data field only).
     */
    private static ContentBlock parseBinaryContent(JsonObject binaryObj) {
        if (!binaryObj.has("mimeType") || !binaryObj.has("data")) {
            return null;
        }

        String mimeType = binaryObj.get("mimeType").getAsString();

        // Only handle images
        if (!mimeType.startsWith("image/")) {
            return null;
        }

        String format = extractFormat(mimeType);
        String data = binaryObj.get("data").getAsString();

        ImageContent imageContent = new ImageContent();
        imageContent.setType(SourceType.BASE64);
        imageContent.setFormat(format);
        imageContent.setData(data);

        ContentBlock block = new ContentBlock();
        block.setType(ContentType.IMAGE);
        block.setImage(imageContent);

        return block;
    }

    /**
     * Extracts format from mimeType (e.g., "image/png" -> "png").
     */
    private static String extractFormat(String mimeType) {
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex >= 0 && slashIndex < mimeType.length() - 1) {
            return mimeType.substring(slashIndex + 1);
        }
        return mimeType;
    }

    /**
     * Extracts the most recent pending tool execution (one without a subsequent assistant response).
     * Returns list with at most one JSON string containing the tool calls array.
     * Historical completed tool executions are handled separately in processHistoricalToolExecutions.
     */
    public static List<String> extractToolCalls(JsonArray aguiMessages) {
        List<String> toolCallsJsonList = new ArrayList<>();

        // Find the last assistant message with tool calls
        int lastToolCallIndex = -1;
        JsonElement lastToolCallElement = null;

        for (int i = 0; i < aguiMessages.size(); i++) {
            JsonElement msgElement = aguiMessages.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);

                if ("assistant".equalsIgnoreCase(role) && msg.has(AGUI_FIELD_TOOL_CALLS)) {
                    lastToolCallIndex = i;
                    lastToolCallElement = msg.get(AGUI_FIELD_TOOL_CALLS);
                }
            }
        }

        // Only return the last tool call if there are no assistant messages after it
        // (indicating the tool execution is still pending, not yet completed)
        if (lastToolCallIndex >= 0 && lastToolCallElement != null && lastToolCallElement.isJsonArray()) {
            boolean hasAssistantAfter = false;
            for (int i = lastToolCallIndex + 1; i < aguiMessages.size(); i++) {
                JsonElement msgElement = aguiMessages.get(i);
                if (msgElement.isJsonObject()) {
                    JsonObject msg = msgElement.getAsJsonObject();
                    String role = getStringField(msg, AGUI_FIELD_ROLE);
                    if ("assistant".equalsIgnoreCase(role)) {
                        hasAssistantAfter = true;
                        break;
                    }
                }
            }

            // Only include if this is the most recent, pending tool execution
            // The tool calls array may contain multiple tool uses
            if (!hasAssistantAfter) {
                toolCallsJsonList.add(gson.toJson(lastToolCallElement));
            }
        }

        return toolCallsJsonList;
    }

    /**
     * Extracts tool results from the most recent pending tool execution only.
     * Returns results that come after the last tool call without a subsequent assistant response.
     * Historical completed tool results are handled separately in processHistoricalToolExecutions.
     */
    public static List<Map<String, String>> extractToolResults(JsonArray aguiMessages) {
        List<Map<String, String>> allToolResults = new ArrayList<>();
        List<Integer> toolResultIndices = new ArrayList<>();
        int lastToolCallIndex = -1;

        // First pass: find all tool results and the last assistant message with tool calls
        for (int i = 0; i < aguiMessages.size(); i++) {
            JsonElement msgElement = aguiMessages.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);

                if ("assistant".equalsIgnoreCase(role) && msg.has(AGUI_FIELD_TOOL_CALLS)) {
                    lastToolCallIndex = i;
                } else if ("tool".equalsIgnoreCase(role)) {
                    String content = getStringField(msg, AGUI_FIELD_CONTENT);
                    String toolCallId = getStringField(msg, AGUI_FIELD_TOOL_CALL_ID);

                    if (content != null && toolCallId != null) {
                        Map<String, String> toolResult = new HashMap<>();
                        toolResult.put("tool_call_id", toolCallId);
                        toolResult.put("content", content);
                        allToolResults.add(toolResult);
                        toolResultIndices.add(i);
                    }
                }
            }
        }

        // If no tool results or no tool calls, return empty
        if (allToolResults.isEmpty() || lastToolCallIndex < 0) {
            return new ArrayList<>();
        }

        // Check if there's an assistant message after the last tool result
        int lastToolResultIndex = toolResultIndices.isEmpty() ? -1 : toolResultIndices.get(toolResultIndices.size() - 1);
        boolean hasAssistantAfter = false;
        for (int i = lastToolResultIndex + 1; i < aguiMessages.size(); i++) {
            JsonElement msgElement = aguiMessages.get(i);
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);
                if ("assistant".equalsIgnoreCase(role)) {
                    hasAssistantAfter = true;
                    break;
                }
            }
        }

        // Only return tool results if they're recent/pending (no assistant response after them)
        if (hasAssistantAfter) {
            return new ArrayList<>();
        }

        // Return only the tool results that come after the last assistant message with tool calls
        List<Map<String, String>> recentToolResults = new ArrayList<>();
        for (int i = 0; i < toolResultIndices.size(); i++) {
            if (toolResultIndices.get(i) > lastToolCallIndex) {
                recentToolResults.add(allToolResults.get(i));
            }
        }

        return recentToolResults;
    }
}
