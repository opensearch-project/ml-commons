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
import org.opensearch.ml.common.input.execute.agent.DocumentContent;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.VideoContent;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.utils.ToolUtils;

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
public class GeminiV1BetaGenerateContentModelProvider extends ModelProvider {

    private static final String REQUEST_BODY_TEMPLATE =
        "{\"systemInstruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt:-You are a helpful assistant.}\"}]},"
            + "\"contents\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
            + "${parameters.tool_configs:-}}";

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
     * Builds messages array using templates for Gemini generateContent API.
     * Converts messages to conversation history format.
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
            // Map role: Gemini uses "user" and "model" (not "assistant")
            String geminiRole = "user".equals(message.getRole()) ? "user" : "model";
            msgParams.put("msg_role", geminiRole);
            msgParams.put("msg_content_array", contentArray);
            StringSubstitutor msgSubstitutor = new StringSubstitutor(msgParams, "${parameters.", "}");
            messagesArray.append(msgSubstitutor.replace(MESSAGE_TEMPLATE));
        }

        return messagesArray.toString();
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

}
