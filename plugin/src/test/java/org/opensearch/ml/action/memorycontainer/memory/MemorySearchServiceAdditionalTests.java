/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.transport.client.Client;

public class MemorySearchServiceAdditionalTests {

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
    public void testSearchSimilarFactsForSession_EmptyFactsList() {
        List<String> facts = Arrays.asList();
        String sessionId = "session-123";
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(facts, sessionId, indexName, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }
}
