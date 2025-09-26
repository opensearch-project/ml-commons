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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_MAPPING_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_NAME_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.LLM_GENERATED_TYPE_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUERY_FIELDS_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUESTION_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.SAMPLE_DOCUMENT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.SYSTEM_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.TEMPLATE_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.USER_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.USER_SEARCH_TEMPLATES_TYPE_FIELD;

import java.io.IOException;
import java.io.InputStream;
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
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.script.StoredScriptSource;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

/**
 * Units test for QueryPlanningTools
 */
@Log4j2
public class QueryPlanningToolTests {

    @Mock
    private Client client;

    @Mock
    private AdminClient adminClient;

    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Mock
    private ClusterAdminClient clusterAdminClient;

    @Mock
    private MLModelTool queryGenerationTool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private Map<String, String> validParams;
    private Map<String, String> emptyParams;

    private QueryPlanningTool.Factory factory;

    // Common test objects
    private ArgumentCaptor<ActionListener<GetIndexResponse>> actionListenerCaptor;
    private GetIndexResponse getIndexResponse;
    private MappingMetadata mapping;
    private String mockedSearchResponseString;

    @Before
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MLModelTool.Factory.getInstance().init(client);

        // Load the search response JSON file like SearchIndexToolTests does
        try (InputStream searchResponseIns = SearchIndexTool.class.getResourceAsStream("retrieval_tool_search_response.json")) {
            if (searchResponseIns != null) {
                mockedSearchResponseString = new String(searchResponseIns.readAllBytes());
            }
        }

        // Mock the client chain for async operations
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock the MLFeatureEnabledSetting to return true for agentic search
        when(mlFeatureEnabledSetting.isAgenticSearchEnabled()).thenReturn(true);

        // Initialize the factory with mocked dependencies
        factory = QueryPlanningTool.Factory.getInstance();
        factory.init(client, mlFeatureEnabledSetting);

