/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.DocumentContent;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.input.execute.agent.VideoContent;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.ToolUtils;

import lombok.extern.log4j.Log4j2;
import tools.jackson.databind.JsonNode;

/**
 * Model provider for Google Gemini generateContent API (v1beta).
 *
 * This provider uses template-based parameter substitution with StringSubstitutor
 * to create the request body. Different input types (text, content blocks, messages)
 * use different body templates that are parameterized and filled in at runtime.
 *
 * The main request body template uses ${parameters.body} which gets populated
 * with the appropriate message structure based on the input type.
 *
 * Template parameters are uniquely named to avoid conflicts:
 * - Text input: ${parameters.user_text}
 * - Content blocks: ${parameters.content_array}
 * - Messages: ${parameters.messages_array}
 * - Content types use prefixed parameters: ${parameters.content_text}, ${parameters.image_format}, etc.
 * - Source types are dynamically mapped: BASE64 → "bytes", URL → "uri"
 *
 * All parameters consistently use the ${parameters.} prefix for uniformity.
 */
// todo: refactor the processing so providers have to only provide the constants
@Log4j2
public class GeminiV1BetaGenerateContentModelProvider extends ModelProvider {

    private static final String REQUEST_BODY_TEMPLATE =
        "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt:-You are a helpful assistant.}\"}]},"
            + "\"contents\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
            + "${parameters.tool_configs:-},"
            + "\"generationConfig\":{\"maxOutputTokens\":${parameters.max_tokens:-4096},"
            + "\"temperature\":${parameters.temperature:-1.0}${parameters.top_p_field:-}}"
            + "}";

    // Body templates for different input types
    private static final String TEXT_INPUT_BODY_TEMPLATE = "{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.user_text}\"}]}";

    private static final String CONTENT_BLOCKS_BODY_TEMPLATE = "{\"role\":\"user\",\"parts\":[${parameters.content_array}]}";

    // Content block templates for multi-modal content
    private static final String TEXT_CONTENT_TEMPLATE = "{\"text\":\"${parameters.content_text}\"}";

    private static final String IMAGE_CONTENT_TEMPLATE =
        "{\"inlineData\":{\"mimeType\":\"image/${parameters.image_format}\",\"data\":\"${parameters.image_data}\"}}";

    private static final String DOCUMENT_CONTENT_TEMPLATE =
        "{\"fileData\":{\"mimeType\":\"${parameters.doc_format}\",\"fileUri\":\"${parameters.doc_data}\"}}";

    private static final String VIDEO_CONTENT_TEMPLATE =
        "{\"fileData\":{\"mimeType\":\"video/${parameters.video_format}\",\"fileUri\":\"${parameters.video_data}\"}}";

    private static final String MESSAGE_TEMPLATE = "{\"role\":\"${parameters.msg_role}\",\"parts\":[${parameters.msg_content_array}]}";

    private static final String USER_ROLE = "user";
    private static final String MODEL_ROLE = "model";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String TOOL_ROLE = "tool";
    static final String THOUGHT_SIGNATURE_FIELD = "thoughtSignature";

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", modelId);

        // Override with any provided model parameters
        if (modelParameters != null) {
            parameters.putAll(modelParameters);
            if (modelParameters.containsKey("top_p")) {
                String topPValue = validateNumericParameter(modelParameters.get("top_p"), "top_p");
                parameters.put("top_p_field", ", \"topP\": " + topPValue);
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-goog-api-key", "${credential.gemini_api_key}");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent")
            .headers(headers)
            .requestBody(REQUEST_BODY_TEMPLATE)
            .build();

        // Set agent connector to have default 3 retries
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);

