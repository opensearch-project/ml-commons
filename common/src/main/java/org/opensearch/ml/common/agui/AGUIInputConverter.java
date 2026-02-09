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
import org.opensearch.ml.common.input.execute.agent.ToolCall;

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

                // Append context to the latest user message if context is provided
                if (context != null && context.isJsonArray()) {
                    appendContextToLatestUserMessage(agentMessages, context.getAsJsonArray());
                }

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
     * Preserves tool-related data in Message objects for proper handling by ModelProviders.
     */
    private static List<Message> convertAGUIMessages(JsonArray aguiMessages) {
        List<Message> messages = new ArrayList<>();

        for (JsonElement msgElement : aguiMessages) {
            if (!msgElement.isJsonObject()) {
                continue;
            }

            JsonObject aguiMsg = msgElement.getAsJsonObject();
            String role = getStringField(aguiMsg, AGUI_FIELD_ROLE);

            if (role == null) {
                continue;
            }

            // Parse content blocks
            List<ContentBlock> contentBlocks = parseContent(aguiMsg.get(AGUI_FIELD_CONTENT));

            // Create message with role and content
            Message message = new Message(role, contentBlocks);

            // Preserve tool calls for assistant messages
            if ("assistant".equalsIgnoreCase(role) && aguiMsg.has(AGUI_FIELD_TOOL_CALLS)) {
                JsonElement toolCallsElement = aguiMsg.get(AGUI_FIELD_TOOL_CALLS);
                if (toolCallsElement.isJsonArray()) {
                    List<ToolCall> toolCalls = parseToolCalls(toolCallsElement.getAsJsonArray());
                    message.setToolCalls(toolCalls);
                }
            }

            // Preserve tool call ID for tool result messages
            if ("tool".equalsIgnoreCase(role) && aguiMsg.has(AGUI_FIELD_TOOL_CALL_ID)) {
                String toolCallId = getStringField(aguiMsg, AGUI_FIELD_TOOL_CALL_ID);
                message.setToolCallId(toolCallId);
            }

            messages.add(message);
        }

        return messages;
    }

    /**
     * Parses AG-UI tool calls array into ToolCall objects.
     */
    private static List<ToolCall> parseToolCalls(JsonArray toolCallsArray) {
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonElement toolCallElement : toolCallsArray) {
            if (!toolCallElement.isJsonObject()) {
                continue;
            }

            JsonObject toolCallObj = toolCallElement.getAsJsonObject();
            String id = getStringField(toolCallObj, "id");
            String type = getStringField(toolCallObj, "type");
            JsonElement functionElement = toolCallObj.get("function");

            if (id != null && functionElement != null && functionElement.isJsonObject()) {
                JsonObject functionObj = functionElement.getAsJsonObject();
                String name = getStringField(functionObj, "name");
                String arguments = getStringField(functionObj, "arguments");

                if (name != null && arguments != null) {
                    ToolCall.ToolFunction function = new ToolCall.ToolFunction(name, arguments);
                    ToolCall toolCall = new ToolCall(id, type, function);
                    toolCalls.add(toolCall);
                }
            }
        }

        return toolCalls;
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
     * Appends context to the latest user message in the messages list.
     * Context is prepended to the last text content block of the latest user message.
     *
     * @param messages the list of messages to modify
     * @param contextArray the context array from AG-UI input
     */
    private static void appendContextToLatestUserMessage(List<Message> messages, JsonArray contextArray) {
        if (messages == null || messages.isEmpty() || contextArray == null || contextArray.size() == 0) {
            return;
        }

        // Find the latest user message (iterate from end)
        Message latestUserMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if ("user".equalsIgnoreCase(message.getRole())) {
                latestUserMessage = message;
                break;
            }
        }

        if (latestUserMessage == null) {
            log.debug("No user message found to append context to, skipping context appending");
            return;
        }

        // Build context string from context array
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Context:\n");
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
        contextBuilder.append("\n");

        // Prepend context to the last text content block
        List<ContentBlock> contentBlocks = latestUserMessage.getContent();
        if (contentBlocks != null && !contentBlocks.isEmpty()) {
            // Find the last text content block
            ContentBlock lastTextBlock = null;
            for (int i = contentBlocks.size() - 1; i >= 0; i--) {
                ContentBlock block = contentBlocks.get(i);
                if (block.getType() == ContentType.TEXT) {
                    lastTextBlock = block;
                    break;
                }
            }

            if (lastTextBlock != null) {
                String originalText = lastTextBlock.getText();
                String newText = contextBuilder.toString() + originalText;
                lastTextBlock.setText(newText);
                log.debug("AG-UI: Appended context to latest user message");
            } else {
                // should not happen as user message has to have content
                log.warn("No text content block found in latest user message, skipping context appending");
            }
        } else {
            // should not happen as requests will always contain at least one user message
            log.debug("No content blocks found in latest user message, skipping context appending");
        }
    }
}
