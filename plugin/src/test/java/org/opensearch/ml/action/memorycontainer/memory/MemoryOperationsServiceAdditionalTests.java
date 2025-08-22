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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
import org.opensearch.transport.client.Client;

public class MemoryOperationsServiceAdditionalTests {

    @Mock
    private Client client;

    @Mock
    private MemoryEmbeddingHelper memoryEmbeddingHelper;

    @Mock
    private ActionListener<List<MemoryResult>> operationsListener;

    private MemoryOperationsService memoryOperationsService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryOperationsService = new MemoryOperationsService(client, memoryEmbeddingHelper);
    }

    @Test
    public void testExecuteMemoryOperations_BulkWithFailures() {
        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("New fact");

        List<MemoryDecision> decisions = Arrays.asList(addDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.isSemanticStorageEnabled()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(true);
        when(bulkResponse.buildFailureMessage()).thenReturn("Some operations failed");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

        verify(client).bulk(any(), any());
        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testCreateFactMemoriesFromList_WithUser() {
        List<String> facts = Arrays.asList("User name is John");
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());

        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = new User("testuser", null, null, Collections.emptyList());

        java.time.Instant now = java.time.Instant.now();
        List<org.opensearch.action.index.IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        memoryOperationsService.createFactMemoriesFromList(facts, input, indexName, sessionId, user, now, indexRequests, memoryInfos);

        assert indexRequests.size() == 1;
        assert memoryInfos.size() == 1;
    }
}
