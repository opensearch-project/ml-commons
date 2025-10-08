/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
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
    public Map<String, String> mapTextInput(String text) {
        Map<String, String> parameters = new HashMap<>();

        // Use StringSubstitutor for parameter replacement
        Map<String, String> templateParams = new HashMap<>();
        templateParams.put("user_text", StringEscapeUtils.escapeJson(text));

        StringSubstitutor substitutor = new StringSubstitutor(templateParams, "${parameters.", "}");
        String body = substitutor.replace(TEXT_INPUT_BODY_TEMPLATE);
        parameters.put("body", body);

        return parameters;
    }

    @Override
    public Map<String, String> mapContentBlocks(List<ContentBlock> contentBlocks) {
        Map<String, String> parameters = new HashMap<>();

        // Use StringSubstitutor for parameter replacement
        String contentArray = buildContentArrayFromBlocks(contentBlocks);
        Map<String, String> templateParams = new HashMap<>();
        templateParams.put("content_array", contentArray);

        StringSubstitutor substitutor = new StringSubstitutor(templateParams, "${parameters.", "}");
        String body = substitutor.replace(CONTENT_BLOCKS_BODY_TEMPLATE);
        parameters.put("body", body);

        return parameters;
    }

    @Override
    public Map<String, String> mapMessages(List<Message> messages) {
        Map<String, String> parameters = new HashMap<>();
        String messagesString = buildMessagesArray(messages);
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
    private String buildContentArrayFromBlocks(List<ContentBlock> blocks) {
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
                    textParams.put("content_text", StringEscapeUtils.escapeJson(block.getText()));
                    StringSubstitutor textSubstitutor = new StringSubstitutor(textParams, "${parameters.", "}");
                    contentArray.append(textSubstitutor.replace(TEXT_CONTENT_TEMPLATE));
                    break;
                case IMAGE:
                    ImageContent image = block.getImage();
                    Map<String, Object> imageParams = new HashMap<>();
                    imageParams.put("image_format", image.getFormat());
                    imageParams.put("image_data", image.getData());
                    // Map SourceType to Bedrock Converse API source type
                    String imageSourceType = mapSourceTypeToBedrock(image.getType());
                    imageParams.put("image_source_type", imageSourceType);
                    StringSubstitutor imageSubstitutor = new StringSubstitutor(imageParams, "${parameters.", "}");
                    contentArray.append(imageSubstitutor.replace(IMAGE_CONTENT_TEMPLATE));
                    break;
                case DOCUMENT:
                    DocumentContent document = block.getDocument();
                    Map<String, Object> docParams = new HashMap<>();
                    docParams.put("doc_format", document.getFormat());
                    docParams.put("doc_name", "document");
                    docParams.put("doc_data", document.getData());
                    // Map SourceType to Bedrock Converse API source type
                    String docSourceType = mapSourceTypeToBedrock(document.getType());
                    docParams.put("doc_source_type", docSourceType);
                    StringSubstitutor docSubstitutor = new StringSubstitutor(docParams, "${parameters.", "}");
                    contentArray.append(docSubstitutor.replace(DOCUMENT_CONTENT_TEMPLATE));
                    break;
                case VIDEO:
                    VideoContent video = block.getVideo();
                    Map<String, Object> videoParams = new HashMap<>();
                    videoParams.put("video_format", video.getFormat());
                    videoParams.put("video_data", video.getData());
                    // Map SourceType to Bedrock Converse API source type
                    String videoSourceType = mapSourceTypeToBedrock(video.getType());
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
     * Converts messages to conversation history format, excluding the last user message
     * which becomes the current input.
     */
    private String buildMessagesArray(List<Message> messages) {
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

            String contentArray = buildContentArrayFromBlocks(message.getContent());
            Map<String, Object> msgParams = new HashMap<>();
            msgParams.put("msg_role", message.getRole());
            msgParams.put("msg_content_array", contentArray);
            StringSubstitutor msgSubstitutor = new StringSubstitutor(msgParams, "${parameters.", "}");
            messagesArray.append(msgSubstitutor.replace(MESSAGE_TEMPLATE));
        }

        return messagesArray.toString();
    }

    /**
     * Maps SourceType to Bedrock Converse API source field names.
     * 
     * @param sourceType the source type from content
     * @return the corresponding Bedrock API source field name
     */
    private String mapSourceTypeToBedrock(SourceType sourceType) {
        if (sourceType == SourceType.URL) {
            return "s3Location"; // Bedrock Converse API uses s3Location for URL-based content
        }

        return "bytes";
    }
}