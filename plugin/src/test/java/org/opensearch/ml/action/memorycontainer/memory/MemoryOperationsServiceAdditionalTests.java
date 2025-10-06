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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.transport.client.Client;

public class MemoryOperationsServiceAdditionalTests {

    @Mock
    private Client client;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ActionListener<List<MemoryResult>> operationsListener;

    private MemoryOperationsService memoryOperationsService;
    private MemoryStrategy strategy;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryOperationsService = new MemoryOperationsService(memoryContainerHelper);
        strategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).id("strategy-123").build();
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

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(true);
        when(bulkResponse.buildFailureMessage()).thenReturn("Some operations failed");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        Map<String, String> namespace = Map.of(SESSION_ID_FIELD, sessionId);
        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(memoryContainerHelper, times(2)).bulkIngestData(any(), any(), any());
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

        Map<String, String> strategyNameSpace = Map.of(SESSION_ID_FIELD, sessionId);
        memoryOperationsService
            .createFactMemoriesFromList(
                facts,
                indexName,
                input,
                strategyNameSpace,
                user,
                strategy,
                indexRequests,
                memoryInfos,
                "container-123"
            );

        assert indexRequests.size() == 1;
        assert memoryInfos.size() == 1;
    }
}
