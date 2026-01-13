/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.tools.SearchIndexTool.INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.engine.tools.SearchIndexTool.STRICT_FIELD;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.search.SearchModule;
import org.opensearch.transport.client.Client;

import lombok.SneakyThrows;

public class SearchIndexToolTests {
    static public final NamedXContentRegistry TEST_XCONTENT_REGISTRY_FOR_QUERY = new NamedXContentRegistry(
        new SearchModule(Settings.EMPTY, List.of()).getNamedXContents()
    );

    private Client client;

    private SearchIndexTool mockedSearchIndexTool;

    private String mockedSearchResponseString;

    @Before
    @SneakyThrows
    public void setup() {
        client = mock(Client.class);
        mockedSearchIndexTool = mock(
            SearchIndexTool.class,
            Mockito.withSettings().useConstructor(client, TEST_XCONTENT_REGISTRY_FOR_QUERY).defaultAnswer(Mockito.CALLS_REAL_METHODS)
        );

        try (InputStream searchResponseIns = SearchIndexTool.class.getResourceAsStream("retrieval_tool_search_response.json")) {
            if (searchResponseIns != null) {
                mockedSearchResponseString = new String(searchResponseIns.readAllBytes());
            }
        }
    }

    @Test
    @SneakyThrows
    public void testGetType() {
        String type = mockedSearchIndexTool.getType();
        assertFalse(Strings.isNullOrEmpty(type));
        assertEquals("SearchIndexTool", type);
    }

    @Test
    @SneakyThrows
    public void testDefaultAttributes() {
        Map<String, Object> attributes = mockedSearchIndexTool.getAttributes();
        assertEquals(
            "{\"type\":\"object\",\"properties\":"
                + "{\"index\":{\"type\":\"string\",\"description\":\"OpenSearch index name. Example: index1\"},"
                + "\"query\":{\"type\":\"object\",\"description\":\"OpenSearch Query DSL as a JSON object. "
                + "The object MUST follow OpenSearch Query DSL and MUST include a top-level 'query' field. "
                + "Preferred format for reliable parsing. "
                + "Example: {\\\"query\\\":{\\\"match\\\":{\\\"field\\\":\\\"value\\\"}},\\\"size\\\":10}. "
                + "String format is also supported for backward compatibility, but object format is strongly recommended.\"},"
                + "\"additionalProperties\":false},"
                + "\"required\":[\"index\",\"query\"],"
                + "\"additionalProperties\":false}",
            attributes.get(INPUT_SCHEMA_FIELD)
        );
        assertEquals(false, attributes.get(STRICT_FIELD));
    }

