/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_MAPPING_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUERY_FIELDS_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.SYSTEM_PROMPT_FIELD;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.transport.client.Client;

/**
 * Units test for QueryPlanningTools
 */
public class QueryPlanningToolTests {

    @Mock
    private Client client;

    @Mock
    private MLModelTool queryGenerationTool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private Map<String, String> validParams;
    private Map<String, String> emptyParams;

    private QueryPlanningTool.Factory factory;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MLModelTool.Factory.getInstance().init(client);

        // Mock the MLFeatureEnabledSetting to return true for agentic search
        when(mlFeatureEnabledSetting.isAgenticSearchEnabled()).thenReturn(true);

        // Initialize the factory with mocked dependencies
        factory = QueryPlanningTool.Factory.getInstance();
        factory.init(client, mlFeatureEnabledSetting);

        validParams = new HashMap<>();
        validParams.put(SYSTEM_PROMPT_FIELD, "test prompt");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testFactoryCreate() {
        Map<String, Object> map = Map.of(MODEL_ID_FIELD, "test_model_id");
        Tool tool = QueryPlanningTool.Factory.getInstance().create(map);
        assertNotNull(tool);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
    }

    @Test
    public void testRun() throws ExecutionException, InterruptedException {
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(matchQueryString);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.query_text}"
            );
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);

        assertEquals(matchQueryString, future.get());
    }

    @Test
    public void testRun_PredictionReturnsList_ThrowsIllegalArgumentException() throws ExecutionException, InterruptedException {
        thrown.expect(ExecutionException.class);
        thrown.expectCause(org.hamcrest.Matchers.isA(IllegalArgumentException.class));
        thrown.expectMessage("Error processing query string: [invalid_query]. Try using response_filter in agent registration if needed.");

        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(1);
            listener.onResponse(List.of("invalid_query"));
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);

        future.get();
    }

    @Test
    public void testRun_PredictionReturnsNull_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);
        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @Test
    public void testRun_PredictionReturnsEmpty_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);
        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @Test
    public void testRun_PredictionReturnsNullString_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("null");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);
        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @Test
    public void testValidate() {
        Tool tool = QueryPlanningTool.Factory.getInstance().create(Map.of("model_id", "test_model_id"));
        assertTrue(tool.validate(validParams));
        assertFalse(tool.validate(emptyParams));
        assertFalse(tool.validate(null));
    }

    @Test
    public void testToolGetters() {
        Tool tool = QueryPlanningTool.Factory.getInstance().create(Map.of("model_id", "test_model_id"));
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
        assertEquals(QueryPlanningTool.TYPE, tool.getType());
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
        assertNull(tool.getVersion());
    }

    @Test
    public void testFactoryGetAllModelKeys() {
        List<String> allModelKeys = QueryPlanningTool.Factory.getInstance().getAllModelKeys();
        assertEquals(List.of(MODEL_ID_FIELD), allModelKeys);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testRunWithNoPrompt() {
        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("query_text", "some query");
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        tool.run(parameters, listener);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        doAnswer(invocation -> {
            Map<String, String> params = invocation.getArgument(0);
            assertNotNull(params.get(SYSTEM_PROMPT_FIELD));
            return null;
        }).when(queryGenerationTool).run(captor.capture(), any());
    }

    @Test
    public void testRunWithInvalidParameters() {
        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        tool.run(Collections.emptyMap(), listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        org.mockito.Mockito.verify(listener).onFailure(captor.capture());
        assertEquals("Empty parameters for QueryPlanningTool: {}", captor.getValue().getMessage());
    }

    @Test
    public void testRunModelReturnsNull() {
        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("query_text", "some query");
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse(null);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(listener).onResponse(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    public void testSetName() {
        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        tool.setName("NewName");
        assertEquals("NewName", tool.getName());
    }

    @Test
    public void testFactoryCreateWithEmptyType() {
        Map<String, Object> map = new HashMap<>();
        map.put(QueryPlanningTool.MODEL_ID_FIELD, "modelId");
        Tool tool = factory.create(map);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
        assertEquals("llmGenerated", ((QueryPlanningTool) tool).getGenerationType());
        assertNotNull(tool);
    }

    @Test
    public void testFactoryCreateWithInvalidType() {
        Map<String, Object> map = new HashMap<>();
        map.put("generation_type", "invalid");
        map.put(QueryPlanningTool.MODEL_ID_FIELD, "modelId");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(map));
        assertEquals("Invalid generation type: invalid. The current supported types are llmGenerated.", exception.getMessage());
    }

    @Test
    public void testAllParameterProcessing() {
        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("query_text", "test query");
        parameters.put(INDEX_MAPPING_FIELD, "{\"properties\":{\"title\":{\"type\":\"text\"}}}");
        parameters.put(QUERY_FIELDS_FIELD, "[\"title\", \"content\"]");
        // No system_prompt - should use default

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool).run(captor.capture(), any());

        Map<String, String> capturedParams = captor.getValue();

        // All parameters should be processed
        assertTrue(capturedParams.containsKey("query_text"));
        assertTrue(capturedParams.containsKey(INDEX_MAPPING_FIELD));
        assertTrue(capturedParams.containsKey(QUERY_FIELDS_FIELD));
        assertTrue(capturedParams.containsKey(SYSTEM_PROMPT_FIELD));

        // Processed parameters should be JSON strings
        assertTrue(capturedParams.get(INDEX_MAPPING_FIELD).startsWith("\""));
        assertTrue(capturedParams.get(QUERY_FIELDS_FIELD).startsWith("\""));
    }

    @Test
    public void testFactoryCreateWhenAgenticSearchDisabled() {
        // Mock the MLFeatureEnabledSetting to return false for agentic search
        when(mlFeatureEnabledSetting.isAgenticSearchEnabled()).thenReturn(false);

        Map<String, Object> map = new HashMap<>();
        map.put(QueryPlanningTool.MODEL_ID_FIELD, "modelId");

        Exception exception = assertThrows(OpenSearchException.class, () -> factory.create(map));
        assertEquals(ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE, exception.getMessage());
    }
}
