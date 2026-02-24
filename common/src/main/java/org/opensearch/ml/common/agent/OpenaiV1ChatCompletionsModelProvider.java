/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.HashMap;
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
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.utils.ToolUtils;

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
    private static final String TEXT_INPUT_BODY_TEMPLATE = "{\"role\":\"user\",\"content\":\"${parameters.user_text}\"}";

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
}