        validParams = new HashMap<>();
        validParams.put(SYSTEM_PROMPT_FIELD, DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT);
        emptyParams = Collections.emptyMap();

    }

    @SneakyThrows
    public void mockSampleDoc() throws IOException {
        // Mock the search operation for getSampleDocAsync - use the same approach as SearchIndexToolTests
        SearchResponse mockedSearchResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, mockedSearchResponseString)
            );
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockedSearchResponse);
            return null;
        }).when(client).search(any(), any());
    }

    public void mockGetIndexMapping() {
        // Mock the getIndex operation for getIndexMappingAsync (following IndexMappingToolTests pattern)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<GetIndexResponse>> captor = ArgumentCaptor.forClass(ActionListener.class);
        this.actionListenerCaptor = captor;
        doNothing().when(indicesAdminClient).getIndex(any(), actionListenerCaptor.capture());

        // Create a real GetIndexResponse with real MappingMetadata
        this.getIndexResponse = mock(GetIndexResponse.class);
        when(getIndexResponse.indices()).thenReturn(new String[] { "testIndex" });

        // Create real MappingMetadata with actual source
        String mappingSource = "{\"properties\":{\"title\":{\"type\":\"text\"}}}";
        this.mapping = new MappingMetadata("testIndex", XContentHelper.convertToMap(JsonXContent.jsonXContent, mappingSource, true));
        when(getIndexResponse.mappings()).thenReturn(Map.of("testIndex", mapping));
    }

    @Test
    public void testFactoryCreate() {
        Map<String, Object> map = Map.of(MODEL_ID_FIELD, "test_model_id");
        Tool tool = QueryPlanningTool.Factory.getInstance().create(map);
        assertNotNull(tool);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
    }

    @SneakyThrows
    @Test
    public void testCreateWithInvalidSearchTemplatesDescription() throws IllegalArgumentException {
        mockSampleDoc();
        mockGetIndexMapping();
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        params.put("question", "help me find some books related to wind");
        params.put("search_templates", "[{'template_id': 'template_id', 'template_des': 'test_description'}]");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field entries must have a template_description", exception.getMessage());
    }

    @SneakyThrows
    @Test
    public void testCreateWithInvalidSearchTemplatesID() throws IllegalArgumentException {
        mockSampleDoc();
        mockGetIndexMapping();
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        params.put("question", "help me find some books related to wind");
        params.put("search_templates", "[{'templateid': 'template_id', 'template_description': 'test_description'}]");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field entries must have a template_id", exception.getMessage());
    }

    @SneakyThrows
    @Test
    public void testRun() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";

        // Mock the query generation tool
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(matchQueryString);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        validParams.put(QUESTION_FIELD, "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");

        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        // Wait for the test to complete
        String result = future.get();
        assertEquals(matchQueryString, result);
    }

    @SneakyThrows
    @Test
    public void testRunWithUserProvidedSearchTemplates() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";

        // Mock the cluster admin client for stored scripts
        doAnswer(invocation -> {
            ActionListener<GetStoredScriptResponse> actionListener = invocation.getArgument(1);
            GetStoredScriptResponse getStoredScriptResponse = mock(GetStoredScriptResponse.class);
            StoredScriptSource storedScriptSource = mock(StoredScriptSource.class);
            when(getStoredScriptResponse.getSource()).thenReturn(storedScriptSource);
            when(storedScriptSource.getSource()).thenReturn("test");
            actionListener.onResponse(getStoredScriptResponse);
            return null;
        }).when(clusterAdminClient).getStoredScript(any(), any());

        // Stub template selection and query planning responses
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("template_id");
            return null;
        }).doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(matchQueryString);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(USER_SEARCH_TEMPLATES_TYPE_FIELD, queryGenerationTool, client, null);

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        validParams.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        assertEquals(matchQueryString, future.get());
    }

    @SneakyThrows
    @Test
    public void testRunWithDefaultSearchTemplate() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";

        // Stub template selection and query planning responses
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(matchQueryString);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        assertEquals(matchQueryString, future.get());

        // ensure query generation tool is invoked only once
        verify(queryGenerationTool, times(1)).run(any(), any());
    }

    @SneakyThrows
    @Test
    public void testRimWithUserProvidedSearchTemplatesAndNoLMMTemplateChoice() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";

        // Use the common mock from setup method - no need to override

        // Stub template selection as blank and query planning responses
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("");
            return null;
        }).doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(matchQueryString);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(USER_SEARCH_TEMPLATES_TYPE_FIELD, queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                SYSTEM_PROMPT_FIELD,
                "You are a query generation agent. Generate a dsl query for the following question: ${parameters.question}"
            );
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        validParams.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        assertEquals(matchQueryString, future.get());
        // Ensure query generation tool is invoked twice, for template selection and query planning
        verify(queryGenerationTool, times(2)).run(any(), any());
    }

    @SneakyThrows
    @Test
    public void testRun_PredictionReturnsList_ThrowsIllegalArgumentException() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        thrown.expect(ExecutionException.class);
        thrown.expectCause(org.hamcrest.Matchers.isA(IllegalArgumentException.class));
        thrown
            .expectMessage("Error processing search template: [invalid_query]. Try using response_filter in agent registration if needed.");

        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(1);
            listener.onResponse(List.of("invalid_query"));
            return null;
        }).when(queryGenerationTool).run(any(), any());

        String searchTemplates = "[{'template_id': 'template1', 'template_description': 'test template'}]";
        QueryPlanningTool tool = new QueryPlanningTool(USER_SEARCH_TEMPLATES_TYPE_FIELD, queryGenerationTool, client, searchTemplates);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        future.get();
    }

    @SneakyThrows
    @Test
    public void testRun_PredictionReturnsNull_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @SneakyThrows
    @Test
    public void testRun_PredictionReturnsEmpty_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @SneakyThrows
    @Test
    public void testRun_PredictionReturnsNullString_ReturnDefaultQuery() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("null");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        String defaultQueryString = "{\"size\":10,\"query\":{\"match_all\":{}}}";
        assertEquals(defaultQueryString, future.get());
    }

    @Test
    public void testValidate() {
        Tool tool = QueryPlanningTool.Factory.getInstance().create(Map.of("model_id", "test_model_id"));

        // Add required parameters to validParams
        validParams.put(QUESTION_FIELD, "test question");
        validParams.put(INDEX_NAME_FIELD, "testIndex");

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

    @SneakyThrows
    @Test
    public void testRunWithNoPrompt() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "some query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool).run(captor.capture(), any());
        Map<String, String> capturedParams = captor.getValue();
        assertNotNull(capturedParams.get(SYSTEM_PROMPT_FIELD));
    }

    @SneakyThrows
    @Test
    public void testRunWithInvalidParameters() {
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        tool.run(Collections.emptyMap(), listener);

        // Don't trigger getIndex response for invalid parameters - validation should fail immediately
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        org.mockito.Mockito.verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("Required parameters not found"));
    }

    @SneakyThrows
    @Test
    public void testRunModelReturnsNull() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "some query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse(null);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(listener).onResponse(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    public void testSetName() {
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        tool.setName("NewName");
        assertEquals("NewName", tool.getName());
    }

    @SneakyThrows
    @Test
    public void testFactoryCreateWithEmptyType() {
        mockSampleDoc();
        mockGetIndexMapping();
        Map<String, Object> map = new HashMap<>();
        map.put(MODEL_ID_FIELD, "modelId");
        Tool tool = factory.create(map);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
        assertEquals(LLM_GENERATED_TYPE_FIELD, ((QueryPlanningTool) tool).getGenerationType());
        assertNotNull(tool);
    }

    @Test
    public void testFactoryCreateWithInvalidType() {
        Map<String, Object> map = new HashMap<>();
        map.put("generation_type", "invalid");
        map.put(MODEL_ID_FIELD, "modelId");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(map));
        assertEquals(
            "Invalid generation type: invalid. The current supported types are llmGenerated and user_templates.",
            exception.getMessage()
        );
    }

    @SneakyThrows
    @Test
    public void testAllParameterProcessing() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
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

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool).run(captor.capture(), any());

        Map<String, String> capturedParams = captor.getValue();

        // All parameters should be processed
        assertTrue(capturedParams.containsKey("question"));
        assertTrue(capturedParams.containsKey(INDEX_MAPPING_FIELD));
        assertTrue(capturedParams.containsKey(QUERY_FIELDS_FIELD));
        assertTrue(capturedParams.containsKey(SYSTEM_PROMPT_FIELD));
        assertTrue(capturedParams.containsKey(USER_PROMPT_FIELD));

        // LLM Generated type should have template param for default search template
        assertTrue(capturedParams.containsKey(TEMPLATE_FIELD));

        // Processed parameters should be JSON strings
        assertTrue(capturedParams.get(INDEX_MAPPING_FIELD).startsWith("\""));
        assertTrue(capturedParams.get(QUERY_FIELDS_FIELD).startsWith("\""));
    }

    @SneakyThrows
    @Test
    public void testAllParameterProcessing_WithUserSearchTemplates() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put(INDEX_MAPPING_FIELD, "{\"properties\":{\"title\":{\"type\":\"text\"}}}");
        parameters.put(QUERY_FIELDS_FIELD, "[\"title\", \"content\"]");
        parameters.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        // No system_prompt - should use default

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        // Use the common mocks from setup method - no need to override

        // Stub query planning response (LLM_GENERATED_TYPE_FIELD skips template selection)
        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool, times(1)).run(captor.capture(), any());

        Map<String, String> capturedParams = captor.getValue();

        // All parameters should be processed
        assertTrue(capturedParams.containsKey("question"));
        assertTrue(capturedParams.containsKey(INDEX_MAPPING_FIELD));
        assertTrue(capturedParams.containsKey(QUERY_FIELDS_FIELD));
        assertTrue(capturedParams.containsKey(SYSTEM_PROMPT_FIELD));
        assertTrue(capturedParams.containsKey(USER_PROMPT_FIELD));
        assertTrue(capturedParams.containsKey(TEMPLATE_FIELD));

        // Processed parameters should be JSON strings
        assertTrue(capturedParams.get(INDEX_MAPPING_FIELD).startsWith("\""));
        assertTrue(capturedParams.get(QUERY_FIELDS_FIELD).startsWith("\""));
    }

    @SneakyThrows
    @Test
    public void testUserPromptParameterProcessing() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put(USER_PROMPT_FIELD, "custom user prompt");
        // No system_prompt or user_prompt - should use defaults

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        tool.run(parameters, listener);

        // Manually trigger the getIndex response to prevent hanging
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool).run(captor.capture(), any());

        Map<String, String> capturedParams = captor.getValue();

        // User prompt should be processed
        assertTrue(capturedParams.containsKey(USER_PROMPT_FIELD));
        assertEquals("custom user prompt", capturedParams.get(USER_PROMPT_FIELD));
    }

    @Test
    public void testFactoryCreateWhenAgenticSearchDisabled() {
        // Mock the MLFeatureEnabledSetting to return false for agentic search
        when(mlFeatureEnabledSetting.isAgenticSearchEnabled()).thenReturn(false);

        Map<String, Object> map = new HashMap<>();
        map.put(MODEL_ID_FIELD, "modelId");

        Exception exception = assertThrows(OpenSearchException.class, () -> factory.create(map));
        assertEquals(ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE, exception.getMessage());
    }

    @Test
    public void testCreateWithValidSearchTemplates() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params
            .put(
                "search_templates",
                "[{'template_id': 'template1', 'template_description': 'description1'}, {'template_id': 'template2', 'template_description': 'description2'}]"
            );

        QueryPlanningTool tool = factory.create(params);
        assertNotNull(tool);
        assertEquals(USER_SEARCH_TEMPLATES_TYPE_FIELD, tool.getGenerationType());
    }

    @Test
    public void testCreateWithEmptySearchTemplatesList() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params.put("search_templates", "[]");

        QueryPlanningTool tool = factory.create(params);
        assertNotNull(tool);
        assertEquals(USER_SEARCH_TEMPLATES_TYPE_FIELD, tool.getGenerationType());
    }

    @Test
    public void testCreateWithMissingSearchTemplatesField() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field is required when generation_type is 'user_templates'", exception.getMessage());
    }

    @Test
    public void testCreateWithInvalidSearchTemplatesJson() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params.put("search_templates", "invalid_json");

        assertThrows(com.google.gson.JsonSyntaxException.class, () -> factory.create(params));
    }

    @Test
    public void testCreateWithNullTemplateId() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params.put("search_templates", "[{'template_id': null, 'template_description': 'description'}]");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field entries must have a template_id", exception.getMessage());
    }

    @Test
    public void testCreateWithBlankTemplateDescription() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params.put("search_templates", "[{'template_id': 'template1', 'template_description': '   '}]");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field entries must have a template_description", exception.getMessage());
    }

    @Test
    public void testCreateWithMixedValidAndInvalidTemplates() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params
            .put(
                "search_templates",
                "[{'template_id': 'template1', 'template_description': 'description1'}, {'template_description': 'description2'}]"
            );

        Exception exception = assertThrows(IllegalArgumentException.class, () -> factory.create(params));
        assertEquals("search_templates field entries must have a template_id", exception.getMessage());
    }

    @Test
    public void testCreateWithExtraFieldsInSearchTemplates() {
        Map<String, Object> params = new HashMap<>();
        params.put("generation_type", USER_SEARCH_TEMPLATES_TYPE_FIELD);
        params.put(MODEL_ID_FIELD, "test_model_id");
        params.put("search_templates", "[{'template_id': 'template1', 'template_description': 'description1', 'extra_field': 'value'}]");

        QueryPlanningTool tool = factory.create(params);
        assertNotNull(tool);
        assertEquals(USER_SEARCH_TEMPLATES_TYPE_FIELD, tool.getGenerationType());
    }

    @Test
    public void testRunWithNonExistentIndex() throws InterruptedException {

        doAnswer(invocation -> {
            org.opensearch.core.action.ActionListener<org.opensearch.action.admin.indices.get.GetIndexResponse> listener = invocation
                .getArgument(1);
            listener.onFailure(new org.opensearch.index.IndexNotFoundException("non_existent_index"));
            return null;
        }).when(indicesAdminClient).getIndex(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "help me find some books related to wind");
        parameters.put("index_name", "non_existent_index");

        tool.run(parameters, listener);

        // Should fail with IllegalArgumentException due to non-existent index
        assertTrue(future.isCompletedExceptionally());
        try {
            future.get();
            fail("Expected ExecutionException to be thrown");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertTrue(e.getCause().getMessage().contains("Index does not exist or is not available"));
        }
    }

    @Test
    @SneakyThrows
    public void testGetSampleDocTruncation() throws ExecutionException, InterruptedException {
        mockGetIndexMapping();
        // Create a search response with a very large document that should be truncated
        String largeContent = "This is a very long document content that exceeds the maximum truncation limit of 250 characters. "
            + "It contains multiple sentences and should be truncated when processed by the getSampleDocAsync method. "
            + "The truncation should add the prefix trucates' to indicate that the content has been shortened. "
            + "This test verifies that the truncation logic works correctly and prevents extremely large documents "
            + "from being included in the query planning process. The content should be cut off at exactly 250 characters "
            + "and prefixed with the truncation marker to maintain readability while keeping the response size manageable.";

        String largeSearchResponseJson = "{\n"
            + "  \"took\": 1,\n"
            + "  \"timed_out\": false,\n"
            + "  \"_shards\": {\n"
            + "    \"total\": 1,\n"
            + "    \"successful\": 1,\n"
            + "    \"skipped\": 0,\n"
            + "    \"failed\": 0\n"
            + "  },\n"
            + "  \"hits\": {\n"
            + "    \"total\": {\n"
            + "      \"value\": 1,\n"
            + "      \"relation\": \"eq\"\n"
            + "    },\n"
            + "    \"max_score\": 1.0,\n"
            + "    \"hits\": [\n"
            + "      {\n"
            + "        \"_index\": \"testIndex\",\n"
            + "        \"_id\": \"1\",\n"
            + "        \"_score\": 1.0,\n"
            + "        \"_source\": {\n"
            + "          \"title\": \"Sample Document\",\n"
            + "          \"content\": \""
            + largeContent
            + "\",\n"
            + "          \"short_field\": \"short\"\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";

        // Override the search response for this test only
        SearchResponse largeSearchResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, largeSearchResponseJson)
            );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(largeSearchResponse);
            return null;
        }).when(client).search(any(), any());

        // Mock queryGenerationTool.run() to capture parameters and return success
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            // Return success immediately to complete the test
            modelListener.onResponse("test query");
            return null;
        }).when(queryGenerationTool).run(paramsCaptor.capture(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null);
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");

        tool.run(parameters, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        // Wait for completion
        String result = future.get();

        // Verify the final result (should be the LLM response)
        assertEquals("test query", result);

        // Verify the captured parameters contain truncated content
        Map<String, String> capturedParams = paramsCaptor.getValue();
        String sampleDoc = capturedParams.get(SAMPLE_DOCUMENT_FIELD);

        assertNotNull("Sample document should not be null", sampleDoc);
        assertTrue("Sample document should contain truncated content", sampleDoc.contains("[truncated]"));
        assertTrue(
            "Sample document should contain short field without truncation",
            sampleDoc.contains("\\\"short_field\\\":\\\"short\\\"")      // double-encoded form
        );
    }

}
