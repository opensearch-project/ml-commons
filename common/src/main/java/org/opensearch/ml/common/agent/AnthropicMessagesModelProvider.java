/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
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
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.ToolUtils;

/**
 * Model provider for the Anthropic Messages API ({@code POST /v1/messages}), non-streaming.
 *
 * <p>The request carries a top-level {@code system} block, {@code messages} made of {@code text},
 * {@code image}, {@code document}, {@code tool_use} and {@code tool_result} content blocks, the
 * {@code tools} declared by function calling, and {@code max_tokens}.
 *
 * <p>Connector settings, read from the agent's {@code model_parameters}:
 * <ul>
 *   <li>{@code endpoint}: the Messages API URL. Default {@value #DEFAULT_ENDPOINT}.</li>
 *   <li>{@code auth_type}: how the credential {@code anthropic_api_key} is sent.
 *       {@code api_key} (default) sends it as the {@code x-api-key} header; {@code bearer} sends it as
 *       {@code Authorization: Bearer ...}; {@code none} sends no authentication header, for example when
 *       a gateway authenticates the client certificate instead.</li>
 *   <li>{@code anthropic_version}: the {@code anthropic-version} header. Default
 *       {@value #DEFAULT_ANTHROPIC_VERSION}. An empty value omits the header, for gateways that reject it.</li>
 *   <li>{@code mutual_tls_enabled} / {@code keystore_type}: client-certificate authentication, applied to the
 *       connector's client config. The certificate material goes in the credential
 *       ({@code client_cert_pem} and {@code client_key_pem}, or {@code client_cert_pkcs12}).</li>
 *   <li>{@code max_tokens} (a positive integer, default 4096), {@code temperature}, {@code top_p}. When
 *       {@code top_p} is set, {@code temperature} is not sent, because recent Claude models reject both together.</li>
 *   <li>Prompt caching flags, see {@link PromptCacheSettings}. Breakpoints are sent as
 *       {@code cache_control: {"type": "ephemeral"}}, with a {@code ttl} when it isn't the default 5m.</li>
 * </ul>
 */
public class AnthropicMessagesModelProvider extends ModelProvider {

    public static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    public static final String ENDPOINT_PARAMETER = "endpoint";
    public static final String AUTH_TYPE_PARAMETER = "auth_type";
    public static final String ANTHROPIC_VERSION_PARAMETER = "anthropic_version";

    public static final String AUTH_TYPE_API_KEY = "api_key";
    public static final String AUTH_TYPE_BEARER = "bearer";
    public static final String AUTH_TYPE_NONE = "none";

    public static final String API_KEY_CREDENTIAL_PLACEHOLDER = "${credential.anthropic_api_key}";

    private static final String TOOLS_PARAMETER = "_tools";
    private static final String MAX_TOKENS_PARAMETER = "max_tokens";
    private static final String BODY_PARAMETER = "body";

    // The default system prompt matters: a direct _predict against the auto-created model runs without an
    // agent runner, and an unfilled placeholder is rejected by Connector#validatePayload.
    private static final String SYSTEM_BLOCK = "{\"type\":\"text\",\"text\":\"${parameters.system_prompt:-You are a helpful assistant}\"}";

    private static final String REQUEST_BODY_TEMPLATE = "{\"model\":\"${parameters.model}\","
        + "\"max_tokens\":${parameters.max_tokens:-4096},"
        + "\"system\":["
        + SYSTEM_BLOCK
        + "],"
        + "\"messages\":[${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
        + "${parameters.tool_configs:-}"
        + "${parameters.temperature_field:-}${parameters.top_p_field:-}}";

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        validatePromptCacheSettings(modelParameters);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url(endpoint(modelParameters))
            .headers(buildHeaders(modelParameters))
            .requestBody(requestBodyTemplate(modelParameters))
            .build();

