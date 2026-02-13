/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.agents.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.agents.models.ModelProvider;

/**
 * Model provider for OpenAI Chat Completions API.
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
 * - Source types are dynamically mapped: BASE64 → data URI format, URL → direct URL
 *
 * All parameters consistently use the ${parameters.} prefix for uniformity.
 *
 * Supported content types:
 * - TEXT: Fully supported
 * - IMAGE: Supported (BASE64 and URL formats)
 * - VIDEO: NOT supported in Chat Completions API (throws IllegalArgumentException)
 * - DOCUMENT: NOT supported in Chat Completions API (throws IllegalArgumentException)
 */
// todo: refactor the processing so providers have to only provide the constants
public class OpenaiV1ChatCompletionsModelProvider extends ModelProvider {

    private static final String REQUEST_BODY_TEMPLATE = "{\"model\":\"${parameters.model}\","
        + "\"messages\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
        + "${parameters.tool_configs:-}}";

    private static final String REQUEST_BODY_REASONING_TEMPLATE = "{\"model\":\"${parameters.model}\","
        + "\"messages\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
        + "${parameters.tool_configs:-}"
        + ",\"reasoning_effort\":\"${parameters.reasoning_effort}\"}";

    // Body templates for different input types
    // OpenAI requires content to be an array with type field, even for simple text
    private static final String TEXT_INPUT_BODY_TEMPLATE =
        "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${parameters.user_text}\"}]}";

    private static final String CONTENT_BLOCKS_BODY_TEMPLATE = "{\"role\":\"user\",\"content\":[${parameters.content_array}]}";

    // Content block templates for multi-modal content
    private static final String TEXT_CONTENT_TEMPLATE = "{\"type\":\"text\",\"text\":\"${parameters.content_text}\"}";

    private static final String IMAGE_CONTENT_BASE64_TEMPLATE =
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/${parameters.image_format};base64,${parameters.image_data}\"}}";

    private static final String IMAGE_CONTENT_URL_TEMPLATE =
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"${parameters.image_data}\"}}";

    private static final String MESSAGE_TEMPLATE = "{\"role\":\"${parameters.msg_role}\",\"content\":[${parameters.msg_content_array}]}";

    private static final String MESSAGE_WITH_TOOL_CALLS_TEMPLATE =
        "{\"role\":\"${parameters.msg_role}\",\"content\":[${parameters.msg_content_array}],\"tool_calls\":[${parameters.tool_calls_array}]}";

    private static final String MESSAGE_WITH_TOOL_CALL_ID_TEMPLATE =
        "{\"role\":\"${parameters.msg_role}\",\"content\":[${parameters.msg_content_array}],\"tool_call_id\":\"${parameters.tool_call_id}\"}";

    private static final String OPENAI_REASONING_EFFORT = "reasoning_effort";

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", modelId);

        // Override with any provided model parameters
        if (modelParameters != null) {
            parameters.putAll(modelParameters);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer ${credential.openai_api_key}");

        // handle reasoning effort
        String requestBody = parameters.containsKey(OPENAI_REASONING_EFFORT) ? REQUEST_BODY_REASONING_TEMPLATE : REQUEST_BODY_TEMPLATE;

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/chat/completions")
            .headers(headers)
            .requestBody(requestBody)
            .build();

        // Set agent connector to have default 3 retries
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);

