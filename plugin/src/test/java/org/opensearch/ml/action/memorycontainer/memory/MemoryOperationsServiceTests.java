/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.transport.client.Client;

public class MemoryOperationsServiceTests {

    @Mock
    private Client client;

    @Mock
    private ActionListener<List<MemoryResult>> operationsListener;

    @Mock
    private ActionListener<MLAddMemoriesResponse> responseListener;

    private MemoryOperationsService memoryOperationsService;

    private MemoryConfiguration memoryConfig;
    private Map<String, String> namespace;
    @Mock
    private MemoryContainerHelper memoryContainerHelper;
    MemoryStrategy strategy;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryOperationsService = new MemoryOperationsService(memoryContainerHelper);

        memoryConfig = MemoryConfiguration
            .builder()
            .llmId("llm-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-123")
            .dimension(512)
            .maxInferSize(5)
            .disableHistory(true)
            .build();
        namespace = new HashMap<>();
        namespace.put("session_id", "session-123");

        strategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).enabled(true).id("strategy-123").build();
    }

    @Test
    public void testExecuteMemoryOperations_EmptyDecisions() {
        List<MemoryDecision> decisions = Arrays.asList();
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null; // User is final, use null instead of mock
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testExecuteMemoryOperations_AddDecision() {
        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("New fact");

        List<MemoryDecision> decisions = Arrays.asList(addDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null; // User is final, use null instead of mock

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        Map<String, String> tags = new HashMap<>();
        tags.put("key1", "value1");
        when(input.getTags()).thenReturn(tags);

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(false);

        // Mock bulk response
        BulkResponse bulkResponse = mock(BulkResponse.class);
        BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { bulkItemResponse });
        when(bulkItemResponse.isFailed()).thenReturn(false);
        when(bulkItemResponse.getId()).thenReturn("generated-id-123");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(memoryContainerHelper, times(2)).bulkIngestData(any(), any(), any());
    }

    @Test
    public void testCreateFactMemoriesFromList() {
        List<String> facts = Arrays.asList("User name is John", "User age is 30");
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        Map<String, String> tags = new HashMap<>();
        tags.put("key1", "value1");
        when(input.getTags()).thenReturn(tags);

        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null; // User is final, use null instead of mock

        Instant now = Instant.now();
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        memoryOperationsService
            .createFactMemoriesFromList(facts, indexName, input, namespace, user, strategy, indexRequests, memoryInfos, "container-123");

        // Verify that requests and infos were populated
        assert indexRequests.size() == 2;
        assert memoryInfos.size() == 2;
    }

    @Test
    public void testExecuteMemoryOperations_UpdateDecision() {
        MemoryDecision updateDecision = mock(MemoryDecision.class);
        when(updateDecision.getEvent()).thenReturn(MemoryEvent.UPDATE);
        when(updateDecision.getId()).thenReturn("memory-1");
        when(updateDecision.getText()).thenReturn("Updated fact");
        when(updateDecision.getOldMemory()).thenReturn("Old fact");

        List<MemoryDecision> decisions = Arrays.asList(updateDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(memoryContainerHelper, times(2)).bulkIngestData(any(), any(), any());
        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testExecuteMemoryOperations_DeleteDecision() {
        MemoryDecision deleteDecision = mock(MemoryDecision.class);
        when(deleteDecision.getEvent()).thenReturn(MemoryEvent.DELETE);
        when(deleteDecision.getId()).thenReturn("memory-1");
        when(deleteDecision.getText()).thenReturn("Fact to delete");

        List<MemoryDecision> decisions = Arrays.asList(deleteDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(memoryContainerHelper, times(2)).bulkIngestData(any(), any(), any());
        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testExecuteMemoryOperations_NoneDecision() {
        MemoryDecision noneDecision = mock(MemoryDecision.class);
        when(noneDecision.getEvent()).thenReturn(MemoryEvent.NONE);
        when(noneDecision.getId()).thenReturn("memory-1");
        when(noneDecision.getText()).thenReturn("No change");

        List<MemoryDecision> decisions = Arrays.asList(noneDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        // Verify that NONE events result in an empty response list (no operations to execute)
        ArgumentCaptor<List<MemoryResult>> resultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(operationsListener).onResponse(resultsCaptor.capture());
        List<MemoryResult> results = resultsCaptor.getValue();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testExecuteMemoryOperations_MixedDecisionsExcludesNone() {
        // Create mixed decisions: ADD, UPDATE, DELETE, and NONE
        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("New fact");

        MemoryDecision updateDecision = mock(MemoryDecision.class);
        when(updateDecision.getEvent()).thenReturn(MemoryEvent.UPDATE);
        when(updateDecision.getId()).thenReturn("memory-2");
        when(updateDecision.getText()).thenReturn("Updated fact");
        when(updateDecision.getOldMemory()).thenReturn("Old fact");

        MemoryDecision deleteDecision = mock(MemoryDecision.class);
        when(deleteDecision.getEvent()).thenReturn(MemoryEvent.DELETE);
        when(deleteDecision.getId()).thenReturn("memory-3");
        when(deleteDecision.getText()).thenReturn("Deleted fact");

        MemoryDecision noneDecision = mock(MemoryDecision.class);
        when(noneDecision.getEvent()).thenReturn(MemoryEvent.NONE);
        when(noneDecision.getId()).thenReturn("memory-4");
        when(noneDecision.getText()).thenReturn("Unchanged fact");

        List<MemoryDecision> decisions = Arrays.asList(addDecision, updateDecision, deleteDecision, noneDecision);
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        BulkItemResponse addItem = mock(BulkItemResponse.class);
        when(addItem.getOpType()).thenReturn(org.opensearch.action.DocWriteRequest.OpType.INDEX);
        when(addItem.isFailed()).thenReturn(false);
        when(addItem.getId()).thenReturn("new-memory-id");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { addItem });

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        // Verify that only ADD, UPDATE, DELETE are included in results (not NONE)
        ArgumentCaptor<List<MemoryResult>> resultsCaptor = ArgumentCaptor.forClass(List.class);
        verify(operationsListener).onResponse(resultsCaptor.capture());
        List<MemoryResult> results = resultsCaptor.getValue();

        // Should have 3 results (ADD, UPDATE, DELETE) but not NONE
        assertEquals(3, results.size());

        // Verify the events in the results
        assertEquals(MemoryEvent.ADD, results.get(0).getEvent());
        assertEquals(MemoryEvent.UPDATE, results.get(1).getEvent());
        assertEquals(MemoryEvent.DELETE, results.get(2).getEvent());
    }

    @Test
    public void testExecuteMemoryOperations_BulkFailure() {
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

        Exception bulkException = new RuntimeException("Bulk operation failed");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onFailure(bulkException);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        verify(memoryContainerHelper).bulkIngestData(any(), any(), any());
        verify(operationsListener).onFailure(bulkException);
    }

    @Test
    public void testExecuteMemoryOperations_WithEmbeddings() {
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
        when(storageConfig.isDisableHistory()).thenReturn(true);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { bulkItemResponse });
        when(bulkItemResponse.getOpType()).thenReturn(org.opensearch.action.DocWriteRequest.OpType.INDEX);
        when(bulkItemResponse.isFailed()).thenReturn(false);
        when(bulkItemResponse.getId()).thenReturn("generated-id-123");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);
    }

    @Test
    public void testExecuteMemoryOperations_HistoryDisabled() {
        // Test that history records are not created when history is disabled
        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("New fact");

        List<MemoryDecision> decisions = Arrays.asList(addDecision);
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(true);
        when(storageConfig.getLongMemoryIndexName()).thenReturn("long-term-index");

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(false);
        when(item.getId()).thenReturn("memory-123");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { item });

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        // Should only call bulkIngestData once (for long-term memory, not history)
        verify(memoryContainerHelper, times(1)).bulkIngestData(any(), any(), any());
    }

    @Test
    public void testExecuteMemoryOperations_UserPreferenceStrategy() {
        // Test that USER_PREFERENCE strategy type maps correctly
        MemoryStrategy userPrefStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.USER_PREFERENCE)
            .enabled(true)
            .id("pref-strategy")
            .build();

        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("User prefers dark mode");

        List<MemoryDecision> decisions = Arrays.asList(addDecision);
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());
        when(input.getOwnerId()).thenReturn("user-123");

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(true);
        when(storageConfig.getLongMemoryIndexName()).thenReturn("long-term-index");

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(false);
        when(item.getId()).thenReturn("memory-123");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { item });

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService
            .executeMemoryOperations(decisions, storageConfig, namespace, user, input, userPrefStrategy, operationsListener);

        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testExecuteMemoryOperations_SummaryStrategy() {
        // Test that SUMMARY strategy type maps correctly
        MemoryStrategy summaryStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SUMMARY)
            .enabled(true)
            .id("summary-strategy")
            .build();

        MemoryDecision addDecision = mock(MemoryDecision.class);
        when(addDecision.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision.getText()).thenReturn("Summary of conversation");

        List<MemoryDecision> decisions = Arrays.asList(addDecision);
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());
        when(input.getOwnerId()).thenReturn("user-123");

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(true);
        when(storageConfig.getLongMemoryIndexName()).thenReturn("long-term-index");

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(false);
        when(item.getId()).thenReturn("memory-123");
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { item });

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService
            .executeMemoryOperations(decisions, storageConfig, namespace, user, input, summaryStrategy, operationsListener);

        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testExecuteMemoryOperations_WithFailedBulkItems() {
        // Test handling of partially failed bulk operations
        MemoryDecision addDecision1 = mock(MemoryDecision.class);
        when(addDecision1.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision1.getText()).thenReturn("Fact 1");

        MemoryDecision addDecision2 = mock(MemoryDecision.class);
        when(addDecision2.getEvent()).thenReturn(MemoryEvent.ADD);
        when(addDecision2.getText()).thenReturn("Fact 2");

        List<MemoryDecision> decisions = Arrays.asList(addDecision1, addDecision2);
        User user = null;
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getAgentId()).thenReturn("agent-123");
        when(input.getTags()).thenReturn(new HashMap<>());
        when(input.getOwnerId()).thenReturn("user-123");

        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);
        when(storageConfig.isDisableHistory()).thenReturn(true);
        when(storageConfig.getLongMemoryIndexName()).thenReturn("long-term-index");

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(true);

        BulkItemResponse successItem = mock(BulkItemResponse.class);
        when(successItem.isFailed()).thenReturn(false);
        when(successItem.getId()).thenReturn("success-id");

        BulkItemResponse failedItem = mock(BulkItemResponse.class);
        when(failedItem.isFailed()).thenReturn(true);
        when(failedItem.getFailureMessage()).thenReturn("Index operation failed");

        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { successItem, failedItem });

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).bulkIngestData(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, storageConfig, namespace, user, input, strategy, operationsListener);

        // Should complete with results despite partial failures
        verify(operationsListener).onResponse(any(List.class));
    }

    @Test
    public void testCreateFactMemoriesFromList_WithEmptyList() {
        List<String> facts = Arrays.asList();
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        String indexName = "memory-index";
        User user = null;
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        memoryOperationsService
            .createFactMemoriesFromList(facts, indexName, input, namespace, user, strategy, indexRequests, memoryInfos, "container-123");

        // Should not create any requests or infos for empty list
        assertTrue(indexRequests.isEmpty());
        assertTrue(memoryInfos.isEmpty());
    }

    @Test
    public void testCreateErrorMemoryHistory_WithMemoryContainerId() {
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getOwnerId()).thenReturn("owner-123");
        when(input.getTags()).thenReturn(Map.of("tag1", "value1"));

        Map<String, String> strategyNamespace = Map.of("session_id", "session-123");
        Exception exception = new RuntimeException("Test error");
        String memoryContainerId = "container-123";

        Map<String, Object> result = memoryOperationsService
            .createErrorMemoryHistory(strategyNamespace, input, exception, memoryContainerId);

        assertEquals(memoryContainerId, result.get("memory_container_id"));
        assertEquals(strategyNamespace, result.get("namespace"));
        assertEquals(Map.of("tag1", "value1"), result.get("tags"));
        assertTrue(result.containsKey("error"));
        assertTrue(result.containsKey("created_time"));
    }

    @Test
    public void testCreateErrorMemoryHistory_WithNullMemoryContainerId() {
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getOwnerId()).thenReturn("owner-123");
        when(input.getTags()).thenReturn(null);

        Exception exception = new RuntimeException("Test error");

        Map<String, Object> result = memoryOperationsService.createErrorMemoryHistory(null, input, exception, null);

        // Should not contain memory_container_id when it's null
        assertTrue(!result.containsKey("memory_container_id"));
        assertTrue(result.containsKey("error"));
        assertTrue(result.containsKey("created_time"));
    }

}
