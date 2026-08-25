/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
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

/**
 * Model provider for Bedrock InvokeModel API with Claude models.
 *
 * This provider uses Claude's native Messages API format instead of Bedrock's Converse API format.
 * Key differences from Converse API:
 * - Content blocks require explicit "type" field (e.g., {"type": "text", "text": "..."})
 * - Tool use format: {"type": "tool_use", "id": "...", "name": "...", "input": {...}}
 * - Tool results: {"type": "tool_result", "tool_use_id": "...", "content": "..."}
 * - Uses snake_case field names (tool_use_id) instead of camelCase (toolUseId)
 * - Supports Claude-specific features like message compaction and extended thinking
 *
 * Message Compaction:
 * Claude can automatically compress earlier conversation context when input becomes too long.
 * To enable compaction, pass "compaction": true in model parameters when registering the agent.
 * This configures the request with:
 * - anthropic_beta: ["compact-2026-01-12"] - enables the compaction beta feature
 * - context_management: configures when and how compaction triggers (default: 100000 tokens)
 *
 * Advanced configuration via compaction_config (optional):
 * - trigger_tokens: customize trigger threshold (default: 100000)
 * - type: compaction type (default: "compact_20260112")
 *
 * Example basic: {"compaction": true}
 * Example advanced: {"compaction": true, "compaction_config": {"trigger_tokens": 50000}}
 *
 * Compacted summaries are streamed back in real-time during agent execution.
 *
 * Template parameters are uniquely named to avoid conflicts:
 * - Text input: ${parameters.user_text}
 * - Content blocks: ${parameters.content_array}
 * - Messages: ${parameters.messages_array}
 * - Content types use prefixed parameters: ${parameters.content_text}, ${parameters.image_format}, etc.
 * - Source types are dynamically mapped: BASE64 → "base64", URL → "s3Location"
 *
 * All parameters consistently use the ${parameters.} prefix for uniformity.
 */
@Log4j2
public class BedrockInvokeModelProvider extends ModelProvider {

    private static final String DEFAULT_REGION = "us-east-1";
    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final String COMPACTION_BETA_VERSION = "compact-2026-01-12";
    private static final String COMPACTION_TYPE = "compact_20260112";

    // Models that support message compaction (Claude 4.6+)
    private static final String SUPPORTED_COMPACTION_MODELS_PATTERN = ".*(claude-sonnet-4-[6-9]|claude-opus-4-[6-9]).*";

    // Claude Messages API request body template
    // Supports optional message compaction via anthropic_beta and context_management
    private static final String REQUEST_BODY_TEMPLATE = "{\"anthropic_version\":\""
        + ANTHROPIC_VERSION
        + "\","
        + "${parameters.anthropic_beta_config:-}"
        + "\"max_tokens\":${parameters.max_tokens},"
        + "${parameters.context_management_config:-}"
        + "\"system\":\"${parameters.system_prompt}\","
        + "\"messages\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
        + "${parameters.tool_configs:-}}";

    // Body templates for different input types
    private static final String TEXT_INPUT_BODY_TEMPLATE =
        "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.user_text}\"}]}";

    private static final String CONTENT_BLOCKS_BODY_TEMPLATE = "{\"role\":\"user\",\"content\":[${parameters.content_array}]}";

    // Content block templates for multi-modal content (Claude Messages API format)
    private static final String TEXT_CONTENT_TEMPLATE = "{\"type\":\"text\",\"text\":\"${parameters.content_text}\"}";

    private static final String COMPACTION_CONTENT_TEMPLATE = "{\"type\":\"compaction\",\"content\":\"${parameters.compaction_content}\"}";

    private static final String IMAGE_CONTENT_TEMPLATE =
        "{\"type\":\"image\",\"source\":{\"type\":\"${parameters.image_source_type}\",\"media_type\":\"${parameters.image_media_type}\",\"data\":\"${parameters.image_data}\"}}";

    private static final String DOCUMENT_CONTENT_TEMPLATE =
        "{\"type\":\"document\",\"source\":{\"type\":\"${parameters.doc_source_type}\",\"media_type\":\"${parameters.doc_media_type}\",\"data\":\"${parameters.doc_data}\"},\"name\":\"${parameters.doc_name}\"}";