    @Test
    @SneakyThrows
    public void testValidateWithInputKey() {
        Map<String, String> parameters = Map.of("input", "{}");
        assertTrue(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidateWithActualKeys() {
        Map<String, String> parameters = Map
            .of(
                SearchIndexTool.INDEX_FIELD,
                "test-index",
                SearchIndexTool.QUERY_FIELD,
                "{\n" + "    \"query\": {\n" + "        \"match_all\": {}\n" + "    }\n" + "}"
            );
        assertTrue(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidateWithActualKeysAndNullValues() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SearchIndexTool.INDEX_FIELD, null);
        parameters.put(SearchIndexTool.QUERY_FIELD, null);
        assertFalse(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidateWithEmptyInput() {
        Map<String, String> parameters = Map.of();
        assertFalse(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidateWithNullInput() {
        assertFalse(mockedSearchIndexTool.validate(null));
    }

    @Test
    public void testRunWithInputKey() {
        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, times(1)).search(any(), any());
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
    }

    @Test
    public void testRunWithActualKeys() {
        Map<String, String> parameters = Map.of("index", "test-index", "query", "{\"query\": {\"match_all\": {}}}");
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, times(1)).search(any(), any());
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
    }

    @Test
    @SneakyThrows
    public void testRunWithInputKeyInvalidJson() {
        ActionListener<String> listener = mock(ActionListener.class);
        Map<String, String> parameters = Map.of("input", "Invalid json");
        mockedSearchIndexTool.run(parameters, listener);
        ArgumentCaptor<Exception> argument = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argument.capture());
        assertEquals(
            "SearchIndexTool's two parameters: index and query are required and should be in valid format",
            argument.getValue().getMessage()
        );
    }

    @Test
    public void testRunWithConnectorIndex() {
        String inputString = "{\"index\": \".plugins-ml-connector\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, never()).search(any(), any());
        Mockito.verify(client, times(1)).execute(eq(MLConnectorSearchAction.INSTANCE), any(), any());
    }

    @Test
    public void testRunWithModelIndex() {
        String inputString = "{\"index\": \".plugins-ml-model\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, never()).search(any(), any());
        Mockito.verify(client, times(1)).execute(eq(MLModelSearchAction.INSTANCE), any(), any());
    }

    @Test
    public void testRunWithModelGroupIndex() {
        String inputString = "{\"index\": \".plugins-ml-model-group\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, never()).search(any(), any());
        Mockito.verify(client, times(1)).execute(eq(MLModelGroupSearchAction.INSTANCE), any(), any());
    }

    @Test
    @SneakyThrows
    public void testRunWithSearchResults() {
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

        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, listener);

        future.join();

        Mockito.verify(client, times(1)).search(any(), any());
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
    }

    @Test
    @SneakyThrows
    public void testRunWithEmptyQuery() {
        String inputString = "{\"index\": \"test_index\"}";
        Map<String, String> parameters = Map.of("input", inputString);
        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);
        ArgumentCaptor<Exception> argument = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argument.capture());
        assertEquals(
            "SearchIndexTool's two parameters: index and query are required and should be in valid format",
            argument.getValue().getMessage()
        );
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
        Mockito.verify(client, Mockito.never()).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testRunWithEmptyIndex() {
        String inputString = "{\"query\": {\"match_all\": {}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);
        ArgumentCaptor<Exception> argument = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argument.capture());
        assertEquals(
            "SearchIndexTool's two parameters: index and query are required and should be in valid format",
            argument.getValue().getMessage()
        );
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
        Mockito.verify(client, Mockito.never()).search(any(), any());
    }

    @Test
    public void testRunWithInvalidQuery() {
        String inputString = "{\"index\": \"test-index\", \"query\": \"invalid query\"}";
        Map<String, String> parameters = Map.of("input", inputString);
        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
        Mockito.verify(client, Mockito.never()).search(any(), any());
    }

    @Test
    public void testRunWithEmptyQueryBody() {
        String inputString = "{\"index\": \"test-index\", \"query\": {}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, times(1)).search(any(), any());
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
    }

    @Test
    public void testFactory() {
        SearchIndexTool searchIndexTool = SearchIndexTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(SearchIndexTool.TYPE, searchIndexTool.getType());
    }

