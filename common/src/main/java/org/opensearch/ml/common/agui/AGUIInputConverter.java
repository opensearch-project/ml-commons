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
     */
    private static List<Message> convertAGUIMessages(JsonArray aguiMessages) {
        List<Message> messages = new ArrayList<>();

        for (JsonElement msgElement : aguiMessages) {

            JsonObject aguiMsg = msgElement.getAsJsonObject();

            String role = aguiMsg.get(AGUI_FIELD_ROLE).getAsString();

            // Skip tool role messages
            if ("tool".equalsIgnoreCase(role)) {
                continue;
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
     * Extracts tool calls JSON from ALL assistant messages with tool calls.
     * Returns list of JSON strings (one per assistant message with tool calls).
     * Used by MLAGUIAgentRunner to format via FunctionCalling.
     */
    public static List<String> extractToolCalls(JsonArray aguiMessages) {
        List<String> toolCallsJsonList = new ArrayList<>();

        for (JsonElement msgElement : aguiMessages) {
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);

                if ("assistant".equalsIgnoreCase(role) && msg.has(AGUI_FIELD_TOOL_CALLS)) {
                    JsonElement toolCallsElement = msg.get(AGUI_FIELD_TOOL_CALLS);
                    if (toolCallsElement != null && toolCallsElement.isJsonArray()) {
                        toolCallsJsonList.add(gson.toJson(toolCallsElement));
                    }
                }
            }
        }

        return toolCallsJsonList;
    }

    /**
     * Extracts ALL tool results from AG-UI messages.
     * Used by MLAGUIAgentRunner to process tool executions.
     */
    public static List<Map<String, String>> extractToolResults(JsonArray aguiMessages) {
        List<Map<String, String>> toolResults = new ArrayList<>();

        for (JsonElement msgElement : aguiMessages) {
            if (msgElement.isJsonObject()) {
                JsonObject msg = msgElement.getAsJsonObject();
                String role = getStringField(msg, AGUI_FIELD_ROLE);

                if ("tool".equalsIgnoreCase(role)) {
                    String content = getStringField(msg, AGUI_FIELD_CONTENT);
                    String toolCallId = getStringField(msg, AGUI_FIELD_TOOL_CALL_ID);

                    if (content != null && toolCallId != null) {
                        Map<String, String> toolResult = new HashMap<>();
                        toolResult.put("tool_call_id", toolCallId);
                        toolResult.put("content", content);
                        toolResults.add(toolResult);
                    }
                }
            }
        }

        return toolResults;
    }
}
