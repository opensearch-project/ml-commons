/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
                + "{\"index\":{\"type\":\"string\",\"description\":\"OpenSearch index name. for example: index1\"},"
                + "\"query\":{\"type\":\"object\",\"description\":\"OpenSearch search index query. "
                + "You need to get index mapping to write correct search query. It must be a valid OpenSearch query. "
                + "Valid value:\\n{\\\"query\\\":{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}},\\\"size\\\":2,\\\"_source\\\":\\\"population_description\\\"}"
                + "\\nInvalid value: \\n{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}}\\nThe value is invalid because the match not wrapped by \\\"query\\\".\","
                + "\"additionalProperties\":false}},\"required\":[\"index\",\"query\"],\"additionalProperties\":false}",
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

}
