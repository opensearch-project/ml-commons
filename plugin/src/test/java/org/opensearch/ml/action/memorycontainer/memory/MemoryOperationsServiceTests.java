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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
import org.opensearch.transport.client.Client;

public class MemoryOperationsServiceTests {

    @Mock
    private Client client;

    @Mock
    private MemoryEmbeddingHelper memoryEmbeddingHelper;

    @Mock
    private ActionListener<List<MemoryResult>> operationsListener;

    @Mock
    private ActionListener<MLAddMemoriesResponse> responseListener;

    private MemoryOperationsService memoryOperationsService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memoryOperationsService = new MemoryOperationsService(client, memoryEmbeddingHelper);
    }

    @Test
    public void testExecuteMemoryOperations_EmptyDecisions() {
        List<MemoryDecision> decisions = Arrays.asList();
        String indexName = "memory-index";
        String sessionId = "session-123";
        User user = null; // User is final, use null instead of mock
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

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

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.isSemanticStorageEnabled()).thenReturn(false);

        // Mock bulk response
        BulkResponse bulkResponse = mock(BulkResponse.class);
        BulkItemResponse bulkItemResponse = mock(BulkItemResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
        when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] { bulkItemResponse });
        when(bulkItemResponse.isFailed()).thenReturn(false);
        when(bulkItemResponse.getId()).thenReturn("generated-id-123");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

        verify(client).bulk(any(), any());
    }

    @Test
    public void testBulkIndexMemoriesWithResults_EmptyRequests() {
        List<IndexRequest> indexRequests = Arrays.asList();
        List<MemoryInfo> memoryInfos = Arrays.asList();
        String sessionId = "session-123";
        String indexName = "memory-index";

        memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, responseListener);

        verify(responseListener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testBulkIndexMemoriesWithResults_SuccessfulIndexing() {
        IndexRequest indexRequest = mock(IndexRequest.class);
        List<IndexRequest> indexRequests = Arrays.asList(indexRequest);

        MemoryInfo memoryInfo = new MemoryInfo(null, "Test content", null, true);
        List<MemoryInfo> memoryInfos = Arrays.asList(memoryInfo);

        String sessionId = "session-123";
        String indexName = "memory-index";

        // Mock index response
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("generated-id-123");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, responseListener);

        verify(client).index(any(), any());
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

        memoryOperationsService.createFactMemoriesFromList(facts, input, indexName, sessionId, user, now, indexRequests, memoryInfos);

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
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.isSemanticStorageEnabled()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
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
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.isSemanticStorageEnabled()).thenReturn(false);

        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);
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
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

        verify(operationsListener).onResponse(any(List.class));
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
        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);

        Exception bulkException = new RuntimeException("Bulk operation failed");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onFailure(bulkException);
            return null;
        }).when(client).bulk(any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

        verify(client).bulk(any(), any());
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

        MemoryStorageConfig storageConfig = mock(MemoryStorageConfig.class);
        when(storageConfig.isSemanticStorageEnabled()).thenReturn(true);

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

        List<Object> embeddings = Arrays.asList(new float[] { 0.1f, 0.2f, 0.3f });
        doAnswer(invocation -> {
            ActionListener<List<Object>> listener = invocation.getArgument(2);
            listener.onResponse(embeddings);
            return null;
        }).when(memoryEmbeddingHelper).generateEmbeddingsForMultipleTexts(any(), any(), any());

        memoryOperationsService.executeMemoryOperations(decisions, indexName, sessionId, user, input, storageConfig, operationsListener);

        verify(memoryEmbeddingHelper).generateEmbeddingsForMultipleTexts(any(), any(), any());
    }

    @Test
    public void testBulkIndexMemoriesWithResults_IndexingFailure() {
        IndexRequest indexRequest = mock(IndexRequest.class);
        List<IndexRequest> indexRequests = Arrays.asList(indexRequest);

        MemoryInfo memoryInfo = new MemoryInfo(null, "Test content", null, true);
        List<MemoryInfo> memoryInfos = Arrays.asList(memoryInfo);

        String sessionId = "session-123";
        String indexName = "memory-index";

        Exception indexException = new RuntimeException("Index failed");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(indexException);
            return null;
        }).when(client).index(any(), any());

        memoryOperationsService.bulkIndexMemoriesWithResults(indexRequests, memoryInfos, sessionId, indexName, responseListener);

        verify(client).index(any(), any());
        verify(responseListener).onFailure(indexException);
    }
}
