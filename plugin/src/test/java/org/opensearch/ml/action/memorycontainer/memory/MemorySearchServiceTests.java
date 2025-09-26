/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.transport.client.Client;

public class MemorySearchServiceTests {

    @Mock
    private Client client;

    @Mock
    private ActionListener<List<FactSearchResult>> listener;

    @Mock
    MLAddMemoriesInput input;
    @Mock
    MemoryStrategy strategy;

    private MemorySearchService memorySearchService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memorySearchService = new MemorySearchService(client);
    }

    @Test
    public void testSearchSimilarFactsForSession_EmptyFacts() {
        List<String> facts = Arrays.asList();
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_NullSessionId() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = null;
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_SearchFailure() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(5);

        Exception searchException = new RuntimeException("Search failed");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onFailure(searchException);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client).search(any(), any());
    }

    @Test
    public void testSearchSimilarFactsForSession_SuccessfulSearch() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(5);

        // Mock search response with empty results
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client).search(any(), any());
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_NullStorageConfig() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = null;

        // Mock search response with empty results
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client).search(any(), any());
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_MultipleFacts() {
        List<String> facts = Arrays.asList("User name is John", "User age is 25", "User city is Seattle");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(2);

        // Mock search response with empty results
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        // Should be called 2 times (limited by maxInferSize)
        verify(client, times(2)).search(any(), any());
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_WithMaxInferSizeLimit() {
        List<String> facts = Arrays.asList("Fact1", "Fact2", "Fact3", "Fact4", "Fact5");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(3);

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client, times(3)).search(any(), any()); // Limited by maxInferSize
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_DefaultMaxInferSize() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(null); // Test default value

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client).search(any(), any());
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_QueryBuildException() {
        List<String> facts = Arrays.asList("Test fact");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(5);

        // Mock client to throw exception
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Search failed"));
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        // The method continues processing even when individual searches fail, so it returns empty results
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_EmptySessionId() {
        List<String> facts = Arrays.asList("Test fact");
        String sessionId = null;
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_WithSearchHits() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.getMaxInferSize()).thenReturn(5);

        // Mock search response with actual hits
        SearchResponse searchResponse = mock(SearchResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "User name is Jane");

        org.opensearch.search.SearchHit hit;
        try {
            hit = new org.opensearch.search.SearchHit(0, "hit-1", null, null)
                .sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            hit.score(0.9f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        org.opensearch.search.SearchHits searchHits = new org.opensearch.search.SearchHits(
            new org.opensearch.search.SearchHit[] { hit },
            null,
            1.0f
        );

        when(searchResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(client).search(any(), any());
        verify(listener).onResponse(any(List.class));
    }
}