    private static final String VIDEO_CONTENT_TEMPLATE =
        "{\"type\":\"video\",\"source\":{\"type\":\"${parameters.video_source_type}\",\"media_type\":\"${parameters.video_media_type}\",\"data\":\"${parameters.video_data}\"}}";

    private static final String MESSAGE_TEMPLATE = "{\"role\":\"${parameters.msg_role}\",\"content\":[${parameters.msg_content_array}]}";

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("region", DEFAULT_REGION); // Default region, can be overridden
        parameters.put("service_name", "bedrock");
        parameters.put("model", modelId);
        parameters.put("max_tokens", "65536"); // Default max_tokens (64K), can be overridden

        // Override with any provided model parameters
        if (modelParameters != null) {
            parameters.putAll(modelParameters);

            // Configure message compaction if enabled
            // Only supported for Claude 4.6+ models (Sonnet 4.6, Opus 4.6)
            if ("true".equalsIgnoreCase(modelParameters.getOrDefault("compaction", "false"))) {
                if (supportsCompaction(modelId)) {
                    // Parse optional compaction_config for advanced settings
                    Map<String, String> compactionConfig = parseCompactionConfig(modelParameters.get("compaction_config"));

                    // Enable compaction beta feature
                    parameters.put("anthropic_beta_config", "\"anthropic_beta\":[\"" + COMPACTION_BETA_VERSION + "\"],");

                    // Configure context management with compaction
                    // Default values can be overridden via compaction_config
                    String compactionType = compactionConfig.getOrDefault("type", COMPACTION_TYPE);
                    String triggerTokens = compactionConfig.getOrDefault("trigger_tokens", "100000");

                    String contextManagement = "\"context_management\":{\"edits\":[{\"type\":\""
                        + compactionType
                        + "\",\"trigger\":{\"type\":\"input_tokens\",\"value\":"
                        + triggerTokens
                        + "}}]},";
                    parameters.put("context_management_config", contextManagement);
                } else {
                    // Model doesn't support compaction - set empty configs so they don't appear in request
                    parameters.put("anthropic_beta_config", "");
                    parameters.put("context_management_config", "");

                    // Log warning so user knows compaction is ignored
                    log
                        .warn(
                            "Message compaction is only supported for Claude 4.6+ models (e.g., claude-sonnet-4-6, claude-opus-4-6). "
                                + "Ignoring compaction setting for model: {}",
                            modelId
                        );
                }
            }
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke")
            .headers(headers)
            .requestBody(REQUEST_BODY_TEMPLATE)
            .build();

        // Set agent connector to have default 3 retries
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);

