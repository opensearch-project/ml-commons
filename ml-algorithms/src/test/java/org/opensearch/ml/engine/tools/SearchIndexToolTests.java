package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.search.SearchModule;

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
        mockedSearchIndexTool = Mockito
            .mock(
                SearchIndexTool.class,
                Mockito.withSettings().useConstructor(client, TEST_XCONTENT_REGISTRY_FOR_QUERY).defaultAnswer(Mockito.CALLS_REAL_METHODS)
            );

        try (InputStream searchResponseIns = AbstractRetrieverTool.class.getResourceAsStream("retrieval_tool_search_response.json")) {
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
    public void testValidate() {
        Map<String, String> parameters = Map.of("input", "{}");
        assertTrue(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    @SneakyThrows
    public void testValidateWithEmptyInput() {
        Map<String, String> parameters = Map.of();
        assertFalse(mockedSearchIndexTool.validate(parameters));
    }

    @Test
    public void testRunWithNormalIndex() {
        String inputString = "{\"index\": \"test-index\", \"query\": {\"match_all\": {}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, times(1)).search(any(), any());
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
    }

    @Test
    public void testRunWithConnectorIndex() {
        String inputString = "{\"index\": \".plugins-ml-connector\", \"query\": {\"match_all\": {}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, never()).search(any(), any());
        Mockito.verify(client, times(1)).execute(eq(MLConnectorSearchAction.INSTANCE), any(), any());
    }

    @Test
    public void testRunWithModelIndex() {
        String inputString = "{\"index\": \".plugins-ml-model\", \"query\": {\"match_all\": {}}}";
        Map<String, String> parameters = Map.of("input", inputString);
        mockedSearchIndexTool.run(parameters, null);
        Mockito.verify(client, never()).search(any(), any());
        Mockito.verify(client, times(1)).execute(eq(MLModelSearchAction.INSTANCE), any(), any());
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

        String inputString = "{\"index\": \"test-index\", \"query\": {\"match_all\": {}}}";
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
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
        Mockito.verify(client, Mockito.never()).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("[" + SearchIndexTool.QUERY_FIELD + "] is null or empty, can not process it.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testRunWithInvalidQuery() {
        String inputString = "{\"index\": \"test-index\", \"query\": \"invalid query\"}";
        Map<String, String> parameters = Map.of("input", inputString);
        ActionListener<String> listener = mock(ActionListener.class);
        mockedSearchIndexTool.run(parameters, listener);
        Mockito.verify(client, Mockito.never()).execute(any(), any(), any());
        Mockito.verify(client, Mockito.never()).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(ParsingException.class);
        // since error message for ParsingException is different, we only need to expect ParsingException to be thrown
        verify(listener).onFailure(argumentCaptor.capture());
    }
}
