/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchemaTests.productSchema;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchemaTests.spec;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplateResolver;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.function_calling.BedrockConverseFunctionCalling;
import org.opensearch.ml.engine.function_calling.GeminiV1BetaGenerateContentFunctionCalling;
import org.opensearch.transport.client.Client;

@SuppressWarnings("unchecked")
public class SearchTemplateFillToolTests {

    private static final String RENDERED = "{\"query\":{\"match\":{\"title\":\"shoes\"}},\"size\":5}";
    private static final String FALLBACK = "{\"query\":{\"match_all\":{}}}";

    private Client client;
    private AgenticSearchTemplateResolver resolver;
    private MLFeatureEnabledSetting featureSetting;
    private QueryPlanningTool fallbackTool;
    private Map<String, AgenticSearchTemplate> registered;
    private AtomicReference<Map<String, Object>> renderedParams;
    private ArgumentCaptor<MLPredictionTaskRequest> predictCaptor;

    @Before
    public void setup() {
        client = mock(Client.class);
        resolver = mock(AgenticSearchTemplateResolver.class);
        featureSetting = mock(MLFeatureEnabledSetting.class);
        fallbackTool = mock(QueryPlanningTool.class);
        when(featureSetting.isAgenticSearchTemplateEnabled()).thenReturn(true);

        registered = new LinkedHashMap<>();
        registered.put("product_search", template("product_search", "products", productSchema()));
        doAnswer(invocation -> {
            ActionListener<Map<String, AgenticSearchTemplate>> listener = invocation.getArgument(2);
            listener.onResponse(registered);
            return null;
        }).when(resolver).getTemplates(any(), any(), any());

        renderedParams = new AtomicReference<>();
        doAnswer(invocation -> {
            renderedParams.set(invocation.getArgument(1));
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onResponse(RENDERED);
            return null;
        }).when(resolver).renderTemplate(any(), anyMap(), any());

        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(FALLBACK);
            return null;
        }).when(fallbackTool).run(any(), any());

        predictCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
    }

    private static AgenticSearchTemplate template(String id, String index, Map<String, Object> schema) {
        return AgenticSearchTemplate.builder().templateId(id).indexBinding(index).description(id + " search").paramSchema(schema).build();
    }

    private SearchTemplateFillTool tool(String... templateIds) {
        return tool(fallbackTool, templateIds);
    }

    private SearchTemplateFillTool tool(QueryPlanningTool fallback, String... templateIds) {
        return new SearchTemplateFillTool(
            client,
            resolver,
            featureSetting,
            "model-1",
            new BedrockConverseFunctionCalling(),
            List.of(templateIds),
            fallback
        );
    }

    private Exception runFailing(SearchTemplateFillTool tool, Map<String, String> params) {
        AtomicReference<Exception> failure = new AtomicReference<>();
        tool.run(params, ActionListener.wrap(r -> { throw new AssertionError("expected failure, got " + r); }, failure::set));
        return failure.get();
    }

    /** Make the model answer with a Bedrock tool call to {@code toolName} carrying {@code input}. */
    private void modelFills(String toolName, Map<String, Object> input) {
        Map<String, Object> response = Map
            .of(
                "stopReason",
                "tool_use",
                "output",
                Map
                    .of(
                        "message",
                        Map.of("content", List.of(Map.of("toolUse", Map.of("name", toolName, "input", input, "toolUseId", "t1"))))
                    )
            );
        ModelTensor tensor = ModelTensor.builder().dataAsMap(response).build();
        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(tensor)).build()))
            .build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), predictCaptor.capture(), any());
    }

    private static Map<String, String> question() {
        Map<String, String> params = new HashMap<>();
        params.put("question", "5 cheapest shoes");
        params.put("index_name", "products");
        return params;
    }

    private Object run(SearchTemplateFillTool tool, Map<String, String> params) {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        tool.run(params, ActionListener.wrap(result::set, failure::set));
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return result.get();
    }

    private Map<String, String> predictParameters() {
        return ((RemoteInferenceInputDataSet) predictCaptor.getValue().getMlInput().getInputDataset()).getParameters();
    }

    @Test
    public void single_fillsAndRenders() {
        modelFills("FillTemplate", Map.of("query_text", "shoes", "size", 5.0, "cannot_express", false));

        assertEquals(RENDERED, run(tool("product_search"), question()));
        assertEquals(Map.of("query_text", "shoes", "size", 5L), renderedParams.get());
        verify(fallbackTool, never()).run(any(), any());

        Map<String, String> sent = predictParameters();
        assertTrue(sent.get("tool_configs").contains("\"toolChoice\""));
        assertTrue(sent.get("no_escape_params").contains("tool_configs"));
        assertTrue(sent.get("user_prompt").contains("5 cheapest shoes"));
        assertEquals(sent.get("user_prompt"), sent.get("prompt"));
    }

    @Test
    public void fillCall_overridesFallbackOutputFilters() {
        modelFills("FillTemplate", Map.of("query_text", "shoes", "size", 5));
        Map<String, String> params = question();
        params.put("response_filter", "$.output.message.content[0].text");
        params.put("output_processors", "[]");
        params.put("no_escape_params", "_chat_history");

        run(tool("product_search"), params);
        Map<String, String> sent = predictParameters();
        // Overridden rather than removed, so a filter set on the connector itself is replaced too.
        assertEquals("$", sent.get("response_filter"));
        assertEquals("[]", sent.get("output_processors"));
        assertEquals("_chat_history,tool_configs", sent.get("no_escape_params"));
    }

    @Test
    public void abstain_fallsBack() {
        modelFills("FillTemplate", Map.of("cannot_express", true));
        assertEquals(FALLBACK, run(tool("product_search"), question()));
        verify(resolver, never()).renderTemplate(any(), anyMap(), any());
    }

    @Test
    public void invalidFill_fallsBack() {
        // size is required and missing.
        modelFills("FillTemplate", Map.of("query_text", "shoes"));
        assertEquals(FALLBACK, run(tool("product_search"), question()));
        verify(resolver, never()).renderTemplate(any(), anyMap(), any());
    }

    @Test
    public void modelSkipsTheTool_fallsBack() {
        modelFills("SomethingElse", Map.of());
        assertEquals(FALLBACK, run(tool("product_search"), question()));
    }

    @Test
    public void renderFailure_fallsBack() {
        modelFills("FillTemplate", Map.of("query_text", "shoes", "size", 5));
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onFailure(new IllegalArgumentException("bad render"));
            return null;
        }).when(resolver).renderTemplate(any(), anyMap(), any());
        assertEquals(FALLBACK, run(tool("product_search"), question()));
    }

    @Test
    public void noUsableTemplate_fallsBackWithoutCallingModel() {
        // The resolver drops templates bound to another index or unreadable by the caller.
        registered.clear();
        assertEquals(FALLBACK, run(tool("product_search"), question()));
        verify(client, never()).execute(any(), any(), any());
        verify(resolver).getTemplates(eq(List.of("product_search")), eq("products"), any());
    }

    @Test
    public void unregisteredTemplate_fallsBack() {
        assertEquals(FALLBACK, run(tool("missing"), question()));
        verify(client, never()).execute(any(), any(), any());
    }

    @Test
    public void lookupFailure_surfacesWithoutFallback() {
        RuntimeException boom = new RuntimeException("boom");
        doAnswer(invocation -> {
            ActionListener<Map<String, AgenticSearchTemplate>> listener = invocation.getArgument(2);
            listener.onFailure(boom);
            return null;
        }).when(resolver).getTemplates(any(), any(), any());
        assertEquals(boom, runFailing(tool("product_search"), question()));
        verify(fallbackTool, never()).run(any(), any());
    }

    @Test
    public void multi_choosesAndFillsOneTemplate() {
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("query_text", spec("string", true, "", null));
        registered.put("semantic_search", template("semantic_search", "products", semantic));
        modelFills(
            "SelectAndFillTemplate",
            Map
                .of(
                    "cannot_express",
                    false,
                    "template_id",
                    "semantic_search",
                    "t1__query_text",
                    "running shoes",
                    "t0__query_text",
                    "ignored"
                )
        );

        assertEquals(RENDERED, run(tool("product_search", "semantic_search"), question()));
        verify(resolver).renderTemplate(eq("semantic_search"), anyMap(), any());
        assertEquals(Map.of("query_text", "running shoes"), renderedParams.get());
        assertTrue(predictParameters().get("user_prompt").contains("- semantic_search (parameters prefixed t1__): semantic_search search"));
    }

    @Test
    public void multi_choosingNone_fallsBack() {
        registered.put("semantic_search", template("semantic_search", "products", productSchema()));
        modelFills("SelectAndFillTemplate", Map.of("template_id", "none"));
        assertEquals(FALLBACK, run(tool("product_search", "semantic_search"), question()));
    }

    @Test
    public void multi_unknownChoice_fallsBack() {
        registered.put("semantic_search", template("semantic_search", "products", productSchema()));
        modelFills("SelectAndFillTemplate", Map.of("template_id", "made_up"));
        assertEquals(FALLBACK, run(tool("product_search", "semantic_search"), question()));
        verify(resolver, never()).renderTemplate(any(), anyMap(), any());
    }

    @Test
    public void candidates_areCappedInConfiguredOrder() {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "t" + i;
            ids.add(id);
            registered.put(id, template(id, "products", productSchema()));
        }
        List<AgenticSearchTemplate> candidates = tool(ids.toArray(new String[0])).selectCandidates(registered);
        assertEquals(8, candidates.size());
        assertEquals("t0", candidates.get(0).getTemplateId());
        assertEquals("t7", candidates.get(7).getTemplateId());
    }

    @Test
    public void abstainFlagAsFalseString_stillFills() {
        modelFills("FillTemplate", Map.of("query_text", "shoes", "size", 5, "cannot_express", "false"));
        assertEquals(RENDERED, run(tool("product_search"), question()));
    }

    @Test
    public void predictFailure_surfacesWithoutFallback() {
        // A throttled or unauthorized model call would fail the same way through the fallback.
        OpenSearchStatusException throttled = new OpenSearchStatusException("throttled", RestStatus.TOO_MANY_REQUESTS);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(throttled);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());
        assertEquals(throttled, runFailing(tool("product_search"), question()));
        verify(fallbackTool, never()).run(any(), any());
    }

    @Test
    public void fallbackDisabled_declineFails() {
        modelFills("FillTemplate", Map.of("cannot_express", true));
        Exception e = runFailing(tool((QueryPlanningTool) null, "product_search"), question());
        assertTrue(e instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) e).status());
        assertTrue(e.getMessage().contains("fallback is disabled"));
    }

    @Test
    public void fallbackDisabled_noUsableTemplateFails() {
        registered.clear();
        assertTrue(runFailing(tool((QueryPlanningTool) null, "product_search"), question()) instanceof OpenSearchStatusException);
        verify(client, never()).execute(any(), any(), any());
    }

    @Test
    public void streamFlag_isDroppedFromFillAndFallback() {
        modelFills("FillTemplate", Map.of("cannot_express", true));
        Map<String, String> params = question();
        params.put("stream", "true");
        assertEquals(FALLBACK, run(tool("product_search"), params));
        assertFalse(predictParameters().containsKey("stream"));
        ArgumentCaptor<Map<String, String>> fallbackParams = ArgumentCaptor.forClass(Map.class);
        verify(fallbackTool).run(fallbackParams.capture(), any());
        assertFalse(fallbackParams.getValue().containsKey("stream"));
        assertEquals("true", params.get("stream"));
    }

    @Test
    public void fallback_pointsPromptAtUserPrompt() {
        modelFills("FillTemplate", Map.of("cannot_express", true));
        run(tool("product_search"), question());
        ArgumentCaptor<Map<String, String>> fallbackParams = ArgumentCaptor.forClass(Map.class);
        verify(fallbackTool).run(fallbackParams.capture(), any());
        assertEquals("${parameters.user_prompt}", fallbackParams.getValue().get("prompt"));
        assertEquals("5 cheapest shoes", fallbackParams.getValue().get("question"));
    }

    @Test
    public void downstreamListenerThrows_isAnsweredOnce() {
        modelFills("FillTemplate", Map.of("query_text", "shoes", "size", 5));
        AtomicInteger calls = new AtomicInteger();
        ActionListener<Object> throwing = new ActionListener<>() {
            @Override
            public void onResponse(Object response) {
                calls.incrementAndGet();
                throw new IllegalStateException("caller failed");
            }

            @Override
            public void onFailure(Exception e) {
                calls.incrementAndGet();
            }
        };
        assertThrows(IllegalStateException.class, () -> tool("product_search").run(question(), throwing));
        assertEquals(1, calls.get());
        verify(fallbackTool, never()).run(any(), any());
    }

    @Test
    public void gemini_sendsEnumsAsStrings_andMapsTheAnswerBack() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("size", spec("number", true, "", List.of(5, 10)));
        registered.put("sized", template("sized", "products", schema));
        Map<String, Object> response = Map
            .of(
                "candidates",
                List
                    .of(
                        Map
                            .of(
                                "content",
                                Map
                                    .of(
                                        "parts",
                                        List.of(Map.of("functionCall", Map.of("name", "FillTemplate", "args", Map.of("size", "10"))))
                                    )
                            )
                    )
            );
        ModelTensor tensor = ModelTensor.builder().dataAsMap(response).build();
        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(tensor)).build()))
            .build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), predictCaptor.capture(), any());

        SearchTemplateFillTool gemini = new SearchTemplateFillTool(
            client,
            resolver,
            featureSetting,
            "model-1",
            new GeminiV1BetaGenerateContentFunctionCalling(),
            List.of("sized"),
            fallbackTool
        );
        assertEquals(RENDERED, run(gemini, question()));
        assertTrue(predictParameters().get("tool_configs").contains("\"enum\":[\"5\",\"10\"]"));
        assertEquals(Map.of("size", 10L), renderedParams.get());
    }

    @Test
    public void featureDisabled_failsWithoutFallback() {
        when(featureSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        AtomicReference<Exception> failure = new AtomicReference<>();
        tool("product_search").run(question(), ActionListener.wrap(r -> {}, failure::set));
        assertTrue(failure.get() instanceof OpenSearchStatusException);
        verify(fallbackTool, never()).run(any(), any());
        verify(resolver, never()).getTemplates(any(), any(), any());
    }

    @Test
    public void missingQuestion_fails() {
        Map<String, String> params = question();
        params.remove("question");
        AtomicReference<Exception> failure = new AtomicReference<>();
        tool("product_search").run(params, ActionListener.wrap(r -> {}, failure::set));
        assertTrue(failure.get() instanceof IllegalArgumentException);
    }

    @Test
    public void factory_parsesTemplateIds() {
        assertEquals(List.of("a", "b"), SearchTemplateFillTool.Factory.parseTemplateIds("[\"a\", \"b\", \"a\"]"));
        assertEquals(List.of("a"), SearchTemplateFillTool.Factory.parseTemplateIds("a"));
        assertEquals(List.of("a"), SearchTemplateFillTool.Factory.parseTemplateIds(List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.parseTemplateIds(null));
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.parseTemplateIds("[]"));
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.parseTemplateIds("[not json"));
    }

    @Test
    public void factory_requiresForcedToolCapableInterface() {
        assertTrue(
            SearchTemplateFillTool.Factory.forcedToolFunctionCalling("bedrock/converse/claude") instanceof BedrockConverseFunctionCalling
        );
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.forcedToolFunctionCalling(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillTool.Factory.forcedToolFunctionCalling("bedrock/converse/deepseek_r1")
        );
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.forcedToolFunctionCalling("nope"));
    }

    @Test
    public void factory_createsToolWithFallback() {
        MLModelTool.Factory.getInstance().init(client);
        QueryPlanningTool.Factory.getInstance().init(client);
        SearchTemplateFillTool.Factory.getInstance().init(client, resolver, featureSetting);
        Map<String, Object> params = new HashMap<>();
        params.put("model_id", "model-1");
        params.put("_llm_interface", "openai/v1/chat/completions");
        params.put("template_ids", "[\"product_search\"]");

        SearchTemplateFillTool created = SearchTemplateFillTool.Factory.getInstance().create(params);
        assertEquals("model-1", created.getModelId());
        assertEquals(List.of("product_search"), created.getTemplateIds());
        assertEquals(QueryPlanningTool.LLM_GENERATED_TYPE_FIELD, created.getFallbackTool().getGenerationType());
        params.put("fallback_enabled", "false");
        assertNull(SearchTemplateFillTool.Factory.getInstance().create(params).getFallbackTool());
        assertThrows(IllegalArgumentException.class, () -> SearchTemplateFillTool.Factory.getInstance().create(new HashMap<>()));
    }
}
