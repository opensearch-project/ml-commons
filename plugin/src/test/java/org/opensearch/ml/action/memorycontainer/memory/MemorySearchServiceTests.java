/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.transport.client.Client;

public class MemorySearchServiceTests {

    @Mock
    private Client client;

    @Mock
    private ActionListener<List<FactSearchResult>> listener;

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
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);

        memorySearchService.searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_NullSessionId() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = null;
        String indexName = "memory-index";
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);

        memorySearchService.searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_SearchFailure() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.getMaxInferSize()).thenReturn(5);

        Exception searchException = new RuntimeException("Search failed");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onFailure(searchException);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, listener);

        verify(client).search(any(), any());
        // Should still call listener.onResponse with empty results due to error handling
    }
}