        return HttpConnector
            .builder()
            .name("Auto-generated Anthropic Messages connector for Agent")
            .description("Auto-generated connector for Anthropic Messages API")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(buildParameters(modelId, modelParameters))
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(buildClientConfig(modelParameters))
            .build();
    }

    private static Map<String, String> buildParameters(String modelId, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", modelId);
        if (modelParameters == null) {
            return parameters;
        }
        parameters.putAll(modelParameters);
        if (modelParameters.containsKey(MAX_TOKENS_PARAMETER)) {
            String maxTokens = validatePositiveIntegerParameter(modelParameters.get(MAX_TOKENS_PARAMETER), MAX_TOKENS_PARAMETER);
            parameters.put(MAX_TOKENS_PARAMETER, maxTokens);
        }
        if (modelParameters.containsKey("top_p")) {
            String topP = validateNumericParameter(modelParameters.get("top_p"), "top_p");
            parameters.put("top_p_field", ",\"top_p\":" + topP);
        } else if (modelParameters.containsKey("temperature")) {
            String temperature = validateNumericParameter(modelParameters.get("temperature"), "temperature");
            parameters.put("temperature_field", ",\"temperature\":" + temperature);
        }
        return parameters;
    }

    private static String endpoint(Map<String, String> modelParameters) {
        String endpoint = modelParameters == null ? null : modelParameters.get(ENDPOINT_PARAMETER);
        return endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
    }

    private static Map<String, String> buildHeaders(Map<String, String> modelParameters) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String authType = modelParameters == null ? null : modelParameters.get(AUTH_TYPE_PARAMETER);
        authType = authType == null || authType.isBlank() ? AUTH_TYPE_API_KEY : authType.trim().toLowerCase(Locale.ROOT);
        switch (authType) {
            case AUTH_TYPE_API_KEY -> headers.put("x-api-key", API_KEY_CREDENTIAL_PLACEHOLDER);
            case AUTH_TYPE_BEARER -> headers.put("Authorization", "Bearer " + API_KEY_CREDENTIAL_PLACEHOLDER);
            case AUTH_TYPE_NONE -> {
            }
            default -> throw new IllegalArgumentException(
                AUTH_TYPE_PARAMETER + " must be one of " + AUTH_TYPE_API_KEY + ", " + AUTH_TYPE_BEARER + ", " + AUTH_TYPE_NONE
            );
        }
        String version = modelParameters == null ? null : modelParameters.get(ANTHROPIC_VERSION_PARAMETER);
        if (version == null) {
            headers.put("anthropic-version", DEFAULT_ANTHROPIC_VERSION);
        } else if (!version.isBlank()) {
            headers.put("anthropic-version", version.trim());
        }
        return headers;
    }

    private static ConnectorClientConfig buildClientConfig(Map<String, String> modelParameters) {
        // Set agent connector to have default 3 retries
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);
        if (modelParameters != null && Boolean.parseBoolean(modelParameters.get(ConnectorClientConfig.MUTUAL_TLS_ENABLED_FIELD))) {
            connectorClientConfig.setMutualTlsEnabled(true);
            String keystoreType = modelParameters.get(ConnectorClientConfig.KEYSTORE_TYPE_FIELD);
            if (keystoreType != null && !keystoreType.isBlank()) {
                connectorClientConfig.setKeystoreType(keystoreType.trim());
            }
        }
        return connectorClientConfig;
    }

    /**
     * Validates the cache TTLs and their order. The tools and system breakpoints default to 1h here.
     * Runs at registration and again on every request, because the agent's model parameters can change
     * after the connector is created.
     */
    private static void validatePromptCacheSettings(Map<String, String> modelParameters) {
        PromptCacheSettings.validateTtlOrder(modelParameters, PromptCacheSettings.DEFAULT_SYSTEM_PROMPT_CACHE_TTL);
    }

    private static String prefixCacheTtl(Map<String, String> modelParameters) {
        return PromptCacheSettings
            .ttl(modelParameters, PromptCacheSettings.SYSTEM_PROMPT_CACHE_TTL, PromptCacheSettings.DEFAULT_SYSTEM_PROMPT_CACHE_TTL);
    }

    private static String messagesCacheTtl(Map<String, String> modelParameters) {
        return PromptCacheSettings
            .ttl(modelParameters, PromptCacheSettings.MESSAGES_CACHE_TTL, PromptCacheSettings.DEFAULT_MESSAGES_CACHE_TTL);
    }

    private static String requestBodyTemplate(Map<String, String> modelParameters) {
        if (!PromptCacheSettings.isEnabled(modelParameters, PromptCacheSettings.SYSTEM_PROMPT_CACHE_ENABLED)) {
            return REQUEST_BODY_TEMPLATE;
        }
        String cachedSystemBlock = withCacheControl(SYSTEM_BLOCK, cacheControl(prefixCacheTtl(modelParameters)));
        return REQUEST_BODY_TEMPLATE.replace(SYSTEM_BLOCK, cachedSystemBlock);
    }

    /**
     * Builds a {@code cache_control} value. The TTL is omitted when it is the API default of 5 minutes.
     */
    static String cacheControl(String ttl) {
        Map<String, Object> cacheControl = new LinkedHashMap<>();
        cacheControl.put("type", "ephemeral");
        if (!PromptCacheSettings.TTL_5_MINUTES.equals(ttl)) {
            cacheControl.put("ttl", ttl);
        }
        return StringUtils.toJson(cacheControl);
    }

    /**
     * Adds a {@code cache_control} field to a rendered JSON object.
     */
    static String withCacheControl(String jsonObject, String cacheControl) {
        return jsonObject.substring(0, jsonObject.length() - 1) + ",\"cache_control\":" + cacheControl + "}";
    }

    /**
     * Adds {@code cache_control} to the last tool definition and to the last content block of the last
     * message, each when its flag is set.
     *
     * @throws IllegalArgumentException if a cache TTL is invalid or a 1h breakpoint would follow a 5m one
     */
    @Override
    public void applyPromptCache(
        Map<String, String> llmParams,
        List<Message> messages,
        MLAgentType type,
        Map<String, String> modelParameters
    ) {
        validatePromptCacheSettings(modelParameters);
        String tools = llmParams.get(TOOLS_PARAMETER);
        if (PromptCacheSettings.isEnabled(modelParameters, PromptCacheSettings.TOOLS_CACHE_ENABLED) && tools != null) {
            String trimmedTools = tools.strip();
            if (trimmedTools.endsWith("}")) {
                llmParams.put(TOOLS_PARAMETER, withCacheControl(trimmedTools, cacheControl(prefixCacheTtl(modelParameters))));
            }
        }
        if (PromptCacheSettings.isEnabled(modelParameters, PromptCacheSettings.MESSAGES_CACHE_ENABLED)
            && messages != null
            && !messages.isEmpty()) {
            llmParams.put(BODY_PARAMETER, buildMessagesArray(messages, type, cacheControl(messagesCacheTtl(modelParameters))));
        }
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
        return "anthropic/v1/messages";
    }

    @Override
    public Map<String, String> mapTextInput(String text, MLAgentType type) {
        // ToDo: Remove workaround once PER supports messages format
        String escapedText = type == MLAgentType.PLAN_EXECUTE_AND_REFLECT ? "${parameters.prompt}" : StringEscapeUtils.escapeJson(text);
        Map<String, String> parameters = new HashMap<>();
        parameters.put(BODY_PARAMETER, renderMessage("user", List.of(textBlock(escapedText))));
        return parameters;
    }

    @Override
    public Map<String, String> mapContentBlocks(List<ContentBlock> contentBlocks, MLAgentType type) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(BODY_PARAMETER, renderMessage("user", buildContentBlocks(contentBlocks, type)));
        return parameters;
    }

    @Override
    public Map<String, String> mapMessages(List<Message> messages, MLAgentType type) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(BODY_PARAMETER, buildMessagesArray(messages, type, null));
        // body is added to no_escape_params as the json constructed is a sequence of objects and not a valid json
        // it becomes valid as REQUEST_BODY_TEMPLATE wraps this in an array
        parameters.put(ToolUtils.NO_ESCAPE_PARAMS, "_chat_history,_tools,_interactions,tool_configs,body");
        return parameters;
    }

    /**
     * Renders messages as Anthropic messages. Consecutive tool results are merged into one user message,
     * because Anthropic expects every result for an assistant turn's tool calls in the next user message.
     * Messages without any content block are left out, because Anthropic rejects empty content.
     *
     * @param lastBlockCacheControl a cache_control value for the last block of the last rendered message, or null
     */
    private String buildMessagesArray(List<Message> messages, MLAgentType type, String lastBlockCacheControl) {
        List<MessageBlocks> messageBlocks = collectMessageBlocks(messages, type);
        if (lastBlockCacheControl != null && !messageBlocks.isEmpty()) {
            List<String> lastBlocks = messageBlocks.get(messageBlocks.size() - 1).blocks();
            int last = lastBlocks.size() - 1;
            lastBlocks.set(last, withCacheControl(lastBlocks.get(last), lastBlockCacheControl));
        }
        List<String> renderedMessages = new ArrayList<>();
        for (MessageBlocks message : messageBlocks) {
            renderedMessages.add(renderMessage(message.role(), message.blocks()));
        }
        return String.join(",", renderedMessages);
    }

    /**
     * A message's role and its rendered content blocks, never empty.
     */
    private record MessageBlocks(String role, List<String> blocks) {
    }

    private List<MessageBlocks> collectMessageBlocks(List<Message> messages, MLAgentType type) {
        List<MessageBlocks> messageBlocks = new ArrayList<>();
        if (messages == null) {
            return messageBlocks;
        }
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            List<String> blocks = new ArrayList<>();
            String role = message.getRole() != null ? message.getRole() : "user";
            if (isToolResult(message)) {
                role = "user";
                while (i < messages.size() && isToolResult(messages.get(i))) {
                    blocks.add(toolResultBlock(messages.get(i)));
                    i++;
                }
                i--;
            } else {
                blocks.addAll(buildContentBlocks(message.getContent(), type));
                blocks.addAll(toolUseBlocks(message));
            }
            if (!blocks.isEmpty()) {
                messageBlocks.add(new MessageBlocks(role, blocks));
            }
        }
        return messageBlocks;
    }

    private static boolean isToolResult(Message message) {
        return "tool".equalsIgnoreCase(message.getRole());
    }

    private static String renderMessage(String role, List<String> blocks) {
        return "{\"role\":\"" + StringEscapeUtils.escapeJson(role) + "\",\"content\":[" + String.join(",", blocks) + "]}";
    }

    private static String textBlock(String escapedText) {
        return "{\"type\":\"text\",\"text\":\"" + escapedText + "\"}";
    }

    private static List<String> toolUseBlocks(Message message) {
        List<String> blocks = new ArrayList<>();
        if (!"assistant".equalsIgnoreCase(message.getRole()) || message.getToolCalls() == null) {
            return blocks;
        }
        for (ToolCall toolCall : message.getToolCalls()) {
            blocks
                .add(
                    "{\"type\":\"tool_use\",\"id\":\""
                        + StringEscapeUtils.escapeJson(toolCall.getId())
                        + "\",\"name\":\""
                        + StringEscapeUtils.escapeJson(toolCall.getFunction().getName())
                        + "\",\"input\":"
                        + toolInputJson(toolCall.getFunction().getArguments())
                        + "}"
                );
        }
        return blocks;
    }

    /**
     * The body isn't escaped again before it's sent, so tool arguments are parsed and re-serialized instead of
     * spliced in as given. Anthropic requires {@code input} to be a JSON object.
     */
    static String toolInputJson(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "{}";
        }
        String trimmed = arguments.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalArgumentException("Tool call arguments must be a JSON object");
        }
        return StringUtils.toJson(StringUtils.fromJson(trimmed, "input"));
    }

    private static String toolResultBlock(Message message) {
        String text = "";
        if (message.getContent() != null) {
            for (ContentBlock block : message.getContent()) {
                if (block.getType() == ContentType.TEXT && block.getText() != null) {
                    text = block.getText();
                    break;
                }
            }
        }
        return "{\"type\":\"tool_result\",\"tool_use_id\":\""
            + StringEscapeUtils.escapeJson(message.getToolCallId())
            + "\",\"content\":\""
            + StringEscapeUtils.escapeJson(text)
            + "\""
            + (Boolean.TRUE.equals(message.getToolError()) ? ",\"is_error\":true" : "")
            + "}";
    }

    /**
     * Renders content blocks. Text, images and PDF documents are supported; video is not part of the
     * Anthropic Messages API.
     */
    private List<String> buildContentBlocks(List<ContentBlock> contentBlocks, MLAgentType type) {
        List<String> blocks = new ArrayList<>();
        if (contentBlocks == null) {
            return blocks;
        }
        for (ContentBlock block : contentBlocks) {
            switch (block.getType()) {
                case TEXT -> {
                    if (type == MLAgentType.PLAN_EXECUTE_AND_REFLECT) {
                        // ToDo: Remove workaround after PER supports messages format
                        blocks.add(textBlock("${parameters.prompt}"));
                    } else if (!isBlank(block.getText())) {
                        // Anthropic rejects empty text blocks, so blank text is left out
                        blocks.add(textBlock(StringEscapeUtils.escapeJson(block.getText())));
                    }
                }
                case IMAGE -> blocks.add(imageBlock(block.getImage()));
                case DOCUMENT -> blocks.add(documentBlock(block.getDocument()));
                case VIDEO -> throw new IllegalArgumentException("Video content is not supported in Anthropic Messages API");
                default -> {
                }
            }
        }
        return blocks;
    }

    private static String imageBlock(ImageContent image) {
        return "{\"type\":\"image\",\"source\":" + sourceObject(image.getType(), "image/" + image.getFormat(), image.getData()) + "}";
    }

    private static String documentBlock(DocumentContent document) {
        if (!"pdf".equalsIgnoreCase(document.getFormat())) {
            throw new IllegalArgumentException("Only PDF documents are supported in Anthropic Messages API, got: " + document.getFormat());
        }
        return "{\"type\":\"document\",\"source\":" + sourceObject(document.getType(), "application/pdf", document.getData()) + "}";
    }

    private static String sourceObject(SourceType sourceType, String mediaType, String data) {
        if (sourceType == SourceType.BASE64) {
            return "{\"type\":\"base64\",\"media_type\":\""
                + StringEscapeUtils.escapeJson(mediaType)
                + "\",\"data\":\""
                + StringEscapeUtils.escapeJson(data)
                + "\"}";
        }
        if (sourceType == SourceType.URL) {
            return "{\"type\":\"url\",\"url\":\"" + StringEscapeUtils.escapeJson(data) + "\"}";
        }
        throw new IllegalArgumentException("Source type is required. Supported types: URL, BASE64");
    }

    /**
     * Extracts the message from an Anthropic Messages response. The response is the message itself, so this
     * keeps only its role and content, the fields a request message may carry.
     *
     * Expected response structure:
     * {"id": "...", "type": "message", "role": "assistant", "content": [...], "stop_reason": "...", "usage": {...}}
     *
     * @param responseData Map containing the Anthropic Messages API response data
     * @return JSON string of the message (role and content), or null if there is no content
     */
    @Override
    public String extractMessageFromResponse(Map<String, ?> responseData) {
        if (responseData == null || !(responseData.get("content") instanceof List)) {
            return null;
        }
        Object role = responseData.get("role");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role != null ? role : "assistant");
        message.put("content", responseData.get("content"));
        return StringUtils.toJson(message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Message parseToUnifiedMessage(String json) {
        Map<String, Object> parsed = StringUtils.fromJson(json, "response");
        if (parsed == null) {
            return null;
        }
        String role = (String) parsed.get("role");
        List<ContentBlock> textBlocks = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        String toolCallId = null;
        boolean toolError = false;

        Object content = parsed.get("content");
        if (content instanceof String text && !text.isBlank()) {
            textBlocks.add(textContent(text));
        } else if (content instanceof List) {
            for (Object item : (List<Object>) content) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> block = (Map<String, Object>) item;
                String blockType = String.valueOf(block.get("type"));
                if ("text".equals(blockType) && !isBlank(block.get("text"))) {
                    textBlocks.add(textContent(String.valueOf(block.get("text"))));
                } else if ("tool_use".equals(blockType)) {
                    toolCalls.add(toToolCall(block));
                } else if ("tool_result".equals(blockType)) {
                    // A unified message holds one tool result; merging several would drop all but one id.
                    if (toolCallId != null) {
                        throw new IllegalArgumentException(
                            "A message with several tool_result blocks can't be parsed into one message; send one per result"
                        );
                    }
                    // Anthropic sends tool results as role=user; map to role=tool for the unified format
                    role = "tool";
                    toolCallId = String.valueOf(block.getOrDefault("tool_use_id", ""));
                    toolError = Boolean.TRUE.equals(block.get("is_error"));
                    String resultText = toolResultText(block.get("content"));
                    if (!resultText.isBlank()) {
                        textBlocks.add(textContent(resultText));
                    }
                }
            }
        }

        if (textBlocks.isEmpty() && toolCalls.isEmpty() && toolCallId == null) {
            return null;
        }
        Message message = new Message();
        message.setRole(role != null ? role : "assistant");
        message.setContent(textBlocks.isEmpty() ? null : textBlocks);
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        if (toolCallId != null) {
            message.setToolCallId(toolCallId);
        }
        if (toolError) {
            message.setToolError(true);
        }
        return message;
    }

    private static boolean isBlank(Object text) {
        return text == null || String.valueOf(text).isBlank();
    }

    private static ContentBlock textContent(String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        return block;
    }

    private static ToolCall toToolCall(Map<String, Object> toolUse) {
        String id = String.valueOf(toolUse.getOrDefault("id", ""));
        String name = String.valueOf(toolUse.getOrDefault("name", ""));
        Object input = toolUse.get("input");
        String arguments = input != null ? StringUtils.toJson(input) : "{}";
        return new ToolCall(id, "function", new ToolCall.ToolFunction(name, arguments));
    }

    /**
     * A tool_result's content is either a string or a list of content blocks; this joins the text blocks.
     */
    @SuppressWarnings("unchecked")
    private static String toolResultText(Object content) {
        if (content instanceof String) {
            return (String) content;
        }
        StringBuilder joined = new StringBuilder();
        if (content instanceof List) {
            for (Object item : (List<Object>) content) {
                if (item instanceof Map && ((Map<String, Object>) item).get("text") != null) {
                    joined.append(((Map<String, Object>) item).get("text"));
                }
            }
        }
        return joined.toString();
    }
}
