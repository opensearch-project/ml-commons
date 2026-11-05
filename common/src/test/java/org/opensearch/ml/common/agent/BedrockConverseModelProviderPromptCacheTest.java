/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * Prompt cache breakpoints in the Bedrock Converse provider: placement per flag, and an unchanged request
 * when every flag is off.
 */
public class BedrockConverseModelProviderPromptCacheTest {

    // The request body template as it was before prompt caching, kept verbatim to prove it is unchanged
    private static final String LEGACY_REQUEST_BODY_TEMPLATE =
        "{\"system\": [{\"text\": \"${parameters.system_prompt:-You are a helpful assistant}\"}], "
            + "\"messages\": [${parameters._chat_history:-}${parameters.body}${parameters._interactions:-}]"
            + "${parameters.tool_configs:-}, "
            + "\"inferenceConfig\": {\"maxTokens\": ${parameters.max_tokens:-4096}"
            + "${parameters.temperature_field:-}${parameters.top_p_field:-}}"
            + " }";

    // As set by BedrockConverseFunctionCalling#configure
    private static final String TOOL_CONFIGS = ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}";
    private static final String TWO_TOOLS = "{\"toolSpec\":{\"name\":\"search\",\"description\":\"Search\",\"inputSchema\": {\"json\": "
        + "{\"type\":\"object\"} } }}, {\"toolSpec\":{\"name\":\"lookup\",\"description\":\"Lookup\",\"inputSchema\": {\"json\": "
        + "{\"type\":\"object\"} } }}";
    private static final Map<String, String> CREDENTIAL = Map.of("access_key", "test_access_key", "secret_key", "test_secret_key");
    private static final Map<String, Object> CACHE_POINT = Map.of("cachePoint", Map.of("type", "default"));

    private BedrockConverseModelProvider provider;

    @Before
    public void setUp() {
        provider = new BedrockConverseModelProvider();
    }

    @Test
    public void testFlagsOff_TemplateUnchanged() {
        assertEquals(LEGACY_REQUEST_BODY_TEMPLATE, template(provider.createConnector("model", CREDENTIAL, null)));
        assertEquals(LEGACY_REQUEST_BODY_TEMPLATE, template(provider.createConnector("model", CREDENTIAL, new HashMap<>())));
        Map<String, String> flagsFalse = Map
            .of("system_prompt_cache_enabled", "false", "tools_cache_enabled", "false", "messages_cache_enabled", "false");
        assertEquals(LEGACY_REQUEST_BODY_TEMPLATE, template(provider.createConnector("model", CREDENTIAL, flagsFalse)));
    }

    @Test
    public void testFlagsOff_RenderedRequestByteIdentical() {
        Map<String, String> modelParameters = Map.of("system_prompt_cache_enabled", "false", "temperature", "0.5");
        Connector connector = provider.createConnector("model", CREDENTIAL, modelParameters);
        List<Message> messages = conversation();
        Map<String, String> legacyParams = requestParams(connector, messages);
        Map<String, String> params = new HashMap<>(legacyParams);

        provider.applyPromptCache(params, messages, MLAgentType.CONVERSATIONAL_V2, modelParameters);

        assertEquals(legacyParams, params);
        String expected = render(LEGACY_REQUEST_BODY_TEMPLATE, legacyParams);
        assertEquals(expected, render(template(connector), params));
        assertFalse(expected.contains("cachePoint"));
    }

    @Test
    public void testSystemPromptCache_CachePointAfterSystemPrompt() {
        Connector connector = provider.createConnector("model", CREDENTIAL, Map.of("system_prompt_cache_enabled", "true"));

        Map<String, Object> request = renderRequest(connector, requestParams(connector, conversation()));

        assertEquals(List.of(Map.of("text", "Be brief"), CACHE_POINT), request.get("system"));
        assertEquals(1, countCachePoints(request));
    }

    @Test
    public void testSystemPromptCache_ExplicitTypeAndTtl() {
        Map<String, String> modelParameters = Map
            .of("system_prompt_cache_enabled", "true", "system_prompt_cache_type", "default", "system_prompt_cache_ttl", "1h");
        Connector connector = provider.createConnector("model", CREDENTIAL, modelParameters);

        Map<String, Object> request = renderRequest(connector, requestParams(connector, conversation()));

        assertEquals(Map.of("cachePoint", Map.of("type", "default", "ttl", "1h")), list(request.get("system")).get(1));
    }

    @Test
    public void testSystemPromptCache_InvalidTtl() {
        Map<String, String> modelParameters = Map.of("system_prompt_cache_enabled", "true", "system_prompt_cache_ttl", "10m");

        assertThrows(IllegalArgumentException.class, () -> provider.createConnector("model", CREDENTIAL, modelParameters));
    }

    @Test
    public void testOneHourMessagesBreakpointAfterDefaultFiveMinuteSystemIsRejected() {
        // Bedrock applies 5m when cachePoint has no ttl, so the system breakpoint here is 5m.
        Map<String, String> modelParameters = Map
            .of("system_prompt_cache_enabled", "true", "messages_cache_enabled", "true", "messages_cache_ttl", "1h");

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> provider.createConnector("model", CREDENTIAL, modelParameters)
        );
        assertTrue(e.getMessage().contains("messages_cache_ttl"));
        provider
            .createConnector(
                "model",
                CREDENTIAL,
                Map
                    .of(
                        "system_prompt_cache_enabled",
                        "true",
                        "messages_cache_enabled",
                        "true",
                        "system_prompt_cache_ttl",
                        "1h",
                        "messages_cache_ttl",
                        "1h"
                    )
            );
    }

    @Test
    public void testToolsCache_CachePointAfterLastTool() {
        Map<String, String> modelParameters = Map.of("tools_cache_enabled", "true");
        Connector connector = provider.createConnector("model", CREDENTIAL, modelParameters);
        Map<String, String> params = requestParams(connector, conversation());

        provider.applyPromptCache(params, conversation(), MLAgentType.CONVERSATIONAL_V2, modelParameters);
        Map<String, Object> request = renderRequest(connector, params);

        List<Object> tools = list(map(request.get("toolConfig")).get("tools"));
        assertEquals(3, tools.size());
        assertEquals("search", map(map(tools.get(0)).get("toolSpec")).get("name"));
        assertEquals("lookup", map(map(tools.get(1)).get("toolSpec")).get("name"));
        assertEquals(CACHE_POINT, tools.get(2));
        assertEquals(1, countCachePoints(request));
    }

    @Test
    public void testToolsCache_NoToolsNoCachePoint() {
        Map<String, String> params = new HashMap<>();
        params.put("_tools", "");

        provider.applyPromptCache(params, conversation(), MLAgentType.CONVERSATIONAL_V2, Map.of("tools_cache_enabled", "true"));

        assertEquals("", params.get("_tools"));
    }

    @Test
    public void testMessagesCache_CachePointAtEndOfLastMessage() {
        Map<String, String> modelParameters = Map.of("messages_cache_enabled", "true");
        Connector connector = provider.createConnector("model", CREDENTIAL, modelParameters);
        Map<String, String> params = requestParams(connector, conversation());

        provider.applyPromptCache(params, conversation(), MLAgentType.CONVERSATIONAL_V2, modelParameters);
        Map<String, Object> request = renderRequest(connector, params);

        List<Object> messages = list(request.get("messages"));
        assertEquals(3, messages.size());
        List<Object> lastContent = list(map(messages.get(2)).get("content"));
        assertEquals(3, lastContent.size());
        assertTrue(map(lastContent.get(0)).containsKey("toolResult"));
        assertTrue(map(lastContent.get(1)).containsKey("toolResult"));
        assertEquals(CACHE_POINT, lastContent.get(2));
        assertEquals(1, countCachePoints(request));
    }

    @Test
    public void testMessagesCache_ExplicitTtl() {
        Map<String, String> modelParameters = Map.of("messages_cache_enabled", "true", "messages_cache_ttl", "5m");
        Map<String, String> params = new HashMap<>();

        provider.applyPromptCache(params, List.of(textMessage("user", "hi")), MLAgentType.CONVERSATIONAL_V2, modelParameters);

        assertTrue(params.get("body").endsWith("{\"cachePoint\":{\"type\":\"default\",\"ttl\":\"5m\"}}]}"));
    }

    @Test
    public void testMessagesCache_EmptyLastMessageGetsNoCachePoint() {
        Message empty = new Message();
        empty.setRole("assistant");
        Map<String, String> params = new HashMap<>();

        provider
            .applyPromptCache(
                params,
                List.of(textMessage("user", "hi"), empty),
                MLAgentType.CONVERSATIONAL_V2,
                Map.of("messages_cache_enabled", "true")
            );

        assertFalse(params.get("body").contains("cachePoint"));
    }

    @Test
    public void testAllFlags_ThreeCachePoints() {
        Map<String, String> modelParameters = Map
            .of("system_prompt_cache_enabled", "true", "tools_cache_enabled", "true", "messages_cache_enabled", "true");
        Connector connector = provider.createConnector("model", CREDENTIAL, modelParameters);
        Map<String, String> params = requestParams(connector, conversation());

        provider.applyPromptCache(params, conversation(), MLAgentType.CONVERSATIONAL_V2, modelParameters);
        String payload = connector.createPayload(ConnectorAction.ActionType.PREDICT.name(), params);
        connector.validatePayload(payload);

        assertTrue(StringUtils.isJson(payload));
        assertEquals(3, countCachePoints(parse(payload)));
    }

    @Test
    public void testNullModelParameters_NoChange() {
        Map<String, String> params = new HashMap<>();
        params.put("_tools", TWO_TOOLS);

        provider.applyPromptCache(params, conversation(), MLAgentType.CONVERSATIONAL_V2, null);

        assertEquals(Map.of("_tools", TWO_TOOLS), params);
        assertNull(params.get("body"));
    }

    // ==================== Helpers ====================

    private static String template(Connector connector) {
        return connector.getActions().get(0).getRequestBody();
    }

    private Map<String, String> requestParams(Connector connector, List<Message> messages) {
        Map<String, String> params = new HashMap<>(connector.getParameters());
        params.putAll(provider.mapMessages(messages, MLAgentType.CONVERSATIONAL_V2));
        params.put("system_prompt", "Be brief");
        params.put("tool_configs", TOOL_CONFIGS);
        params.put("_tools", TWO_TOOLS);
        return params;
    }

    private static Map<String, Object> renderRequest(Connector connector, Map<String, String> params) {
        String payload = connector.createPayload(ConnectorAction.ActionType.PREDICT.name(), params);
        connector.validatePayload(payload);
        return parse(payload);
    }

    private static String render(String template, Map<String, String> params) {
        return new StringSubstitutor(params, "${parameters.", "}").replace(template);
    }

    /**
     * A user question, an assistant turn calling two tools, and the two tool results.
     */
    private static List<Message> conversation() {
        Message assistant = textMessage("assistant", "Checking");
        List<ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new ToolCall("call_1", "function", new ToolCall.ToolFunction("search", "{\"q\":\"a\"}")));
        toolCalls.add(new ToolCall("call_2", "function", new ToolCall.ToolFunction("lookup", "{}")));
        assistant.setToolCalls(toolCalls);
        return List.of(textMessage("user", "question"), assistant, toolResult("call_1", "r1"), toolResult("call_2", "r2"));
    }

    private static Message textMessage(String role, String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        return new Message(role, List.of(block));
    }

    private static Message toolResult(String toolCallId, String text) {
        Message message = textMessage("tool", text);
        message.setToolCallId(toolCallId);
        return message;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return StringUtils.gson.fromJson(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static int countCachePoints(Map<String, Object> request) {
        String json = StringUtils.gson.toJson(request);
        return json.split("\"cachePoint\"", -1).length - 1;
    }
}