        return AwsConnector
            .awsConnectorBuilder()
            .name("Auto-generated Bedrock InvokeModel connector for Agent")
            .description("Auto-generated connector for Bedrock InvokeModel API with Claude Messages format")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(connectorClientConfig)
            .build();
    }

    /**
     * Check if the given model ID supports message compaction.
     * Compaction is only supported for Claude 4.6+ models (Sonnet 4.6, Opus 4.6).
     */
    private boolean supportsCompaction(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        return modelId.matches(SUPPORTED_COMPACTION_MODELS_PATTERN);
    }

    /**
     * Parse compaction_config JSON string into a Map.
     * Expected format: {"trigger_tokens": 50000, "type": "compact_20260112"}
     * Returns empty map if config is null or invalid.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseCompactionConfig(String compactionConfigJson) {
        if (compactionConfigJson == null || compactionConfigJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            Map<String, Object> parsed = StringUtils.fromJson(compactionConfigJson, "compaction_config");
            if (parsed == null) {
                return new HashMap<>();
            }

            // Convert all values to strings
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return result;
        } catch (Exception e) {
            // Return empty map if parsing fails
            return new HashMap<>();
        }
    }

    @Override
    public MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters) {
        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated model for " + modelName)
            .description("Auto-generated model for agent using Bedrock InvokeModel API")
            .connector(connector)
            .build();
    }

    @Override
    public String getLLMInterface() {
        return "bedrock/invoke/claude";
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
     * Builds content array from content blocks using Claude Messages API format.
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
                case COMPACTION:
                    // Claude compaction blocks contain summarized context
                    Map<String, Object> compactionParams = new HashMap<>();
                    compactionParams.put("compaction_content", StringEscapeUtils.escapeJson(block.getContent()));
                    StringSubstitutor compactionSubstitutor = new StringSubstitutor(compactionParams, "${parameters.", "}");
                    contentArray.append(compactionSubstitutor.replace(COMPACTION_CONTENT_TEMPLATE));
                    break;
                case IMAGE:
                    ImageContent image = block.getImage();
                    Map<String, Object> imageParams = new HashMap<>();
                    imageParams.put("image_media_type", mapFormatToMediaType(image.getFormat(), "image"));
                    imageParams.put("image_data", StringEscapeUtils.escapeJson(image.getData()));
                    // Map SourceType to Claude Messages API source type
                    String imageSourceType = mapSourceTypeToClaudeMessages(image.getType());
                    imageParams.put("image_source_type", imageSourceType);
                    StringSubstitutor imageSubstitutor = new StringSubstitutor(imageParams, "${parameters.", "}");
                    contentArray.append(imageSubstitutor.replace(IMAGE_CONTENT_TEMPLATE));
                    break;
                case DOCUMENT:
                    DocumentContent document = block.getDocument();
                    Map<String, Object> docParams = new HashMap<>();
                    docParams.put("doc_media_type", mapFormatToMediaType(document.getFormat(), "document"));
                    docParams.put("doc_name", "document");
                    docParams.put("doc_data", StringEscapeUtils.escapeJson(document.getData()));
                    // Map SourceType to Claude Messages API source type
                    String docSourceType = mapSourceTypeToClaudeMessages(document.getType());
                    docParams.put("doc_source_type", docSourceType);
                    StringSubstitutor docSubstitutor = new StringSubstitutor(docParams, "${parameters.", "}");
                    contentArray.append(docSubstitutor.replace(DOCUMENT_CONTENT_TEMPLATE));
                    break;
                case VIDEO:
                    VideoContent video = block.getVideo();
                    Map<String, Object> videoParams = new HashMap<>();
                    videoParams.put("video_media_type", mapFormatToMediaType(video.getFormat(), "video"));
                    videoParams.put("video_data", StringEscapeUtils.escapeJson(video.getData()));
                    // Map SourceType to Claude Messages API source type
                    String videoSourceType = mapSourceTypeToClaudeMessages(video.getType());
                    videoParams.put("video_source_type", videoSourceType);
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
     * Builds messages array using Claude Messages API format.
     * Converts messages to conversation history format, handling tool calls and results.
     */
    private String buildMessagesArray(List<Message> messages, MLAgentType type) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder messagesArray = new StringBuilder();
        boolean first = true;

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);

            // Build content array based on message type
            StringBuilder contentArray = new StringBuilder();

            // Tool result messages: merge consecutive tool messages
            if ("tool".equalsIgnoreCase(message.getRole())) {
                int j = i;
                while (j < messages.size() && "tool".equalsIgnoreCase(messages.get(j).getRole())) {
                    if (contentArray.length() > 0) {
                        contentArray.append(",");
                    }
                    contentArray.append(buildToolResultBlock(messages.get(j)));
                    j++;
                }
                // Skip the messages we just processed
                i = j - 1;
            }
            // Assistant messages with tool calls: convert to Claude tool_use blocks
            else if ("assistant".equalsIgnoreCase(message.getRole())
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty()) {
                // Include content if present
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    contentArray.append(buildContentArrayFromBlocks(message.getContent(), type));
                }

                // Add tool use blocks
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (contentArray.length() > 0) {
                        contentArray.append(",");
                    }
                    contentArray.append(buildToolUseBlock(toolCall));
                }
            }
            // Regular messages: build from content blocks
            else {
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    contentArray.append(buildContentArrayFromBlocks(message.getContent(), type));
                }
            }

            if (!first) {
                messagesArray.append(",");
            }
            first = false;

            // Convert "tool" role to "user" for Claude compatibility
            String role = "tool".equalsIgnoreCase(message.getRole()) ? "user" : message.getRole();

            Map<String, Object> msgParams = new HashMap<>();
            msgParams.put("msg_role", role);
            msgParams.put("msg_content_array", contentArray.toString());
            StringSubstitutor msgSubstitutor = new StringSubstitutor(msgParams, "${parameters.", "}");
            messagesArray.append(msgSubstitutor.replace(MESSAGE_TEMPLATE));
        }

        return messagesArray.toString();
    }

    /**
     * Builds a Claude Messages API tool_use content block from a ToolCall.
     * Format: {"type": "tool_use", "id": "...", "name": "...", "input": {...}}
     */
    private String buildToolUseBlock(ToolCall toolCall) {
        Map<String, Object> params = new HashMap<>();
        params.put("tool_use_id", toolCall.getId());
        params.put("tool_name", toolCall.getFunction().getName());

        // Handle empty or null arguments - default to empty object
        String arguments = toolCall.getFunction().getArguments();
        if (arguments == null || arguments.trim().isEmpty()) {
            arguments = "{}";
        }
        params.put("tool_input", arguments);

        String template =
            "{\"type\":\"tool_use\",\"id\":\"${parameters.tool_use_id}\",\"name\":\"${parameters.tool_name}\",\"input\":${parameters.tool_input}}";
        StringSubstitutor substitutor = new StringSubstitutor(params, "${parameters.", "}");
        return substitutor.replace(template);
    }

    /**
     * Builds a Claude Messages API tool_result content block from a Message with toolCallId.
     * Format: {"type": "tool_result", "tool_use_id": "...", "content": "..."}
     */
    private String buildToolResultBlock(Message message) {
        Map<String, Object> params = new HashMap<>();
        params.put("tool_call_id", message.getToolCallId());

        // Extract text content from content blocks
        String contentText = "";
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            for (ContentBlock block : message.getContent()) {
                if (block.getType() == ContentType.TEXT) {
                    contentText = StringEscapeUtils.escapeJson(block.getText());
                    break;
                }
            }
        }
        params.put("content_text", contentText);

        String template =
            "{\"type\":\"tool_result\",\"tool_use_id\":\"${parameters.tool_call_id}\",\"content\":\"${parameters.content_text}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(params, "${parameters.", "}");
        return substitutor.replace(template);
    }

    /**
     * Maps SourceType to Claude Messages API source type.
     * Claude uses "base64" for base64-encoded content.
     *
     * @param sourceType the source type from content
     * @return the corresponding Claude API source type
     * @throws IllegalArgumentException if sourceType is null or unsupported
     */
    private String mapSourceTypeToClaudeMessages(SourceType sourceType) {
        if (sourceType == null) {
            String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Source type is required. Supported types: " + supportedTypes);
        }
        return switch (sourceType) {
            case BASE64 -> "base64";
            case URL -> throw new IllegalArgumentException(
                "URL-based content is not supported by Claude Messages API via InvokeModel. Use BASE64 encoding instead."
            );
            default -> {
                String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Unsupported source type. Supported types: " + supportedTypes);
            }
        };
    }

    /**
     * Maps format string to media type.
     * For images: png -> image/png, jpeg/jpg -> image/jpeg, gif -> image/gif, webp -> image/webp
     * For documents: pdf -> application/pdf, txt -> text/plain, etc.
     * For video: mp4 -> video/mp4, etc.
     *
     * @param format the format string (e.g., "png", "pdf", "mp4")
     * @param contentType the content type category ("image", "document", "video")
     * @return the corresponding media type
     */
    private String mapFormatToMediaType(String format, String contentType) {
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("Format is required for " + contentType + " content");
        }

        String lowerFormat = format.toLowerCase();
        return switch (contentType) {
            case "image" -> switch (lowerFormat) {
                case "png" -> "image/png";
                case "jpeg", "jpg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                default -> "image/" + lowerFormat;
            };
            case "document" -> switch (lowerFormat) {
                case "pdf" -> "application/pdf";
                case "txt" -> "text/plain";
                case "html" -> "text/html";
                case "csv" -> "text/csv";
                case "doc" -> "application/msword";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                default -> "application/" + lowerFormat;
            };
            case "video" -> switch (lowerFormat) {
                case "mp4" -> "video/mp4";
                case "mpeg", "mpg" -> "video/mpeg";
                case "mov" -> "video/quicktime";
                case "avi" -> "video/x-msvideo";
                default -> "video/" + lowerFormat;
            };
            default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
        };
    }

    /**
     * Parses a Claude Messages API response into a unified Message object.
     * Handles three content item types within the "content" array:
     *
     * 1. Text response:
     *    {"role": "assistant", "content": [{"type": "text", "text": "Here is the result..."}]}
     *
     * 2. Tool call request:
     *    {"role": "assistant", "content": [
     *      {"type": "tool_use", "id": "tool_abc123", "name": "get_weather",
     *       "input": {"location": "Seattle"}}
     *    ]}
     *
     * 3. Tool result (stored as role=user, mapped to role=tool for unified format):
     *    {"role": "user", "content": [
     *      {"type": "tool_result", "tool_use_id": "tool_abc123",
     *       "content": "72°F, sunny"}
     *    ]}
     *
     * @param json JSON string containing the Claude Messages API response
     * @return a unified Message object, or null if the input cannot be parsed
     */
    @SuppressWarnings("unchecked")
    @Override
    public Message parseToUnifiedMessage(String json) {
        Map<String, Object> parsed = StringUtils.fromJson(json, "response");
        if (parsed == null) {
            return null;
        }

        // Handle wrapped format from InvokeModel streaming: {"output": {"message": {...}}}
        if (parsed.containsKey("output")) {
            Map<String, Object> output = (Map<String, Object>) parsed.get("output");
            if (output != null && output.containsKey("message")) {
                parsed = (Map<String, Object>) output.get("message");
            }
        }

        String role = (String) parsed.get("role");
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) parsed.get("content");
        if (contentList == null || contentList.isEmpty()) {
            return null;
        }

        List<ContentBlock> textBlocks = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        String toolCallId = null;

        for (Map<String, Object> contentItem : contentList) {
            String type = (String) contentItem.get("type");
            if (type == null) {
                continue;
            }

            switch (type) {
                case "text":
                    ContentBlock block = new ContentBlock();
                    block.setType(ContentType.TEXT);
                    block.setText(String.valueOf(contentItem.get("text")));
                    textBlocks.add(block);
                    break;
                case "compaction":
                    // Claude compaction summary: {"type":"compaction","content":"Summary text..."}
                    ContentBlock compactionBlock = new ContentBlock();
                    compactionBlock.setType(ContentType.COMPACTION);
                    compactionBlock.setContent(String.valueOf(contentItem.get("content")));
                    textBlocks.add(compactionBlock);
                    break;
                case "tool_use":
                    // Claude tool call: {"type":"tool_use","id":"...","name":"...","input":{...}}
                    String id = String.valueOf(contentItem.getOrDefault("id", ""));
                    String name = String.valueOf(contentItem.getOrDefault("name", ""));
                    Object input = contentItem.get("input");
                    String arguments = input != null ? StringUtils.toJson(input) : "{}";
                    toolCalls.add(new ToolCall(id, "function", new ToolCall.ToolFunction(name, arguments)));
                    break;
                case "tool_result":
                    // Claude tool result: {"type":"tool_result","tool_use_id":"...","content":"..."}
                    // Map to role=tool for unified format
                    toolCallId = String.valueOf(contentItem.getOrDefault("tool_use_id", ""));
                    role = "tool";
                    Object content = contentItem.get("content");
                    if (content != null) {
                        String contentText = "";
                        if (content instanceof String) {
                            contentText = (String) content;
                        } else if (content instanceof List) {
                            // Handle array format: [{"type": "text", "text": "..."}]
                            List<Map<String, Object>> contentArray = (List<Map<String, Object>>) content;
                            StringBuilder sb = new StringBuilder();
                            for (Map<String, Object> item : contentArray) {
                                if ("text".equals(item.get("type"))) {
                                    sb.append(String.valueOf(item.get("text")));
                                }
                            }
                            contentText = sb.toString();
                        }
                        if (!contentText.isEmpty()) {
                            ContentBlock resultBlock = new ContentBlock();
                            resultBlock.setType(ContentType.TEXT);
                            resultBlock.setText(contentText);
                            textBlocks.add(resultBlock);
                        }
                    }
                    break;
                default:
                    // Skip unknown content types
                    break;
            }
        }

        if (textBlocks.isEmpty() && toolCalls.isEmpty()) {
            return null;
        }

        Message msg = new Message();
        msg.setRole(role != null ? role : "assistant");
        msg.setContent(textBlocks.isEmpty() ? null : textBlocks);
        if (!toolCalls.isEmpty()) {
            msg.setToolCalls(toolCalls);
        }
        if (toolCallId != null) {
            msg.setToolCallId(toolCallId);
        }

        return msg;
    }
}
