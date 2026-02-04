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
import org.opensearch.ml.common.utils.ToolUtils;

/**
 * Model provider for Bedrock Converse API.
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
 * - Source types are dynamically mapped: BASE64 → "bytes", URL → "s3Location"
 *
 * All parameters consistently use the ${parameters.} prefix for uniformity.
 */
// todo: refactor the processing so providers have to only provide the constants
public class BedrockConverseModelProvider extends ModelProvider {

    private static final String DEFAULT_REGION = "us-east-1";

    private static final String REQUEST_BODY_TEMPLATE = "{\"system\": [{\"text\": \"${parameters.system_prompt}\"}], "
        + "\"messages\": [${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
        + "${parameters.tool_configs:-} }";

    // Body templates for different input types
    private static final String TEXT_INPUT_BODY_TEMPLATE = "{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_text}\"}]}";

    private static final String CONTENT_BLOCKS_BODY_TEMPLATE = "{\"role\":\"user\",\"content\":[${parameters.content_array}]}";

    // Content block templates for multi-modal content
    private static final String TEXT_CONTENT_TEMPLATE = "{\"text\":\"${parameters.content_text}\"}";

    private static final String IMAGE_CONTENT_TEMPLATE =
        "{\"image\":{\"format\":\"${parameters.image_format}\",\"source\":{\"${parameters.image_source_type}\":\"${parameters.image_data}\"}}}";

    private static final String DOCUMENT_CONTENT_TEMPLATE =
        "{\"document\":{\"format\":\"${parameters.doc_format}\",\"name\":\"${parameters.doc_name}\",\"source\":{\"${parameters.doc_source_type}\":\"${parameters.doc_data}\"}}}";

    private static final String VIDEO_CONTENT_TEMPLATE =
        "{\"video\":{\"format\":\"${parameters.video_format}\",\"source\":{\"${parameters.video_source_type}\":\"${parameters.video_data}\"}}}";

    private static final String MESSAGE_TEMPLATE = "{\"role\":\"${parameters.msg_role}\",\"content\":[${parameters.msg_content_array}]}";

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("region", DEFAULT_REGION); // Default region, can be overridden
        parameters.put("service_name", "bedrock");
        parameters.put("model", modelId);

        // Override with any provided model parameters
        if (modelParameters != null) {
            parameters.putAll(modelParameters);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse")
            .headers(headers)
            .requestBody(REQUEST_BODY_TEMPLATE)
            .build();

        // Set agent connector to have default 3 retries
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);

        return AwsConnector
            .awsConnectorBuilder()
            .name("Auto-generated Bedrock Converse connector for Agent")
            .description("Auto-generated connector for Bedrock Converse API")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
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
        return "bedrock/converse/claude";
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
     * Builds content array from content blocks using templates for Bedrock Converse API.
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
                    // Map SourceType to Bedrock Converse API source type
                    String imageSourceType = mapSourceTypeToBedrock(image.getType(), image.getData());
                    imageParams.put("image_source_type", imageSourceType);
                    StringSubstitutor imageSubstitutor = new StringSubstitutor(imageParams, "${parameters.", "}");
                    contentArray.append(imageSubstitutor.replace(IMAGE_CONTENT_TEMPLATE));
                    break;
                case DOCUMENT:
                    DocumentContent document = block.getDocument();
                    Map<String, Object> docParams = new HashMap<>();
                    docParams.put("doc_format", document.getFormat());
                    docParams.put("doc_name", "document");
                    docParams.put("doc_data", StringEscapeUtils.escapeJson(document.getData()));
                    // Map SourceType to Bedrock Converse API source type
                    String docSourceType = mapSourceTypeToBedrock(document.getType(), document.getData());
                    docParams.put("doc_source_type", docSourceType);
                    StringSubstitutor docSubstitutor = new StringSubstitutor(docParams, "${parameters.", "}");
                    contentArray.append(docSubstitutor.replace(DOCUMENT_CONTENT_TEMPLATE));
                    break;
                case VIDEO:
                    VideoContent video = block.getVideo();
                    Map<String, Object> videoParams = new HashMap<>();
                    videoParams.put("video_format", video.getFormat());
                    videoParams.put("video_data", StringEscapeUtils.escapeJson(video.getData()));
                    // Map SourceType to Bedrock Converse API source type
                    String videoSourceType = mapSourceTypeToBedrock(video.getType(), video.getData());
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
     * Builds messages array using templates for Bedrock Converse API.
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
            // Assistant messages with tool calls: convert to Bedrock toolUse blocks
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

            // Convert "tool" role to "user" for Bedrock compatibility
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
     * Builds a Bedrock toolUse content block from a ToolCall.
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
            "{\"toolUse\":{\"toolUseId\":\"${parameters.tool_use_id}\",\"name\":\"${parameters.tool_name}\",\"input\":${parameters.tool_input}}}";
        StringSubstitutor substitutor = new StringSubstitutor(params, "${parameters.", "}");
        return substitutor.replace(template);
    }

    /**
     * Builds a Bedrock toolResult content block from a Message with toolCallId.
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
            "{\"toolResult\":{\"toolUseId\":\"${parameters.tool_call_id}\",\"content\":[{\"text\":\"${parameters.content_text}\"}]}}";
        StringSubstitutor substitutor = new StringSubstitutor(params, "${parameters.", "}");
        return substitutor.replace(template);
    }

    /**
     * Maps SourceType to Bedrock Converse API source field names.
     * Validates that URL-based sources are S3 URIs.
     *
     * @param sourceType the source type from content
     * @param dataUrl the data URL (only validated when sourceType is URL)
     * @return the corresponding Bedrock API source field name
     * @throws IllegalArgumentException if sourceType is null, unsupported, or if sourceType is URL but dataUrl is not an S3 URI
     */
    private String mapSourceTypeToBedrock(SourceType sourceType, String dataUrl) {
        if (sourceType == null) {
            String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Source type is required. Supported types: " + supportedTypes);
        }
        return switch (sourceType) {
            case BASE64 -> "bytes";
            case URL -> {
                // s3Location for S3 URIs (must be s3://...)
                if (dataUrl == null || !dataUrl.startsWith("s3://")) {
                    throw new IllegalArgumentException(
                        "URL-based content must use S3 URIs (s3://...). Other URL schemes are not supported by Bedrock Converse API"
                    );
                }
                yield "s3Location";
            }
            default -> {
                String supportedTypes = Stream.of(SourceType.values()).map(SourceType::name).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Unsupported source type. Supported types: " + supportedTypes);
            }
        };
    }
}
