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
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.AGENT_LLM_MODEL_ID;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_MAPPING_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_NAME_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.LLM_GENERATED_TYPE_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUERY_FIELDS_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUERY_PLANNER_SYSTEM_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUERY_PLANNER_USER_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUESTION_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.SAMPLE_DOCUMENT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.TEMPLATE_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.TEMPLATE_SELECTION_SYSTEM_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.TEMPLATE_SELECTION_USER_PROMPT_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.USER_SEARCH_TEMPLATES_TYPE_FIELD;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.engine.tools.parser.ToolParser;
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
@SuppressWarnings("unchecked")
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

        // Mock the model validation to return a valid REMOTE model by default
        mockRemoteModelValidation();

        // Initialize the factory with mocked dependencies
        factory = QueryPlanningTool.Factory.getInstance();
        factory.init(client);

        validParams = new HashMap<>();
        validParams.put(QUERY_PLANNER_SYSTEM_PROMPT_FIELD, DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT);
        emptyParams = Collections.emptyMap();

    }

    /**
     * Mocks the model validation to return a valid REMOTE model.
     * This is needed because QueryPlanningTool.run() validates the model is REMOTE type.
     */
    public void mockRemoteModelValidation() {
        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);

        MLModelGetResponse mockResponse = mock(MLModelGetResponse.class);
        when(mockResponse.getMlModel()).thenReturn(mockModel);

        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(MLModelGetRequest.class), any());
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
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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

        QueryPlanningTool tool = new QueryPlanningTool(
            USER_SEARCH_TEMPLATES_TYPE_FIELD,
            queryGenerationTool,
            client,
            null,
            "test_model_id"
        );

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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
    public void testRunWithUserProvidedSearchTemplatesAndNoLMMTemplateChoice() throws ExecutionException, InterruptedException {
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

        QueryPlanningTool tool = new QueryPlanningTool(
            USER_SEARCH_TEMPLATES_TYPE_FIELD,
            queryGenerationTool,
            client,
            null,
            "test_model_id"
        );
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put(
                QUERY_PLANNER_SYSTEM_PROMPT_FIELD,
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

        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(1);
            listener.onResponse(List.of("invalid_query"));
            return null;
        }).when(queryGenerationTool).run(any(), any());

        String searchTemplates = "[{'template_id': 'template1', 'template_description': 'test template'}]";
        QueryPlanningTool tool = new QueryPlanningTool(
            USER_SEARCH_TEMPLATES_TYPE_FIELD,
            queryGenerationTool,
            client,
            searchTemplates,
            "test_model_id"
        );
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        validParams.put("question", "help me find some books related to wind");
        validParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(validParams, listener);

        // Should throw ExecutionException with IllegalArgumentException cause
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(
            exception
                .getCause()
                .getMessage()
                .contains("Error processing search template: [invalid_query]. Try using response_filter in agent registration if needed.")
        );
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

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
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

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
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

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
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

    @SneakyThrows
    @Test
    public void testRunWithNoPrompt() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "some query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
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
        assertEquals(DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT, capturedParams.get("system_prompt"));
    }

    @SneakyThrows
    @Test
    public void testStripAgentContextParameters() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();

        // Capture parameters sent to the model
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(paramsCaptor.capture(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> parameters = new HashMap<>();
        parameters.put(QUESTION_FIELD, "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        // Excluded keys that should be removed
        parameters.put("_chat_history", "should be removed");
        parameters.put("_tools", "should be removed");
        parameters.put("_interactions", "should be removed");
        parameters.put("tool_configs", "should be removed");
        // Nulls should be filtered out
        parameters.put("some_extra_field", null);

        tool.run(parameters, listener);

        // Trigger async chain
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        // Ensure run completed
        future.get();

        Map<String, String> capturedParams = paramsCaptor.getValue();
        // Excluded parameters
        assertFalse(capturedParams.containsKey("_chat_history"));
        assertFalse(capturedParams.containsKey("_tools"));
        assertFalse(capturedParams.containsKey("_interactions"));
        assertFalse(capturedParams.containsKey("tool_configs"));
        // Nulls
        assertFalse(capturedParams.containsKey("some_extra_field"));
        // Required parameters remain
        assertEquals("test query", capturedParams.get(QUESTION_FIELD));
        assertEquals("testIndex", capturedParams.get(INDEX_NAME_FIELD));
    }

    @SneakyThrows
    @Test
    public void testRunWithInvalidParameters() {
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        ActionListener<String> listener = mock(ActionListener.class);

        tool.run(Collections.emptyMap(), listener);

        // Don't trigger getIndex response for invalid parameters - validation should fail immediately
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        org.mockito.Mockito.verify(listener).onFailure(captor.capture());
        assertTrue(
            captor.getValue().getMessage().contains("Validation error: missing or empty required parameters — index_name, question.")
        );
    }

    @SneakyThrows
    @Test
    public void testRunModelReturnsNull() {
        // Mock the async calls
        mockSampleDoc();
        mockGetIndexMapping();
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "some query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
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
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
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
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put(INDEX_MAPPING_FIELD, "{\"properties\":{\"title\":{\"type\":\"text\"}}}");
        parameters.put(QUERY_FIELDS_FIELD, "[\"title\", \"content\"]");
        // No system_prompt - should use default

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
        assertTrue(capturedParams.containsKey("system_prompt"));
        assertTrue(capturedParams.containsKey("user_prompt"));

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
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put(INDEX_MAPPING_FIELD, "{\"properties\":{\"title\":{\"type\":\"text\"}}}");
        parameters.put(QUERY_FIELDS_FIELD, "[\"title\", \"content\"]");
        parameters.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        // No system_prompt - should use default

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
        assertTrue(capturedParams.containsKey("system_prompt"));
        assertTrue(capturedParams.containsKey("user_prompt"));
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
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test query");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put(QUERY_PLANNER_USER_PROMPT_FIELD, "custom user prompt");
        // No system_prompt or user_prompt - should use defaults

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
        assertTrue(capturedParams.containsKey("user_prompt"));
        assertEquals("custom user prompt", capturedParams.get("user_prompt"));
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

        QueryPlanningTool tool = new QueryPlanningTool("llmGenerated", queryGenerationTool, client, null, "test_model_id");
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
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            // Return success immediately to complete the test
            modelListener.onResponse("test query");
            return null;
        }).when(queryGenerationTool).run(paramsCaptor.capture(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");
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

    @SneakyThrows
    @Test
    public void testGetSampleDoc_ErrorPaths() throws ExecutionException, InterruptedException {
        // Success for index mapping
        mockGetIndexMapping();

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        // Case 1: onResponse throws inside try (null SearchResponse)
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(null); // triggers NPE inside try -> caught -> IOException("Failed to process sample document")
            return null;
        }).when(client).search(any(), any());

        CompletableFuture<String> f1 = new CompletableFuture<>();
        ActionListener<String> l1 = ActionListener.wrap(f1::complete, f1::completeExceptionally);
        Map<String, String> p1 = new HashMap<>();
        p1.put(QUESTION_FIELD, "q");
        p1.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p1, l1);
        actionListenerCaptor.getValue().onResponse(getIndexResponse);
        ExecutionException ex1 = assertThrows(ExecutionException.class, () -> f1.get());
        assertTrue(ex1.getCause() instanceof IOException);
        assertTrue(ex1.getCause().getMessage().contains("Failed to process sample document"));

        // Case 2: onFailure path
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("boom"));
            return null;
        }).when(client).search(any(), any());

        CompletableFuture<String> f2 = new CompletableFuture<>();
        ActionListener<String> l2 = ActionListener.wrap(f2::complete, f2::completeExceptionally);
        Map<String, String> p2 = new HashMap<>();
        p2.put(QUESTION_FIELD, "q");
        p2.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p2, l2);
        actionListenerCaptor.getValue().onResponse(getIndexResponse);
        ExecutionException ex2 = assertThrows(ExecutionException.class, () -> f2.get());
        assertTrue(ex2.getCause() instanceof IOException);
        assertTrue(ex2.getCause().getMessage().contains("Failed to get sample document"));

        // Case 3: outer catch (search throws synchronously)
        doAnswer(invocation -> { throw new RuntimeException("sync"); }).when(client).search(any(), any());

        CompletableFuture<String> f3 = new CompletableFuture<>();
        ActionListener<String> l3 = ActionListener.wrap(f3::complete, f3::completeExceptionally);
        Map<String, String> p3 = new HashMap<>();
        p3.put(QUESTION_FIELD, "q");
        p3.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p3, l3);
        actionListenerCaptor.getValue().onResponse(getIndexResponse);
        ExecutionException ex3 = assertThrows(ExecutionException.class, () -> f3.get());
        assertTrue(ex3.getCause() instanceof IOException);
        assertTrue(ex3.getCause().getMessage().contains("Failed to get sample document"));
    }

    @SneakyThrows
    @Test
    public void testGetIndexMapping_ErrorPaths() throws ExecutionException, InterruptedException {
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        // Common model stub to avoid NPE later if it gets that far
        doAnswer(invocation -> {
            ActionListener<String> ml = invocation.getArgument(1);
            ml.onResponse("{}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        // Case 1: onResponse mapping null -> NPE inside try caught
        ArgumentCaptor<ActionListener<GetIndexResponse>> captor1 = ArgumentCaptor.forClass(ActionListener.class);
        doAnswer(invocation -> {
            // capture and call onResponse with a response missing mapping
            ActionListener<GetIndexResponse> al = invocation.getArgument(1);
            if (actionListenerCaptor == null) {
                actionListenerCaptor = captor1;
            }
            al.onResponse(getIndexResponse); // getIndexResponse will be mocked below
            return null;
        }).when(indicesAdminClient).getIndex(any(), any());

        // Mock getIndexResponse to return no mapping for index
        getIndexResponse = mock(GetIndexResponse.class);
        when(getIndexResponse.mappings()).thenReturn(Collections.emptyMap());

        CompletableFuture<String> f1 = new CompletableFuture<>();
        ActionListener<String> l1 = ActionListener.wrap(f1::complete, f1::completeExceptionally);
        Map<String, String> p1 = new HashMap<>();
        p1.put(QUESTION_FIELD, "q");
        p1.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p1, l1);
        ExecutionException ix1 = assertThrows(ExecutionException.class, () -> f1.get());
        assertTrue(ix1.getCause() instanceof IllegalStateException);
        assertTrue(ix1.getCause().getMessage().contains("Failed to extract index mapping"));

        // Case 2: onFailure path with generic exception (non IndexNotFoundException)
        doAnswer(invocation -> {
            ActionListener<GetIndexResponse> al = invocation.getArgument(1);
            al.onFailure(new RuntimeException("boom"));
            return null;
        }).when(indicesAdminClient).getIndex(any(), any());

        CompletableFuture<String> f2 = new CompletableFuture<>();
        ActionListener<String> l2 = ActionListener.wrap(f2::complete, f2::completeExceptionally);
        Map<String, String> p2 = new HashMap<>();
        p2.put(QUESTION_FIELD, "q");
        p2.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p2, l2);
        ExecutionException ix2 = assertThrows(ExecutionException.class, () -> f2.get());
        assertTrue(ix2.getCause() instanceof IllegalStateException);
        assertTrue(ix2.getCause().getMessage().contains("Failed to extract index mapping"));

        // Case 3: outer try-catch (getIndex throws synchronously)
        doAnswer(invocation -> { throw new RuntimeException("sync"); }).when(indicesAdminClient).getIndex(any(), any());

        CompletableFuture<String> f3 = new CompletableFuture<>();
        ActionListener<String> l3 = ActionListener.wrap(f3::complete, f3::completeExceptionally);
        Map<String, String> p3 = new HashMap<>();
        p3.put(QUESTION_FIELD, "q");
        p3.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(p3, l3);
        ExecutionException ix3 = assertThrows(ExecutionException.class, () -> f3.get());
        assertTrue(ix3.getCause() instanceof IllegalStateException);
        assertTrue(ix3.getCause().getMessage().contains("Failed to extract index mapping"));
    }

    @SneakyThrows
    @Test
    public void testGetSampleDoc_NullCases_SetJsonNullInParameters() throws ExecutionException, InterruptedException {
        mockGetIndexMapping();

        String noHitsResponseJson = "{\n"
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
            + "      \"value\": 0,\n"
            + "      \"relation\": \"eq\"\n"
            + "    },\n"
            + "    \"max_score\": null,\n"
            + "    \"hits\": []\n"
            + "  }\n"
            + "}";

        String emptySourceResponseJson = "{\n"
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
            + "        \"_source\": {}\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";

        SearchResponse noHitsResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, noHitsResponseJson)
            );

        SearchResponse emptySourceResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, emptySourceResponseJson)
            );

        // Respond with noHits first, then emptySource
        final SearchResponse[] responses = new SearchResponse[] { noHitsResponse, emptySourceResponse };
        final int[] idx = { 0 };
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(responses[idx[0]]);
            idx[0] = Math.min(idx[0] + 1, responses.length - 1);
            return null;
        }).when(client).search(any(), any());

        // Capture parameters passed to the model
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        doAnswer(invocation -> {
            ActionListener<String> modelListener = invocation.getArgument(1);
            modelListener.onResponse("ok");
            return null;
        }).when(queryGenerationTool).run(paramsCaptor.capture(), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        // Run 1: no hits
        final CompletableFuture<String> future1 = new CompletableFuture<>();
        ActionListener<String> listener1 = ActionListener.wrap(future1::complete, future1::completeExceptionally);
        Map<String, String> params1 = new HashMap<>();
        params1.put(QUESTION_FIELD, "test query");
        params1.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(params1, listener1);
        actionListenerCaptor.getAllValues().get(0).onResponse(getIndexResponse);
        future1.get();

        // Run 2: empty source map
        final CompletableFuture<String> future2 = new CompletableFuture<>();
        ActionListener<String> listener2 = ActionListener.wrap(future2::complete, future2::completeExceptionally);
        Map<String, String> params2 = new HashMap<>();
        params2.put(QUESTION_FIELD, "test query");
        params2.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(params2, listener2);
        actionListenerCaptor.getAllValues().get(1).onResponse(getIndexResponse);
        future2.get();

        // We should have two model invocations captured
        assertEquals(2, paramsCaptor.getAllValues().size());
        Map<String, String> captured1 = paramsCaptor.getAllValues().get(0);
        Map<String, String> captured2 = paramsCaptor.getAllValues().get(1);

        // SAMPLE_DOCUMENT_FIELD should be JSON null literal (gson.toJson(null) -> "null")
        assertEquals("null", captured1.get(SAMPLE_DOCUMENT_FIELD));
        assertEquals("null", captured2.get(SAMPLE_DOCUMENT_FIELD));
    }

    @SneakyThrows
    @Test
    public void testTemplateSelectionCustomPrompts() throws ExecutionException, InterruptedException {
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

        QueryPlanningTool tool = new QueryPlanningTool(
            USER_SEARCH_TEMPLATES_TYPE_FIELD,
            queryGenerationTool,
            client,
            null,
            "test_model_id"
        );

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "help me find some books related to wind");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        parameters.put(TEMPLATE_SELECTION_SYSTEM_PROMPT_FIELD, "Custom template selection system prompt");
        parameters.put(TEMPLATE_SELECTION_USER_PROMPT_FIELD, "Custom template selection user prompt");

        tool.run(parameters, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        assertEquals(matchQueryString, future.get());

        // Verify that the custom template selection prompts were used
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool, times(2)).run(captor.capture(), any());

        // First call should be template selection with custom prompts
        Map<String, String> firstCallParams = captor.getAllValues().get(0);
        assertEquals("Custom template selection system prompt", firstCallParams.get("system_prompt"));
        assertEquals("Custom template selection user prompt", firstCallParams.get("user_prompt"));
    }

    @SneakyThrows
    @Test
    public void testTemplateSelectionPromptsWithDefaults() throws ExecutionException, InterruptedException {
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

        QueryPlanningTool tool = new QueryPlanningTool(
            USER_SEARCH_TEMPLATES_TYPE_FIELD,
            queryGenerationTool,
            client,
            null,
            "test_model_id"
        );

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "help me find some books related to wind");
        parameters.put(INDEX_NAME_FIELD, "testIndex");
        parameters.put("search_templates", "[{'template_id': 'template_id', 'template_description': 'test_description'}]");
        // No custom template selection prompts - should use defaults

        tool.run(parameters, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        assertEquals(matchQueryString, future.get());

        // Verify that the default template selection prompts were used
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(queryGenerationTool, times(2)).run(captor.capture(), any());

        // First call should be template selection with default prompts
        Map<String, String> firstCallParams = captor.getAllValues().get(0);
        assertNotNull(firstCallParams.get("system_prompt"));
        assertNotNull(firstCallParams.get("user_prompt"));
        // Should contain the default template selection prompts - check for more specific content
        assertTrue(firstCallParams.get("system_prompt").contains("template"));
        assertTrue(firstCallParams.get("user_prompt").contains("INPUTS"));
    }

    // Test 1: Create tool from factory, get parser, test parser behavior directly
    @SneakyThrows
    @Test
    public void testFactoryCreatedTool_DefaultExtractJsonParser() {
        // Create tool using factory and verify the output parser is correctly configured
        Map<String, Object> params = Map.of(MODEL_ID_FIELD, "test_model_id");
        QueryPlanningTool tool = QueryPlanningTool.Factory.getInstance().create(params);

        // Verify the output parser was created
        assertNotNull("Output parser should be created by factory", tool.getOutputParser());

        // Test the parser directly with different inputs
        Parser outputParser = tool.getOutputParser();

        // Test case 1: Extract JSON object from text
        Object parsedResult1 = outputParser.parse("Here is your query: {\"query\":{\"match\":{\"title\":\"test\"}}}");
        String resultWithText = parsedResult1 instanceof String ? (String) parsedResult1 : gson.toJson(parsedResult1);
        assertEquals("{\"query\":{\"match\":{\"title\":\"test\"}}}", resultWithText);

        // Test case 2: Extract pure JSON
        Object parsedResult2 = outputParser.parse("{\"query\":{\"match\":{\"title\":\"test\"}}}");
        String resultPureJson = parsedResult2 instanceof String ? (String) parsedResult2 : gson.toJson(parsedResult2);
        assertEquals("{\"query\":{\"match\":{\"title\":\"test\"}}}", resultPureJson);

        // Test case 3: No valid JSON - should return default template
        Object parsedResult3 = outputParser.parse("No JSON here at all");
        String resultNoJson = parsedResult3 instanceof String ? (String) parsedResult3 : gson.toJson(parsedResult3);
        assertEquals(DEFAULT_QUERY, resultNoJson);
    }

    // Test 2: Create tool from factory with custom processors, verify both default and custom processors work
    @SneakyThrows
    @Test
    public void testFactoryCreatedTool_WithCustomProcessors() {
        // Create tool using factory with custom output_processors (set_field)
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ID_FIELD, "test_model_id");

        // Add custom processor configuration
        List<Map<String, Object>> outputProcessors = new ArrayList<>();
        Map<String, Object> setFieldConfig = new HashMap<>();
        setFieldConfig.put("type", "set_field");
        setFieldConfig.put("path", "$.metadata");
        setFieldConfig.put("value", Map.of("source", "query_planner_tool"));
        outputProcessors.add(setFieldConfig);
        params.put("output_processors", outputProcessors);

        QueryPlanningTool tool = QueryPlanningTool.Factory.getInstance().create(params);

        // Verify the output parser was created
        assertNotNull("Output parser should be created by factory", tool.getOutputParser());

        // Test the parser - it should use BOTH default extract_json AND custom set_field processors
        Parser outputParser = tool.getOutputParser();

        // Test: Extract JSON from text (default extract_json) + add metadata field (custom set_field)
        String inputWithText = "Here is your query: {\"query\":{\"match\":{\"title\":\"test\"}}}";
        Object parsedResult = outputParser.parse(inputWithText);
        String result = parsedResult instanceof String ? (String) parsedResult : gson.toJson(parsedResult);

        // Verify both processors worked: extract_json extracted JSON, set_field added metadata
        String expectedResult = "{\"query\":{\"match\":{\"title\":\"test\"}},\"metadata\":{\"source\":\"query_planner_tool\"}}";
        assertEquals("Parser should extract JSON and add metadata field", expectedResult, result);
    }

    // Test 3: Create tool with mocked queryGenerationTool, manually set extract_json processor, run end-to-end
    @SneakyThrows
    @Test
    public void testQueryPlanningTool_WithMockedMLModelTool_EndToEnd() {
        mockSampleDoc();
        mockGetIndexMapping();

        // Mock the queryGenerationTool (MLModelTool) to return JSON embedded in text
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("Here is your query: {\"query\":{\"match\":{\"title\":\"test\"}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        // Create tool using constructor with the mocked queryGenerationTool
        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "test_model_id");

        // Create extract_json processor config (same as in factory)
        Map<String, Object> extractJsonConfig = new HashMap<>();
        extractJsonConfig.put("type", "extract_json");
        extractJsonConfig.put("extract_type", "object");
        extractJsonConfig.put("default", DEFAULT_QUERY);

        // Set the parser on the tool
        tool.setOutputParser(ToolParser.createProcessingParser(null, List.of(extractJsonConfig)));

        // Run the tool end-to-end - the output parser will return a Map, not String
        CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> runParams = new HashMap<>();
        runParams.put(QUESTION_FIELD, "test query");
        runParams.put(INDEX_NAME_FIELD, "testIndex");
        tool.run(runParams, listener);

        // Trigger the async index mapping response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        // Verify the JSON was extracted correctly by the parser
        Object resultObj = future.get();
        String result = resultObj instanceof String ? (String) resultObj : gson.toJson(resultObj);
        assertEquals("{\"query\":{\"match\":{\"title\":\"test\"}}}", result);
    }

    @Test
    public void testFactoryCreate_UsesAgentLlmModelIdAsFallback() {
        Map<String, Object> params = new HashMap<>();
        params.put(AGENT_LLM_MODEL_ID, "agent_model_123");
        // No model_id specified - should use agent_llm_model_id

        Tool tool = QueryPlanningTool.Factory.getInstance().create(params);
        assertNotNull(tool);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());

        assertTrue(params.containsKey(MODEL_ID_FIELD));
        assertEquals("agent_model_123", params.get(MODEL_ID_FIELD));
    }

    @Test
    public void testFactoryCreate_ToolModelIdTakesPrecedence() {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ID_FIELD, "tool_model_456");
        params.put(AGENT_LLM_MODEL_ID, "agent_model_123");

        Tool tool = QueryPlanningTool.Factory.getInstance().create(params);
        assertNotNull(tool);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());

        assertEquals("tool_model_456", params.get(MODEL_ID_FIELD));
    }

    @Test
    public void testFactoryCreate_NoModelIdProvided() {
        Map<String, Object> params = new HashMap<>();

        Exception exception = assertThrows(
            IllegalArgumentException.class,
            () -> { QueryPlanningTool.Factory.getInstance().create(params); }
        );
        assertEquals("Model ID can't be null or empty", exception.getMessage());
    }

    @SneakyThrows
    @Test
    public void testRun_RemoteModel_ValidationSucceeds() throws ExecutionException, InterruptedException {
        mockSampleDoc();
        mockGetIndexMapping();

        // Mock a REMOTE model - validation should succeed
        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);

        MLModelGetResponse mockResponse = mock(MLModelGetResponse.class);
        when(mockResponse.getMlModel()).thenReturn(mockModel);

        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(MLModelGetRequest.class), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "remote_model_id");

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("{\"query\":{\"match_all\":{}}}");
            return null;
        }).when(queryGenerationTool).run(any(), any());

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION_FIELD, "test question");
        params.put(INDEX_NAME_FIELD, "testIndex");

        tool.run(params, listener);

        // Manually trigger the getIndex response
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        // Should succeed - no exception thrown
        String result = future.get();
        assertNotNull(result);
    }

    @SneakyThrows
    @Test
    public void testRun_NonRemoteModel_ValidationFails() throws ExecutionException, InterruptedException {
        // Mock a non-REMOTE model (e.g., TEXT_EMBEDDING) - validation should fail
        MLModel mockModel = mock(MLModel.class);
        when(mockModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);

        MLModelGetResponse mockResponse = mock(MLModelGetResponse.class);
        when(mockResponse.getMlModel()).thenReturn(mockModel);

        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(any(), any(MLModelGetRequest.class), any());

        QueryPlanningTool tool = new QueryPlanningTool(LLM_GENERATED_TYPE_FIELD, queryGenerationTool, client, null, "non_remote_model_id");

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION_FIELD, "test question");
        params.put(INDEX_NAME_FIELD, "testIndex");

        tool.run(params, listener);

        // Should fail with validation error
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("remote LLM model"));
    }
}