        return HttpConnector
            .builder()
            .name("Auto-generated Gemini connector for Agent")
            .description("Auto-generated connector for Gemini generateContent API")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters)
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(connectorClientConfig)
            .build();
    }

    @Override
    public MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters) {
        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated model for " + modelName)
            .description("Auto-generated model for agent")
            .connector(connector)
            .build();
    }

    @Override
    public String getLLMInterface() {
        return "gemini/v1beta/generatecontent";
    }

    @Override
    public Map<String, String> mapTextInput(String text, MLAgentType type) {
        Map<String, String> parameters = new HashMap<>();

        // Use StringSubstitutor for parameter replacement
        Map<String, String> templateParams = new HashMap<>();
        // ToDo: Remove workaround once PER supports messages format
        if (type == MLAgentType.PLAN_EXECUTE_AND_REFLECT) {
            templateParams.put("user_text", "${parameters.prompt}");
        } else {
            templateParams.put("user_text", StringEscapeUtils.escapeJson(text));
        }

        StringSubstitutor substitutor = new StringSubstitutor(templateParams, "${parameters.", "}");
        String body = substitutor.replace(TEXT_INPUT_BODY_TEMPLATE);
        parameters.put("body", body);

        return parameters;
    }

    @Override
    public Map<String, String> mapContentBlocks(List<ContentBlock> contentBlocks, MLAgentType type) {
        Map<String, String> parameters = new HashMap<>();

        // Use StringSubstitutor for parameter replacement
        String contentArray = buildContentArrayFromBlocks(contentBlocks, type);
        Map<String, String> templateParams = new HashMap<>();
        templateParams.put("content_array", contentArray);

        StringSubstitutor substitutor = new StringSubstitutor(templateParams, "${parameters.", "}");
        String body = substitutor.replace(CONTENT_BLOCKS_BODY_TEMPLATE);
        parameters.put("body", body);

        return parameters;
    }

    @Override
    public Map<String, String> mapMessages(List<Message> messages, MLAgentType type) {
        Map<String, String> parameters = new HashMap<>();
        String messagesString = buildMessagesArray(messages, type);
        parameters.put("body", messagesString);
        // todo: Merge function calling code into this class
        // body is added to no_escape_params as the json constructed is a sequence of objects and not a valid json
        // it becomes valid as REQUEST_BODY_TEMPLATE wraps this in an array
        parameters.put(ToolUtils.NO_ESCAPE_PARAMS, "_chat_history,_tools,_interactions,tool_configs,body");
        return parameters;
    }

    /**
     * Builds content array from content blocks using templates for Gemini generateContent API.
     * Supports text, image, document, and video content types.
     */
    private String buildContentArrayFromBlocks(List<ContentBlock> blocks, MLAgentType type) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        StringBuilder contentArray = new StringBuilder();
        boolean first = true;
        for (ContentBlock block : blocks) {
            if (!first) {
                contentArray.append(",");
            }
            first = false;

            switch (block.getType()) {
                case TEXT:
                    Map<String, Object> textParams = new HashMap<>();
                    // ToDo: Remove workaround after PER supports messages format
                    if (type == MLAgentType.PLAN_EXECUTE_AND_REFLECT) {
                        textParams.put("content_text", "${parameters.prompt}");
                    } else {
                        textParams.put("content_text", StringEscapeUtils.escapeJson(block.getText()));
                    }
                    StringSubstitutor textSubstitutor = new StringSubstitutor(textParams, "${parameters.", "}");
                    contentArray.append(textSubstitutor.replace(TEXT_CONTENT_TEMPLATE));
                    break;
                case IMAGE:
                    ImageContent image = block.getImage();
                    Map<String, Object> imageParams = new HashMap<>();
                    imageParams.put("image_format", image.getFormat());
                    imageParams.put("image_data", StringEscapeUtils.escapeJson(image.getData()));
                    // Map SourceType to Gemini API source type
                    String imageTemplate = mapImageSourceTypeToGemini(image.getType());
                    StringSubstitutor imageSubstitutor = new StringSubstitutor(imageParams, "${parameters.", "}");
                    contentArray.append(imageSubstitutor.replace(imageTemplate));
                    break;
                case DOCUMENT:
                    DocumentContent document = block.getDocument();
                    Map<String, Object> docParams = new HashMap<>();
                    docParams.put("doc_format", document.getFormat());
                    docParams.put("doc_data", StringEscapeUtils.escapeJson(document.getData()));
                    StringSubstitutor docSubstitutor = new StringSubstitutor(docParams, "${parameters.", "}");
                    contentArray.append(docSubstitutor.replace(DOCUMENT_CONTENT_TEMPLATE));
                    break;
                case VIDEO:
                    VideoContent video = block.getVideo();
                    Map<String, Object> videoParams = new HashMap<>();
                    videoParams.put("video_format", video.getFormat());
                    videoParams.put("video_data", StringEscapeUtils.escapeJson(video.getData()));
                    StringSubstitutor videoSubstitutor = new StringSubstitutor(videoParams, "${parameters.", "}");
                    contentArray.append(videoSubstitutor.replace(VIDEO_CONTENT_TEMPLATE));
                    break;
                default:
                    // Skip unsupported content types
                    break;
            }
        }

        return contentArray.toString();
    }

    /**
     * Builds the Gemini {@code contents} array from unified messages.
     *
     * "assistant" maps to "model"; "tool" maps to "user", with consecutive tool messages merged into
     * one content as Gemini expects for parallel calls. Each functionCall part keeps its thought
     * signature: Gemini thinking models reject a follow-up request that drops it.
     */
    private String buildMessagesArray(List<Message> messages, MLAgentType type) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        List<String> contents = new ArrayList<>();
        List<Map<String, Object>> pendingToolResults = new ArrayList<>();
        List<ToolCall> lastToolCalls = List.of();
        for (Message message : messages) {
            if (TOOL_ROLE.equals(message.getRole())) {
                String name = functionNameFor(message.getToolCallId(), lastToolCalls, pendingToolResults.size());
                pendingToolResults.add(functionResponsePart(message, name));
                continue;
            }
            flushToolResults(pendingToolResults, contents);
            if (ASSISTANT_ROLE.equals(message.getRole())) {
                lastToolCalls = message.getToolCalls() == null ? List.of() : message.getToolCalls();
            }

            String contentArray = buildContentArrayFromBlocks(message.getContent(), type);
            if (ASSISTANT_ROLE.equals(message.getRole()) && message.getToolCalls() != null) {
                StringBuilder parts = new StringBuilder(contentArray);
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (parts.length() > 0) {
                        parts.append(",");
                    }
                    parts.append(StringUtils.toJson(functionCallPart(toolCall)));
                }
                contentArray = parts.toString();
            }
            Map<String, Object> msgParams = new HashMap<>();
            msgParams.put("msg_role", USER_ROLE.equals(message.getRole()) ? USER_ROLE : MODEL_ROLE);
            msgParams.put("msg_content_array", contentArray);
            StringSubstitutor msgSubstitutor = new StringSubstitutor(msgParams, "${parameters.", "}");
            contents.add(msgSubstitutor.replace(MESSAGE_TEMPLATE));
        }
        flushToolResults(pendingToolResults, contents);
        return String.join(",", contents);
    }

    private static void flushToolResults(List<Map<String, Object>> pendingToolResults, List<String> contents) {
        if (pendingToolResults.isEmpty()) {
            return;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", USER_ROLE);
        content.put("parts", new ArrayList<>(pendingToolResults));
        contents.add(StringUtils.toJson(content));
        pendingToolResults.clear();
    }

    private static Map<String, Object> functionCallPart(ToolCall toolCall) {
        Map<String, Object> functionCall = new LinkedHashMap<>();
        functionCall.put("name", toolCall.getFunction() == null ? null : toolCall.getFunction().getName());
        functionCall.put("args", parseArguments(toolCall.getFunction() == null ? null : toolCall.getFunction().getArguments()));
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("functionCall", functionCall);
        if (toolCall.getThoughtSignature() != null) {
            part.put(THOUGHT_SIGNATURE_FIELD, toolCall.getThoughtSignature());
        }
        return part;
    }

    /**
     * Gemini wants functionCall.args as an object. Kept as a Jackson tree, the mapper that serializes
     * the request, so values go back exactly as the model sent them.
     */
    private static JsonNode parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return StringUtils.MAPPER.createObjectNode();
        }
        // The arguments can hold user data, so only their length is logged.
        try {
            JsonNode parsed = StringUtils.MAPPER.readTree(arguments);
            if (parsed != null && parsed.isObject()) {
                return parsed;
            }
            log.warn("Tool call arguments are not a JSON object; sending empty args to Gemini. length={}", arguments.length());
        } catch (Exception e) {
            log.warn("Tool call arguments are not valid JSON; sending empty args to Gemini. length={}", arguments.length());
        }
        return StringUtils.MAPPER.createObjectNode();
    }

    /**
     * Gemini matches a functionResponse to its call by function name. The name comes from the
     * assistant call with the same id, else from the call at the same position (results follow
     * their calls in order), else the id itself, which is the function name in Gemini history.
     */
    static String functionNameFor(String toolCallId, List<ToolCall> lastToolCalls, int position) {
        for (ToolCall call : lastToolCalls) {
            if (call != null && call.getFunction() != null && toolCallId != null && toolCallId.equals(call.getId())) {
                return call.getFunction().getName();
            }
        }
        if ((toolCallId == null || toolCallId.isBlank()) && position < lastToolCalls.size()) {
            ToolCall call = lastToolCalls.get(position);
            if (call != null && call.getFunction() != null && call.getFunction().getName() != null) {
                return call.getFunction().getName();
            }
        }
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("A tool result message has no tool_call_id and no matching tool call to answer");
        }
        return toolCallId;
    }

    private static Map<String, Object> functionResponsePart(Message toolResult, String name) {
        String text = toolResult.getContent() == null
            ? ""
            : toolResult
                .getContent()
                .stream()
                .filter(block -> block.getType() == ContentType.TEXT && block.getText() != null)
                .map(ContentBlock::getText)
                .collect(Collectors.joining("\n"));
        Map<String, Object> functionResponse = new LinkedHashMap<>();
        functionResponse.put("name", name);
        functionResponse.put("response", Map.of("text", text));
        return Map.of("functionResponse", functionResponse);
    }

    /**
     * Maps SourceType to Gemini generateContent API image format.
     * Returns the appropriate template based on the source type.
     *
     * @param sourceType the source type from image content
     * @return the corresponding Gemini API template string
     * @throws IllegalArgumentException if sourceType is null or unsupported
     */
    private String mapImageSourceTypeToGemini(SourceType sourceType) {
        if (sourceType == null) {
            String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Image source type is required. Supported types: " + supportedTypes);
        }
        return switch (sourceType) {
            case BASE64 -> IMAGE_CONTENT_TEMPLATE;
            case URL -> "{\"fileData\":{\"mimeType\":\"image/${parameters.image_format}\",\"fileUri\":\"${parameters.image_data}\"}}";
            default -> {
                String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Unsupported image source type. Supported types: " + supportedTypes);
            }
        };
    }

    @Override
    public String extractMessageFromResponse(Map<String, ?> responseData) {
        if (responseData == null) {
            return null;
        }

        Object candidatesObj = responseData.get("candidates");
        if (candidatesObj instanceof List) {
            List<?> candidatesList = (List<?>) candidatesObj;
            if (!candidatesList.isEmpty() && candidatesList.get(0) instanceof Map) {
                Map<String, ?> candidateMap = (Map<String, ?>) candidatesList.get(0);
                Object contentObj = candidateMap.get("content");
                if (contentObj != null) {
                    return org.opensearch.ml.common.utils.StringUtils.toJson(contentObj);
                }
            }
        }

        return null;
    }

    /**
     * Parses one Gemini content ({@code {"role": ..., "parts": [...]}}) into a unified message.
     *
     * Thought summaries ({@code "thought": true}) are skipped. Use {@link #parseToUnifiedMessages}
     * for a turn holding several functionResponse parts. Gemini calls have no id, so the function
     * name is the call id, matching GeminiV1BetaGenerateContentFunctionCalling.
     *
     * @return the message, or null if the content has no text, tool call or tool result
     */
    @Override
    public Message parseToUnifiedMessage(String json) {
        List<Message> messages = parseToUnifiedMessages(json);
        return messages.isEmpty() ? null : messages.get(0);
    }

    @Override
    public List<Message> parseToUnifiedMessages(String json) {
        Map<String, Object> content = StringUtils.fromJson(json, "response");
        if (content == null || !(content.get("parts") instanceof List<?> parts)) {
            return List.of();
        }

        List<ContentBlock> textBlocks = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        List<Message> toolResults = new ArrayList<>();
        for (Object partObj : parts) {
            if (!(partObj instanceof Map<?, ?> part)) {
                continue;
            }
            if (part.get("functionCall") instanceof Map<?, ?> functionCall) {
                // The name is both the call id and the function to run, so a call without one can't be used.
                if (!(functionCall.get("name") instanceof String name) || name.isBlank()) {
                    log.warn("Skipping a Gemini functionCall part that has no function name");
                    continue;
                }
                Object args = functionCall.get("args");
                String arguments = args == null ? "{}" : StringUtils.toJson(args);
                Object signature = part.get(THOUGHT_SIGNATURE_FIELD);
                toolCalls
                    .add(
                        new ToolCall(
                            name,
                            "function",
                            new ToolCall.ToolFunction(name, arguments),
                            signature == null ? null : signature.toString()
                        )
                    );
            } else if (part.get("functionResponse") instanceof Map<?, ?> functionResponse) {
                toolResults.add(toolResultMessage(functionResponse));
            } else if (part.get("text") instanceof String text && !text.isEmpty() && !Boolean.TRUE.equals(part.get("thought"))) {
                ContentBlock block = new ContentBlock();
                block.setType(ContentType.TEXT);
                block.setText(text);
                textBlocks.add(block);
            }
        }

        if (!toolResults.isEmpty()) {
            return toolResults;
        }
        if (textBlocks.isEmpty() && toolCalls.isEmpty()) {
            return List.of();
        }
        Message message = new Message();
        message.setRole(ASSISTANT_ROLE);
        message.setContent(textBlocks.isEmpty() ? null : textBlocks);
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        return List.of(message);
    }

    private static Message toolResultMessage(Map<?, ?> functionResponse) {
        Object response = functionResponse.get("response");
        String text;
        if (response instanceof Map<?, ?> responseMap
            && responseMap.size() == 1
            && responseMap.get("text") instanceof String responseText) {
            text = responseText;
        } else {
            text = response == null ? "" : StringUtils.toJson(response);
        }
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        Message message = new Message();
        message.setRole(TOOL_ROLE);
        if (functionResponse.get("name") instanceof String name && !name.isBlank()) {
            message.setToolCallId(name);
        }
        message.setContent(List.of(block));
        return message;
    }

}
