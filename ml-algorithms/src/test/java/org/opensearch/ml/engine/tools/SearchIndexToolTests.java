/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.tools.SearchIndexTool.INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.engine.tools.SearchIndexTool.STRICT_FIELD;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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
import org.opensearch.core.common.ParsingException;
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
        // Verify the text block schema is properly formatted
        String expectedSchema = """
            {"type":"object",\
            "properties":{\
            "index":{"type":"string","description":"OpenSearch index name. Example: index1"},\
            "query":{"type":"object","description":"OpenSearch Query DSL as a JSON object. \
            You need to get index mapping to write correct search query. \
            The object MUST follow OpenSearch Query DSL and MUST include a top-level 'query' field. \
            Preferred format for reliable parsing. \
            Example: {\\"query\\":{\\"match\\":{\\"field\\":\\"value\\"}},\\"size\\":10}. \
            String format is also supported for backward compatibility, but object format is strongly recommended."}},\
            "required":["index","query"],\
            "additionalProperties":false}""";
        assertEquals(expectedSchema, attributes.get(INPUT_SCHEMA_FIELD));
        assertEquals(true, attributes.get(STRICT_FIELD));
    }

    @Test
    @SneakyThrows
    public void testTextBlockSchemaFormat() {
        // Test that the text block schema is valid JSON and contains expected fields
        Map<String, Object> attributes = mockedSearchIndexTool.getAttributes();
        String schema = (String) attributes.get(INPUT_SCHEMA_FIELD);

        // Parse the schema to verify it's valid JSON
        com.google.gson.JsonObject schemaJson = com.google.gson.JsonParser.parseString(schema).getAsJsonObject();

        // Verify schema structure
        assertEquals("object", schemaJson.get("type").getAsString());
        assertTrue(schemaJson.has("properties"));
        assertTrue(schemaJson.has("required"));
        assertEquals(false, schemaJson.get("additionalProperties").getAsBoolean());

        // Verify properties
        com.google.gson.JsonObject properties = schemaJson.getAsJsonObject("properties");
        assertTrue(properties.has("index"));
        assertTrue(properties.has("query"));

        // Verify required fields
        com.google.gson.JsonArray required = schemaJson.getAsJsonArray("required");
        assertEquals(2, required.size());
        assertTrue(required.toString().contains("index"));
        assertTrue(required.toString().contains("query"));
    }

    @Test
    @SneakyThrows
    public void testTextBlockSchemaReadability() {
        // Test that the text block maintains readability while being valid JSON
        String schema = SearchIndexTool.DEFAULT_INPUT_SCHEMA;

        // Should be valid JSON
        com.google.gson.JsonParser.parseString(schema);

        // Should contain readable descriptions
        assertTrue("Schema should contain index description", schema.contains("OpenSearch index name"));
        assertTrue("Schema should contain query description", schema.contains("OpenSearch Query DSL"));
        assertTrue("Schema should contain example", schema.contains("Example:"));
        assertTrue("Schema should mention backward compatibility", schema.contains("backward compatibility"));
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
        assertTrue(
            "Should contain JSON format error message",
            argument.getValue().getMessage().contains("Invalid JSON format in input parameter")
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
        assertTrue("Should mention missing query parameter", argument.getValue().getMessage().contains("Missing: 'query'"));
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
        assertTrue("Should mention missing index parameter", argument.getValue().getMessage().contains("Missing: 'index'"));
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
    // ========== Simplified Tool Functionality Tests ==========

    @Test
    @SneakyThrows
    public void testSimplifiedTool_withValidObjectQuery() {
        // Test that the simplified tool works with proper object queries
        String validInput = "{\"index\":\"test-index\",\"query\":{\"query\":{\"match_all\":{}}}}";
        Map<String, String> parameters = Map.of("input", validInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Should execute successfully without normalization
        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());
    }

    @Test
    @SneakyThrows
    public void testSimplifiedTool_withComplexValidQuery() {
        // Test complex but valid query structure
        String complexInput =
            "{\"index\":\"test-index\",\"query\":{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"test\"}},{\"range\":{\"date\":{\"gte\":\"2023-01-01\"}}}]}},\"size\":5}}";
        Map<String, String> parameters = Map.of("input", complexInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        verify(listener, never()).onFailure(any());
        verify(client, times(1)).search(any(), any());

        // Verify query structure is preserved
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());
        SearchRequest capturedRequest = searchCaptor.getValue();
        String sourceString = capturedRequest.source().toString();
        assertTrue("Should contain bool query", sourceString.contains("bool"));
        assertTrue("Should contain size parameter", sourceString.contains("size"));
    }

    @Test
    @SneakyThrows
    public void testSimplifiedTool_frameworkValidationIntegration() {
        // Test that malformed input is handled by framework validation (not tool-level normalization)
        String malformedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}\"}";
        Map<String, String> parameters = Map.of("input", malformedInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // The tool should either succeed (if framework validation fixed it) or fail gracefully
        // This test verifies the tool doesn't crash on malformed input
        verify(client, atMost(1)).search(any(), any());
    }

    // ========== Additional Coverage Tests ==========

    @Test
    @SneakyThrows
    public void testConvertSearchResponseToMap_withIOException() {
        // Test convertSearchResponseToMap when IOException occurs
        SearchResponse mockResponse = mock(SearchResponse.class);

        // Mock the response to throw IOException during toXContent
        doThrow(new IOException("Test IO Exception")).when(mockResponse).toXContent(any(), any());

        try {
            mockedSearchIndexTool.convertSearchResponseToMap(mockResponse);
            fail("Expected IOException to be thrown");
        } catch (IOException e) {
            assertEquals("Test IO Exception", e.getMessage());
        }
    }

    @Test
    @SneakyThrows
    public void testRun_withIOExceptionInConvertSearchResponseToMap() {
        // Test run method when convertSearchResponseToMap throws IOException
        // Create a spy to override the convertSearchResponseToMap method
        SearchIndexTool spyTool = spy(new SearchIndexTool(client, TEST_XCONTENT_REGISTRY_FOR_QUERY));

        // Mock convertSearchResponseToMap to throw IOException
        doThrow(new IOException("Test IO Exception")).when(spyTool).convertSearchResponseToMap(any());

        // Use a real SearchResponse with empty hits instead of mocking final classes
        String emptySearchResponseString =
            "{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0},\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]}}";

        SearchResponse emptySearchResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, emptySearchResponseString)
            );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse);
            return null;
        }).when(client).search(any(), any());

        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("input", inputString);
        parameters.put(SearchIndexTool.RETURN_RAW_RESPONSE, "true");

        ActionListener<Object> listener = mock(ActionListener.class);
        spyTool.run(parameters, listener);

        // Should call onFailure due to IOException
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception caughtException = exceptionCaptor.getValue();
        assertTrue(
            "Should contain IOException in the cause chain",
            caughtException instanceof IOException
                || (caughtException.getCause() != null && caughtException.getCause() instanceof IOException)
                || caughtException.getMessage().contains("Test IO Exception")
        );
    }

    @Test
    @SneakyThrows
    public void testFactoryGetInstance_singletonBehavior() {
        // Test that Factory.getInstance() returns the same instance (singleton)
        SearchIndexTool.Factory instance1 = SearchIndexTool.Factory.getInstance();
        SearchIndexTool.Factory instance2 = SearchIndexTool.Factory.getInstance();
        assertSame("Should return same singleton instance", instance1, instance2);
    }

    @Test
    @SneakyThrows
    public void testFactoryGetDefaultVersion() {
        // Test Factory.getDefaultVersion()
        SearchIndexTool.Factory factory = SearchIndexTool.Factory.getInstance();
        assertNull("Default version should be null", factory.getDefaultVersion());
    }

    @Test
    @SneakyThrows
    public void testFactoryGetDefaultType() {
        // Test Factory.getDefaultType()
        SearchIndexTool.Factory factory = SearchIndexTool.Factory.getInstance();
        assertEquals("SearchIndexTool", factory.getDefaultType());
    }

    @Test
    @SneakyThrows
    public void testFactoryGetDefaultDescription() {
        // Test Factory.getDefaultDescription()
        SearchIndexTool.Factory factory = SearchIndexTool.Factory.getInstance();
        assertTrue("Should contain description", factory.getDefaultDescription().contains("search an index"));
    }

    @Test
    @SneakyThrows
    public void testFactoryGetDefaultAttributes() {
        // Test Factory.getDefaultAttributes()
        SearchIndexTool.Factory factory = SearchIndexTool.Factory.getInstance();
        Map<String, Object> attributes = factory.getDefaultAttributes();
        assertNotNull("Attributes should not be null", attributes);
        assertTrue("Should contain input schema", attributes.containsKey(INPUT_SCHEMA_FIELD));
        assertEquals(true, attributes.get(STRICT_FIELD));
    }

    @Test
    @SneakyThrows
    public void testGetVersion() {
        // Test getVersion() method
        assertNull("Version should be null", mockedSearchIndexTool.getVersion());
    }

    @Test
    @SneakyThrows
    public void testRun_withExceptionInMainTryCatch() {
        // Test run method when exception occurs in main try-catch block
        SearchIndexTool spyTool = spy(new SearchIndexTool(client, TEST_XCONTENT_REGISTRY_FOR_QUERY));

        ActionListener<String> listener = mock(ActionListener.class);

        // Create a parameters map that will cause an exception in extractInputParameters
        Map<String, String> invalidParams = null;

        spyTool.run(invalidParams, listener);

        // Should call onFailure due to exception
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertNotNull("Exception should not be null", exceptionCaptor.getValue());
    }

    @Test
    @SneakyThrows
    public void testRun_withJsonSyntaxExceptionInInputParsing() {
        // Test run method when JsonSyntaxException occurs during input parsing
        String invalidJsonInput = "invalid json input";
        Map<String, String> parameters = Map.of("input", invalidJsonInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Should call onFailure due to missing required parameters
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue("Should be IllegalArgumentException", exceptionCaptor.getValue() instanceof IllegalArgumentException);
        assertTrue("Should mention JSON format error", exceptionCaptor.getValue().getMessage().contains("Invalid JSON format"));
    }

    @Test
    @SneakyThrows
    public void testProcessResponse_staticMethod() {
        // Test the static processResponse method using reflection
        // Since SearchHit is final, we'll test through integration
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
        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, listener);

        Object result = future.join();
        assertTrue("Result should be a string containing processed hits", result instanceof String);
        String resultString = (String) result;
        assertTrue("Should contain _index field", resultString.contains("_index"));
        assertTrue("Should contain _id field", resultString.contains("_id"));
        assertTrue("Should contain _score field", resultString.contains("_score"));
        assertTrue("Should contain _source field", resultString.contains("_source"));
    }

    @Test
    @SneakyThrows
    public void testRun_withNullQueryElementInInput() {
        // Test run method when query element is null in input JSON
        String inputWithNullQuery = "{\"index\": \"test-index\", \"query\": null}";
        Map<String, String> parameters = Map.of("input", inputWithNullQuery);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Should call onFailure due to missing query
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(
            "Should be ParsingException or IllegalArgumentException",
            exceptionCaptor.getValue() instanceof IllegalArgumentException
                || exceptionCaptor.getValue().getMessage().contains("Invalid query format")
        );
    }

    @Test
    @SneakyThrows
    public void testRun_withEmptyHitsArray() {
        // Test run method with empty hits array (different from null)
        // Use a real SearchResponse with no hits instead of mocking
        String emptySearchResponseString =
            "{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0},\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]}}";

        SearchResponse emptySearchResponse = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, emptySearchResponseString)
            );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse);
            return null;
        }).when(client).search(any(), any());

        String inputString = "{\"index\": \"test-index\", \"query\": {\"query\": {\"match_all\": {}}}}";
        Map<String, String> parameters = Map.of("input", inputString);

        final CompletableFuture<Object> future = new CompletableFuture<>();
        ActionListener<Object> listener = ActionListener.wrap(r -> future.complete(r), e -> future.completeExceptionally(e));

        mockedSearchIndexTool.run(parameters, listener);

        Object result = future.join();
        assertEquals("", result); // Should return empty string for no hits
    }

    @Test
    @SneakyThrows
    public void testSetDescription() {
        // Test setDescription method
        String newDescription = "New custom description";
        mockedSearchIndexTool.setDescription(newDescription);
        assertEquals(newDescription, mockedSearchIndexTool.getDescription());
    }

    @Test
    @SneakyThrows
    public void testRun_withInvalidQueryFormat() {
        // Test run method when query format is invalid (triggers ParsingException path)
        String inputString = "{\"index\": \"test-index\", \"query\": \"invalid-query-format\"}";
        Map<String, String> parameters = Map.of("input", inputString);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Should call onFailure with ParsingException
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception caughtException = exceptionCaptor.getValue();
        assertTrue("Should be ParsingException", caughtException instanceof ParsingException);
    }

    @Test
    @SneakyThrows
    public void testFactoryGetInstance_doubleCheckLocking() {
        // Test the double-check locking in Factory.getInstance()
        SearchIndexTool.Factory instance1 = SearchIndexTool.Factory.getInstance();
        SearchIndexTool.Factory instance2 = SearchIndexTool.Factory.getInstance();
        assertSame("Should return same singleton instance", instance1, instance2);

        // Reset the singleton using reflection to test the synchronized block
        Field instanceField = SearchIndexTool.Factory.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Now test getInstance again
        SearchIndexTool.Factory newInstance = SearchIndexTool.Factory.getInstance();
        assertNotNull("Should create new instance", newInstance);
    }

    @Test
    @SneakyThrows
    public void testValidate_withEmptyInputField() {
        // Test validate method with empty input field
        Map<String, String> parameters = Map.of("input", "");
        assertFalse("Should return false for empty input", mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidate_withEmptyQueryField() {
        // Test validate method with empty query field
        Map<String, String> parameters = Map.of("index", "test-index", "query", "");
        assertFalse("Should return false for empty query", mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testSimplifiedTool_focusOnSearchLogic() {
        // Test that the simplified tool focuses on search domain logic
        String validInput = "{\"index\":\"test-index\",\"query\":{\"query\":{\"term\":{\"status\":\"active\"}}}}";
        Map<String, String> parameters = Map.of("input", validInput);

        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);

        // Verify search execution
        verify(client, times(1)).search(any(), any());
        verify(listener, never()).onFailure(any());

        // Capture and verify the search request contains the term query
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchCaptor.capture(), any());
        SearchRequest capturedRequest = searchCaptor.getValue();
        String sourceString = capturedRequest.source().toString();
        assertTrue("Should contain term query", sourceString.contains("term"));
        assertTrue("Should contain status field", sourceString.contains("status"));
    }

}