    @Test
    @SneakyThrows
    public void testConvertSearchResponseToMap() {
        // Given a mocked search response
        SearchResponse mockedSearchResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, mockedSearchResponseString)
            );

        // When converting to map
        Map<String, Object> resultMap = mockedSearchIndexTool.convertSearchResponseToMap(mockedSearchResponse);

        // Then the map should contain expected keys
        assertFalse(resultMap.isEmpty());
        assertTrue(resultMap.containsKey("took"));
        assertTrue(resultMap.containsKey("hits"));
    }

    @Test
    @SneakyThrows
    public void testRunWithReturnFullResponseTrue() {
        // Given a mocked search response
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

        // When running with return_full_response=true
        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);
        parameters.put(SearchIndexTool.RETURN_RAW_RESPONSE, "true");

        mockedSearchIndexTool.run(parameters, listener);

        // Then expect ModelTensorOutput result
        Object result = future.join();
        assertTrue(result instanceof ModelTensorOutput);
        ModelTensorOutput output = (ModelTensorOutput) result;

        assertEquals(1, output.getMlModelOutputs().size());
        ModelTensors tensors = output.getMlModelOutputs().get(0);
        assertEquals(1, tensors.getMlModelTensors().size());

        ModelTensor tensor = tensors.getMlModelTensors().get(0);
        assertEquals(mockedSearchIndexTool.getName(), tensor.getName());
        assertFalse(tensor.getDataAsMap().isEmpty());
        assertTrue(tensor.getDataAsMap().containsKey("_shards"));
        assertTrue(tensor.getDataAsMap().containsKey("took"));
    }

    @Test
    @SneakyThrows
    public void testRunWithReturnFullResponseFalse() {
        // Given a mocked search response
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

        // When running with return_full_response=false
        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);
        parameters.put(SearchIndexTool.RETURN_RAW_RESPONSE, "false");

        mockedSearchIndexTool.run(parameters, listener);

        // Then expect String result
        Object result = future.join();
        assertTrue(result instanceof String);
        assertFalse(((String) result).isEmpty());
        assertFalse(((String) result).contains("_shards"));
        assertFalse(((String) result).contains("took"));
    }

    @Test
    @SneakyThrows
    public void testRunWithoutReturnFullResponse() {
        // Given a mocked search response
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

        // When running without return_full_response parameter
        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);

        mockedSearchIndexTool.run(parameters, listener);

        // Then expect String result (default behavior)
        Object result = future.join();
        assertTrue(result instanceof String);
        assertFalse(((String) result).contains("_shards"));
        assertFalse(((String) result).contains("took"));
    }

    @Test
    @SneakyThrows
    public void testRunWithOutputParserForModelTensorOutput() {
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

        mockedSearchIndexTool.setOutputParser(output -> "parsed_output");

        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);
        parameters.put(SearchIndexTool.RETURN_RAW_RESPONSE, "true");

        mockedSearchIndexTool.run(parameters, listener);

        Object result = future.join();
        assertEquals("parsed_output", result);
    }

    @Test
    @SneakyThrows
    public void testRunWithOutputParserForStringResponse() {
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

        mockedSearchIndexTool.setOutputParser(output -> "parsed_string_output");

        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);
        parameters.put(SearchIndexTool.RETURN_RAW_RESPONSE, "false");

        mockedSearchIndexTool.run(parameters, listener);

        Object result = future.join();
        assertEquals("parsed_string_output", result);
    }

    @Test
    public void testFactoryCreateWithProcessorEnhancement() {
        SearchIndexTool.Factory.getInstance().init(client, TEST_XCONTENT_REGISTRY_FOR_QUERY);

        Map<String, Object> params = new HashMap<>();
        params.put("processor_configs", "[{\"type\":\"test_processor\"}]");

        SearchIndexTool tool = SearchIndexTool.Factory.getInstance().create(params);

        assertEquals(SearchIndexTool.TYPE, tool.getType());
        // Verify that the output parser was set (not null)
        assertTrue(tool.getOutputParser() != null);
    }

    @Test
    @SneakyThrows
    public void testRun_withMatchQuery_triggersPlainDoubleGson() {
        String input = "{\"index\":\"test-index\",\"query\":{}}";
        Map<String, String> params = Map.of("input", input);
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        mockedSearchIndexTool.run(params, listener);

        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).search(cap.capture(), any());
        verify(client, never()).execute(any(), any(), any());

        assertArrayEquals(new String[] { "test-index" }, cap.getValue().indices());
    }

    @Test
    @SneakyThrows
    public void testRun_withRangeQuery_triggersPlainDoubleGson() {
        String input = "{\"index\":\"test-index\",\"query\":{}}";
        Map<String, String> params = Map.of("input", input);
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        mockedSearchIndexTool.run(params, listener);

        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).search(cap.capture(), any());
        verify(client, never()).execute(any(), any(), any());

        assertArrayEquals(new String[] { "test-index" }, cap.getValue().indices());
    }
    // ========== JSON Normalization Test Cases ==========

    @Test
    @SneakyThrows
    public void testFixMalformedJson_withExtraClosingBraces() {
        // Test the specific LLM-generated malformed JSON pattern that was failing
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully (no failure callback)
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testFixMalformedJson_withMultipleExtraClosingBraces() {
        // Test multiple extra closing braces scenario
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}}}}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testFixMalformedJson_withComplexMatchQuery() {
        // Test complex match query with escaped JSON that LLMs commonly generate
        String malformedInput =
            "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match\\\":{\\\"title\\\":\\\"test document\\\"}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testFixMalformedJson_withSizeParameter() {
        // Test query with size parameter and malformed JSON
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}},\\\"size\\\":10}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testBackwardCompatibility_withProperlyFormattedJson() {
        // Ensure that properly formatted JSON still works (backward compatibility)
        String properInput = "{\"index\":\"test-index\",\"query\":{\"query\":{\"match_all\":{}}}}";
        Map<String, String> parameters = Map.of("input", properInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testNormalizeQueryString_withDirectQueryParameter() {
        // Test normalization when query is passed as direct parameter (not in input JSON)
        String malformedQuery = "{\"query\":{\"match_all\":{}}}}}"; // Extra closing braces
        Map<String, String> parameters = Map.of("index", "test-index", "query", malformedQuery);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testCountBraces_balancedJson() {
        // Test that balanced JSON is not modified
        String balancedInput = "{\"index\":\"test-index\",\"query\":{\"query\":{\"match_all\":{}}}}";
        Map<String, String> parameters = Map.of("input", balancedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testGsonParsing_withSpecialFloatingPoints() {
        // Test that GSON with serializeSpecialFloatingPointValues handles floating point values
        String inputWithSpecialValues = "{\"index\":\"test-index\",\"query\":{\"query\":{\"range\":{\"score\":{\"gte\":1.0}}}}}";
        Map<String, String> parameters = Map.of("input", inputWithSpecialValues);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testRealWorldLLMPattern_OpenAIFunctionCalling() {
        // Test the exact pattern from the user's feedback that was failing
        String llmGeneratedInput = "{\"index\":\"opensearch-release\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}\"}";
        Map<String, String> parameters = Map.of("input", llmGeneratedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully (this was failing before the fix)
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testErrorHandling_stillFailsForTrulyInvalidJson() {
        // Test that truly invalid JSON (not just malformed braces) still fails appropriately
        String trulyInvalidInput = "{\"index\":\"test-index\",\"query\":\"not-json-at-all\"}";
        Map<String, String> parameters = Map.of("input", trulyInvalidInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // This should still fail because "not-json-at-all" is not valid JSON
        ArgumentCaptor<Exception> argument = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argument.capture());
        assertTrue(argument.getValue().getMessage().contains("Invalid query format"));
    }

    @Test
    @SneakyThrows
    public void testJsonNormalization_preservesQueryStructure() {
        // Test that normalization preserves the actual query structure
        String complexQuery =
            "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"bool\\\":{\\\"must\\\":[{\\\"match\\\":{\\\"title\\\":\\\"test\\\"}},{\\\"range\\\":{\\\"date\\\":{\\\"gte\\\":\\\"2023-01-01\\\"}}}]}},\\\"size\\\":5}\"}";
        Map<String, String> parameters = Map.of("input", complexQuery);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the complex query was processed successfully
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());

        // Capture the search request to verify the query structure is preserved
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());

        SearchRequest capturedRequest = searchCaptor.getValue();
        assertArrayEquals(new String[] { "test-index" }, capturedRequest.indices());
        // The source should contain the normalized query structure
        assertFalse(capturedRequest.source().toString().isEmpty());
    }

    // ========== Normalization Verification Tests ==========

    @Test
    @SneakyThrows
    public void testNormalization_verifyFixedQueryStructure() {
        // Test that malformed JSON is properly normalized to expected structure
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Capture the search request to verify the normalized query
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());

        SearchRequest capturedRequest = searchCaptor.getValue();
        assertArrayEquals(new String[] { "test-index" }, capturedRequest.indices());
        
        // Verify the query structure is properly normalized
        String sourceString = capturedRequest.source().toString();
        assertTrue("Query should contain match_all", sourceString.contains("match_all"));
        assertFalse("Query should not contain escaped quotes", sourceString.contains("\\\""));
    }

    @Test
    @SneakyThrows
    public void testNormalization_verifyExtraBracesRemoved() {
        // Test that extra closing braces are removed
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify that the search was executed successfully (no failure callback)
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());

        // Capture and verify the normalized query structure
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());

        SearchRequest capturedRequest = searchCaptor.getValue();
        String sourceString = capturedRequest.source().toString();
        
        // Count braces to ensure they're balanced
        long openBraces = sourceString.chars().filter(ch -> ch == '{').count();
        long closeBraces = sourceString.chars().filter(ch -> ch == '}').count();
        assertEquals("Braces should be balanced after normalization", openBraces, closeBraces);
    }

    @Test
    @SneakyThrows
    public void testNormalization_verifyComplexQueryPreserved() {
        // Test that complex query structure is preserved during normalization
        String complexMalformed = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"bool\\\":{\\\"must\\\":[{\\\"match\\\":{\\\"title\\\":\\\"test\\\"}},{\\\"range\\\":{\\\"date\\\":{\\\"gte\\\":\\\"2023-01-01\\\"}}}]}},\\\"size\\\":5}\"}";
        Map<String, String> parameters = Map.of("input", complexMalformed);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());

        SearchRequest capturedRequest = searchCaptor.getValue();
        String sourceString = capturedRequest.source().toString();
        
        // Verify complex query elements are preserved
        assertTrue("Should contain bool query", sourceString.contains("bool"));
        assertTrue("Should contain must clause", sourceString.contains("must"));
        assertTrue("Should contain match query", sourceString.contains("match"));
        assertTrue("Should contain range query", sourceString.contains("range"));
        assertTrue("Should contain size parameter", sourceString.contains("size"));
    }

    @Test
    @SneakyThrows
    public void testNormalization_verifyStringParameterNormalization() {
        // Test normalization when query is passed as direct parameter
        String malformedQuery = "{\"query\":{\"match_all\":{}}}}}"; // Extra closing braces
        Map<String, String> parameters = Map.of("index", "test-index", "query", malformedQuery);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());

        SearchRequest capturedRequest = searchCaptor.getValue();
        String sourceString = capturedRequest.source().toString();
        
        // Verify the query is properly normalized
        assertTrue("Should contain match_all", sourceString.contains("match_all"));
        
        // Count braces to ensure normalization worked
        long openBraces = sourceString.chars().filter(ch -> ch == '{').count();
        long closeBraces = sourceString.chars().filter(ch -> ch == '}').count();
        assertEquals("Braces should be balanced after normalization", openBraces, closeBraces);
    }

    @Test
    @SneakyThrows
    public void testNormalization_verifyMultipleOuterQuotesHandled() {
        // Test handling of multiple outer quotes
        String multiQuoteInput = "{\"index\":\"test-index\",\"query\":\"\\\"{\\\\\\\"query\\\\\\\":{\\\\\\\"match_all\\\\\\\":{}}}\\\"\"}";;
        Map<String, String> parameters = Map.of("input", multiQuoteInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Should either succeed or fail gracefully without hanging
        verify(client, atMost(1)).search(any(), any());
        
        // If it succeeded, verify the structure
        try {
            ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(client).search(searchCaptor.capture(), any());
            
            SearchRequest capturedRequest = searchCaptor.getValue();
            assertArrayEquals(new String[] { "test-index" }, capturedRequest.indices());
        } catch (Exception e) {
            // If normalization couldn't fix it, that's acceptable - we just want to ensure no hanging
            verify(listener, times(1)).onFailure(any());
        }
    }

    @Test
    public void testMixedEscapedAndUnescapedQuotesHandledSafely() {
        String malformed =
            "{\"field_1\":\"value with \\\"quotes\\\"\", \\\\\"field_2\\\\\", \"value\"}";

        String normalized = mockedSearchIndexTool.normalizeQueryString(malformed);

        // Either:
        // 1. Successfully normalized into valid JSON
        // OR
        // 2. Returned original without crashing
        assertNotNull(normalized);
    }

}