        return HttpConnector
            .builder()
            .name("Auto-generated OpenAI connector for Agent")
            .description("Auto-generated connector for OpenAI Chat Completions API")
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
        return "openai/v1/chat/completions";
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
     * Builds content array from content blocks using templates for OpenAI Chat Completions API.
     * Supports text and image content types.
     * Throws IllegalArgumentException for unsupported VIDEO and DOCUMENT content types.
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
                    // Map SourceType to OpenAI API format
                    String imageTemplate = mapImageSourceTypeToOpenAI(image.getType());
                    StringSubstitutor imageSubstitutor = new StringSubstitutor(imageParams, "${parameters.", "}");
                    contentArray.append(imageSubstitutor.replace(imageTemplate));
                    break;
                case VIDEO:
                    // TODO: Video support would require separate Videos API integration
                    throw new IllegalArgumentException("Video content is not supported in OpenAI Chat Completions API. ");
                case DOCUMENT:
                    // TODO: Document support would require separate API integration
                    throw new IllegalArgumentException("Document content is not supported in OpenAI Chat Completions API. ");
                default:
                    // Skip unsupported content types
                    break;
            }
        }

        return contentArray.toString();
    }

    /**
     * Builds messages array using templates for OpenAI Chat Completions API.
     * Converts messages to conversation history format, handling tool calls and results.
     */
    private String buildMessagesArray(List<Message> messages, MLAgentType type) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder messagesArray = new StringBuilder();
        boolean first = true;
        for (Message message : messages) {
            if (!first) {
                messagesArray.append(",");
            }
            first = false;

            String contentArray = buildContentArrayFromBlocks(message.getContent(), type);
            Map<String, Object> msgParams = new HashMap<>();
            msgParams.put("msg_role", message.getRole());
            msgParams.put("msg_content_array", contentArray);

            // Determine which template to use based on message properties
            String template = MESSAGE_TEMPLATE;

            // Assistant messages with tool calls
            if ("assistant".equalsIgnoreCase(message.getRole()) && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                String toolCallsArray = buildToolCallsArray(message.getToolCalls());
                msgParams.put("tool_calls_array", toolCallsArray);
                template = MESSAGE_WITH_TOOL_CALLS_TEMPLATE;
            }
            // Tool result messages
            else if ("tool".equalsIgnoreCase(message.getRole()) && message.getToolCallId() != null) {
                msgParams.put("tool_call_id", message.getToolCallId());
                template = MESSAGE_WITH_TOOL_CALL_ID_TEMPLATE;
            }

            StringSubstitutor msgSubstitutor = new StringSubstitutor(msgParams, "${parameters.", "}");
            messagesArray.append(msgSubstitutor.replace(template));
        }

        return messagesArray.toString();
    }

    /**
     * Builds tool calls array for OpenAI Chat Completions API format.
     */
    private String buildToolCallsArray(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "";
        }

        StringBuilder toolCallsArray = new StringBuilder();
        boolean first = true;
        for (ToolCall toolCall : toolCalls) {
            if (!first) {
                toolCallsArray.append(",");
            }
            first = false;

            Map<String, Object> params = new HashMap<>();
            params.put("tool_call_id", toolCall.getId());
            params.put("tool_call_type", toolCall.getType() != null ? toolCall.getType() : "function");
            params.put("function_name", toolCall.getFunction().getName());
            params.put("function_arguments", StringEscapeUtils.escapeJson(toolCall.getFunction().getArguments()));

            String template =
                "{\"id\":\"${parameters.tool_call_id}\",\"type\":\"${parameters.tool_call_type}\",\"function\":{\"name\":\"${parameters.function_name}\",\"arguments\":\"${parameters.function_arguments}\"}}";
            StringSubstitutor substitutor = new StringSubstitutor(params, "${parameters.", "}");
            toolCallsArray.append(substitutor.replace(template));
        }

        return toolCallsArray.toString();
    }

    /**
     * Maps SourceType to OpenAI Chat Completions API image format.
     * Returns the appropriate template based on the source type.
     *
     * @param sourceType the source type from image content
     * @return the corresponding OpenAI API template string
     * @throws IllegalArgumentException if sourceType is null or unsupported
     */
    private String mapImageSourceTypeToOpenAI(SourceType sourceType) {
        if (sourceType == null) {
            String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Image source type is required. Supported types: " + supportedTypes);
        }
        return switch (sourceType) {
            case BASE64 -> IMAGE_CONTENT_BASE64_TEMPLATE;
            case URL -> IMAGE_CONTENT_URL_TEMPLATE;
            default -> {
                String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Unsupported image source type. Supported types: " + supportedTypes);
            }
        };
    }

    @Override
    public String formatToolConfiguration(Map<String, org.opensearch.ml.common.spi.tools.Tool> tools, Map<String, MLToolSpec> toolSpecMap) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }

        // OpenAI format: "tools":[{"type":"function","function":{...}}]
        StringBuilder toolsJson = new StringBuilder();
        toolsJson.append(",\"tools\":[");

        boolean first = true;
        for (Map.Entry<String, org.opensearch.ml.common.spi.tools.Tool> entry : tools.entrySet()) {
            String toolType = entry.getKey();
            org.opensearch.ml.common.spi.tools.Tool tool = entry.getValue();
            MLToolSpec toolSpec = toolSpecMap.get(toolType);

            if (!first) {
                toolsJson.append(",");
            }
            first = false;

            // Get tool metadata
            String toolName = tool.getName();
            if (toolName == null || toolName.trim().isEmpty()) {
                toolName = toolType;
            }

            String toolDescription = tool.getDescription();
            if (toolDescription == null) {
                toolDescription = "";
            }

            // Get input schema
            String inputSchema = getToolInputSchema(tool);

            // Build OpenAI function format
            toolsJson.append("{\"type\":\"function\",\"function\":{");
            toolsJson.append("\"name\":\"").append(StringEscapeUtils.escapeJson(toolName)).append("\",");
            toolsJson.append("\"description\":\"").append(StringEscapeUtils.escapeJson(toolDescription)).append("\"");

            // Add parameters
            if (inputSchema != null && !inputSchema.trim().isEmpty()) {
                toolsJson.append(",\"parameters\":");
                if (inputSchema.startsWith("{") || inputSchema.startsWith("[")) {
                    toolsJson.append(inputSchema);
                } else {
                    toolsJson.append(StringUtils.gson.toJson(inputSchema));
                }
            } else {
                toolsJson.append(",\"parameters\":{\"type\":\"object\",\"properties\":{}}");
            }

            toolsJson.append("}}");
        }

        toolsJson.append("],\"tool_choice\":\"auto\"");
        return toolsJson.toString();
    }

    @Override
    public String formatAssistantToolUseMessage(List<Map<String, Object>> content, List<Map<String, Object>> toolUseBlocks) {
        // OpenAI format: {"role":"assistant","content":null,"tool_calls":[...]}
        StringBuilder message = new StringBuilder();
        message.append("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[");

        boolean first = true;
        for (Map<String, Object> toolUse : toolUseBlocks) {
            if (!first) {
                message.append(",");
            }
            first = false;

            String toolUseId = (String) toolUse.get("toolUseId");
            String name = (String) toolUse.get("name");
            Map<String, Object> input = (Map<String, Object>) toolUse.get("input");

            // Convert input to JSON string, then convert that to a JSON string literal
            // OpenAI expects arguments to be a string: "arguments": "{\"indices\":[]}"
            String argumentsJson = StringUtils.gson.toJson(input); // {"indices":[]}

            message.append("{");
            message.append("\"id\":\"").append(toolUseId).append("\",");
            message.append("\"type\":\"function\",");
            message.append("\"function\":{");
            message.append("\"name\":\"").append(name).append("\",");
            message.append("\"arguments\":").append(StringUtils.gson.toJson(argumentsJson)); // Wrap as JSON string
            message.append("}}");
        }

        message.append("]}");
        return message.toString();
    }

    @Override
    public String formatToolResultMessages(List<Map<String, Object>> toolResults) {
        // OpenAI format: Multiple {"role":"tool","content":"...","tool_call_id":"..."} messages
        StringBuilder messages = new StringBuilder();
        boolean first = true;

        for (Map<String, Object> toolResult : toolResults) {
            if (!first) {
                messages.append(",");
            }
            first = false;

            String toolUseId = (String) toolResult.get("toolUseId");
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) toolResult.get("content");

            // Extract text from content
            StringBuilder contentText = new StringBuilder();
            for (Map<String, Object> contentItem : contentList) {
                if (contentItem.containsKey("text")) {
                    contentText.append(contentItem.get("text"));
                }
            }

            messages.append("{\"role\":\"tool\",");
            messages.append("\"content\":\"").append(StringEscapeUtils.escapeJson(contentText.toString())).append("\",");
            messages.append("\"tool_call_id\":\"").append(toolUseId).append("\"}");
        }

        return messages.toString();
    }

    @Override
    public org.opensearch.ml.engine.agents.models.ModelProvider.ParsedLLMResponse parseResponse(Map<String, Object> rawResponse) {
        org.opensearch.ml.engine.agents.models.ModelProvider.ParsedLLMResponse parsed =
            new org.opensearch.ml.engine.agents.models.ModelProvider.ParsedLLMResponse();

        // OpenAI format: {choices: [{message: {...}, finish_reason: "..."}]}
        List<Map<String, Object>> choices = (List<Map<String, Object>>) rawResponse.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("No choices in OpenAI response");
        }

        Map<String, Object> choice = choices.get(0);
        parsed.setStopReason((String) choice.get("finish_reason"));

        Map<String, Object> rawMessage = (Map<String, Object>) choice.get("message");
        if (rawMessage == null) {
            throw new RuntimeException("No message in OpenAI choice");
        }

        // Convert OpenAI tool_calls to toolUse blocks and normalize content
        List<Map<String, Object>> toolUseBlocks = new java.util.ArrayList<>();
        List<Map<String, Object>> content = new java.util.ArrayList<>();

        // Check if there's text content (OpenAI returns content as string)
        Object contentObj = rawMessage.get("content");
        if (contentObj instanceof String && contentObj != null) {
            Map<String, Object> textBlock = new HashMap<>();
            textBlock.put("text", contentObj);
            content.add(textBlock);
        }

        // Check for tool_calls
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) rawMessage.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (Map<String, Object> toolCall : toolCalls) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function != null) {
                    // Convert OpenAI tool_call to Bedrock toolUse format
                    Map<String, Object> toolUse = new HashMap<>();
                    toolUse.put("toolUseId", toolCall.get("id"));
                    toolUse.put("name", function.get("name"));

                    // Parse arguments JSON string to Map
                    String argumentsJson = (String) function.get("arguments");
                    try {
                        Map<String, Object> input = StringUtils.gson.fromJson(argumentsJson, Map.class);
                        toolUse.put("input", input);
                    } catch (Exception e) {
                        toolUse.put("input", new HashMap<>());
                    }

                    toolUseBlocks.add(toolUse);

                    // Add toolUse block to content for storage
                    Map<String, Object> toolUseBlock = new HashMap<>();
                    toolUseBlock.put("toolUse", toolUse);
                    content.add(toolUseBlock);
                }
            }
        }

        // Build normalized Strands-format message (remove OpenAI-specific fields)
        Map<String, Object> normalizedMessage = new HashMap<>();
        normalizedMessage.put("role", rawMessage.get("role")); // "assistant"
        normalizedMessage.put("content", content); // Array of content blocks (Strands format)

        parsed.setMessage(normalizedMessage);
        parsed.setContent(content);
        parsed.setToolUseBlocks(toolUseBlocks);

        // Normalize finish_reason to match Bedrock convention
        if ("tool_calls".equals(parsed.getStopReason())) {
            parsed.setStopReason("tool_use");
        }

        // Extract usage information
        // OpenAI format: {usage: {prompt_tokens: 123, completion_tokens: 456, total_tokens: 579}}
        Map<String, Object> usage = (Map<String, Object>) rawResponse.get("usage");
        if (usage != null) {
            // Normalize to Strands format (inputTokens, outputTokens, totalTokens)
            Map<String, Object> normalizedUsage = new java.util.HashMap<>();
            if (usage.containsKey("prompt_tokens")) {
                normalizedUsage.put("inputTokens", usage.get("prompt_tokens"));
            }
            if (usage.containsKey("completion_tokens")) {
                normalizedUsage.put("outputTokens", usage.get("completion_tokens"));
            }
            if (usage.containsKey("total_tokens")) {
                normalizedUsage.put("totalTokens", usage.get("total_tokens"));
            }
            // Include cache-related tokens if present (for providers that support it)
            if (usage.containsKey("prompt_tokens_details")) {
                Map<String, Object> details = (Map<String, Object>) usage.get("prompt_tokens_details");
                if (details != null) {
                    if (details.containsKey("cached_tokens")) {
                        normalizedUsage.put("cacheReadInputTokens", details.get("cached_tokens"));
                    }
                }
            }
            parsed.setUsage(normalizedUsage);
        }

        return parsed;
    }

    /**
     * Gets input schema from MLToolSpec or Tool attributes
     */
    private String getToolInputSchema(org.opensearch.ml.common.spi.tools.Tool tool) {
        String inputSchema = null;

        // Get input schema from Tool.getAttributes().get("input_schema")
        if (tool.getAttributes() != null && tool.getAttributes().containsKey("input_schema")) {
            Object schemaObj = tool.getAttributes().get("input_schema");
            if (schemaObj instanceof String) {
                inputSchema = (String) schemaObj;
            } else if (schemaObj instanceof Map) {
                inputSchema = StringUtils.gson.toJson(schemaObj);
            }
        }

        return inputSchema;
    }
}
