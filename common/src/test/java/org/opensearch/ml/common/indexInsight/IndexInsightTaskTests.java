/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_GENERATING_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_UPDATE_INTERVAL;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class IndexInsightTaskTests {

    private Client client;
    private SdkClient sdkClient;
    private TestIndexInsightTask task;
    private ThreadContext threadContext;
    private ThreadPool threadPool;

    @Before
    public void setUp() {
        client = mock(Client.class);
        sdkClient = mock(SdkClient.class);
        task = new TestIndexInsightTask(client, sdkClient);
        threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    private void mockSearchWithPatternHit(SdkClient sdkClient, SearchHit[] hits) {
        SearchHits searchHits = new SearchHits(hits, new TotalHits(hits.length, Relation.EQUAL_TO), 1.0f);
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);

        SearchDataObjectResponse sdkResponse = mock(SearchDataObjectResponse.class);
        when(sdkResponse.searchResponse()).thenReturn(searchResponse);

        CompletableFuture<SearchDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(future);
    }

    private void mockFullFlowExecution(SdkClient sdkClient) throws IOException {
        mockGetFailToGet(sdkClient, "");
        mockSearchSuccess(sdkClient);
        mockUpdateSuccess(sdkClient);
    }

    @Test
    public void testGenerateDocId() {
        String docId = task.generateDocId();
        assertEquals(64, docId.length());
    }

    @Test
    public void testGetInsightContentFromContainer() {
        mockGetSuccess(sdkClient, "{\"key\": \"value\"}");
        ActionListener<Map<String, Object>> listener = mock(ActionListener.class);
        task.getInsightContentFromContainer(MLIndexInsightType.STATISTICAL_DATA, "", listener);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass((Class) Map.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("value", captor.getValue().get("key"));
    }

    @Test
    public void testGetInsightContentFromContainer_NotExists() {
        mockGetSuccess(sdkClient, "");
        ActionListener<Map<String, Object>> listener = mock(ActionListener.class);
        task.getInsightContentFromContainer(MLIndexInsightType.STATISTICAL_DATA, "", listener);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass((Class) Map.class);
        verify(listener).onResponse(captor.capture());
        assertNull(captor.getValue());
    }

    @Test
    public void testSaveResult() {
        mockUpdateSuccess(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.saveResult("test content", "test-tenant", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("test content", captor.getValue().getContent());
        assertEquals(IndexInsightTaskStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    public void testSaveResult_Failure() {
        // Mock SDK Client failure
        CompletableFuture<PutDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("SDK Client update failed"));
        when(sdkClient.putDataObjectAsync(any())).thenReturn(failedFuture);

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.saveResult("test content", "test-storage", listener);

        verify(listener).onFailure(any());
    }

    @Test
    public void testSaveFailedStatus() {
        mockUpdateSuccess(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.saveFailedStatus("", new RuntimeException("test error"), listener);

        verify(sdkClient).putDataObjectAsync(any());
    }

    @Test
    public void testHandleExistingDoc_Completed_NoUpdate() {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.COMPLETED.toString());
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - 3600);
        source.put(IndexInsight.INDEX_NAME_FIELD, "test-index");
        source.put(IndexInsight.TASK_TYPE_FIELD, "STATISTICAL_DATA");
        source.put(IndexInsight.CONTENT_FIELD, "test content");

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("test content", captor.getValue().getContent());
    }

    @Test
    public void testHandleExistingDoc_Completed_NeedUpdate() throws IOException {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.COMPLETED.toString());
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - INDEX_INSIGHT_UPDATE_INTERVAL - 3600);
        source.put(IndexInsight.INDEX_NAME_FIELD, "test-index");
        source.put(IndexInsight.TASK_TYPE_FIELD, "STATISTICAL_DATA");
        source.put(IndexInsight.CONTENT_FIELD, "test content");

        mockFullFlowExecution(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        // Verify prerequisite task execution: 5 threadPool calls indicate both prerequisite and main task ran
        verify(client, times(5)).threadPool();
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testHandleExistingDoc_Generating_NotTimeout() {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, "GENERATING");
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli());

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(listener).onFailure(captor.capture());
        assertEquals("Index insight is being generated, please wait...", captor.getValue().getMessage());
        assertEquals(RestStatus.TOO_MANY_REQUESTS, captor.getValue().status());
    }

    @Test
    public void testHandleExistingDoc_Generating_Timeout() throws IOException {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, "GENERATING");
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - INDEX_INSIGHT_GENERATING_TIMEOUT - 3600);

        mockFullFlowExecution(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        verify(client, times(5)).threadPool();
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testHandleExistingDoc_Failed_Retry() throws IOException {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, "FAILED");
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli());

        mockFullFlowExecution(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        verify(client, times(5)).threadPool();
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_Success() throws IOException {
        mockFullFlowExecution(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_PrerequisiteFailure() {
        // Mock SDK Client get failure
        CompletableFuture<GetDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("Prerequisite failed"));
        when(sdkClient.getDataObjectAsync(any())).thenReturn(failedFuture);

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

        verify(listener).onFailure(any());
    }

    @Test
    public void testRunWithPrerequisites_PrerequisiteCompleted() {
        // Mock prerequisite completed data
        Map<String, Object> prereqSource = new HashMap<>();
        prereqSource.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.COMPLETED.toString());
        prereqSource.put(IndexInsight.CONTENT_FIELD, "prerequisite content");
        prereqSource.put(IndexInsight.INDEX_NAME_FIELD, "test-index");
        prereqSource.put(IndexInsight.TASK_TYPE_FIELD, "STATISTICAL_DATA");
        prereqSource.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - 3600);

        mockGetSuccess(sdkClient, prereqSource);
        mockUpdateSuccess(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

        verify(client, times(2)).threadPool();
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testExecute_WithPatternMatch_Success() throws IOException {
        mockGetFailToGet(sdkClient, "");

        XContentBuilder sourceContent = XContentBuilder
            .builder(XContentType.JSON.xContent())
            .startObject()
            .field(IndexInsight.INDEX_NAME_FIELD, "test-*")
            .field(IndexInsight.TASK_TYPE_FIELD, "FIELD_DESCRIPTION")
            .field(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.COMPLETED.toString())
            .field(IndexInsight.CONTENT_FIELD, "pattern matched content")
            .field(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - 3600)
            .endObject();

        SearchHit patternHit = new SearchHit(0, "pattern-doc", Map.of(), Map.of());
        patternHit.sourceRef(BytesReference.bytes(sourceContent));

        SearchHit[] hits = new SearchHit[] { patternHit };
        mockSearchWithPatternHit(sdkClient, hits);

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.execute("test-storage", "test-tenant", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());
        IndexInsight result = captor.getValue();
        assertEquals("test-index", result.getIndex());
        assertEquals("pattern matched content", result.getContent());
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    // Test implementation with prerequisites
    private static class TestIndexInsightTask implements IndexInsightTask {
        private final Client client;
        private final SdkClient sdkClient;

        TestIndexInsightTask(Client client, SdkClient sdkClient) {
            this.client = client;
            this.sdkClient = sdkClient;
        }

        @Override
        public MLIndexInsightType getTaskType() {
            return MLIndexInsightType.FIELD_DESCRIPTION;
        }

        @Override
        public String getSourceIndex() {
            return "test-index";
        }

        @Override
        public List<MLIndexInsightType> getPrerequisites() {
            return Arrays.asList(MLIndexInsightType.STATISTICAL_DATA);
        }

        @Override
        public Client getClient() {
            return client;
        }

        @Override
        public SdkClient getSdkClient() {
            return sdkClient;
        }

        @Override
        public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
            saveResult("main task result", storageIndex, listener);
        }

        @Override
        public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
            return new SimpleTestTask(client, sdkClient);
        }
    }

    // Simple task without prerequisites
    private static class SimpleTestTask implements IndexInsightTask {
        private final Client client;
        private final SdkClient sdkClient;

        SimpleTestTask(Client client, SdkClient sdkClient) {
            this.client = client;
            this.sdkClient = sdkClient;
        }

        @Override
        public MLIndexInsightType getTaskType() {
            return MLIndexInsightType.STATISTICAL_DATA;
        }

        @Override
        public String getSourceIndex() {
            return "test-index";
        }

        @Override
        public List<MLIndexInsightType> getPrerequisites() {
            return Arrays.asList();
        }

        @Override
        public Client getClient() {
            return client;
        }

        @Override
        public SdkClient getSdkClient() {
            return sdkClient;
        }

        @Override
        public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
            saveResult("prerequisite result", storageIndex, listener);
        }

        @Override
        public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
            return new SimpleTestTask(client, sdkClient);
        }
    }
}
