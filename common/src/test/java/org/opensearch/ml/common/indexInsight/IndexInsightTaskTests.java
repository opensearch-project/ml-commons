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
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockGetFailToGet;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockGetSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockSearchSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockUpdateSuccess;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.remote.metadata.client.SdkClient;
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

    private void mockClientGetResponse(Client client, GetResponse response) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
    }

    private void mockClientUpdateResponse(Client client, UpdateResponse response) {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).update(any(), any());
    }

    private void mockClientUpdateFailure(Client client, Exception exception) {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(exception);
            return null;
        }).when(client).update(any(), any());
    }

    private void mockFullFlowExecution(Client client, SdkClient sdkClient) throws IOException {
        // Mock update responses
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(UpdateResponse.class));
            return null;
        }).when(client).update(any(), any());

        mockUpdateSuccess(sdkClient);

        // Mock get request - return not exists to trigger new prerequisite execution
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        mockGetSuccess(sdkClient, "");

        // Mock search request - return no hits to avoid pattern matching
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchHits noHits = new SearchHits(new SearchHit[0], new TotalHits(0, Relation.EQUAL_TO), 1.0f);
            SearchResponse searchResponse = mock(SearchResponse.class);
            when(searchResponse.getHits()).thenReturn(noHits);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
        mockSearchSuccess(sdkClient);
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
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        mockClientUpdateResponse(client, updateResponse);
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
        mockClientUpdateFailure(client, new Exception("Update failed"));

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.saveResult("test content", "test-storage", listener);

        verify(listener).onFailure(any());
    }

    @Test
    public void testSaveFailedStatus() {
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        mockClientUpdateResponse(client, updateResponse);
        mockUpdateSuccess(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.saveFailedStatus("", new RuntimeException("test error"), listener);

        verify(sdkClient).putDataObjectAsync(any());
    }

    @Test
    public void testHandleExistingDoc_Completed_NoUpdate() {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, "COMPLETED");
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
        source.put(IndexInsight.STATUS_FIELD, "COMPLETED");
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - INDEX_INSIGHT_UPDATE_INTERVAL - 3600);
        source.put(IndexInsight.INDEX_NAME_FIELD, "test-index");
        source.put(IndexInsight.TASK_TYPE_FIELD, "STATISTICAL_DATA");
        source.put(IndexInsight.CONTENT_FIELD, "test content");

        mockFullFlowExecution(client, sdkClient);
        mockGetFailToGet(sdkClient, "");
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        // Should call update 4 times: beginGeneration + prerequisite beginGeneration + prerequisite saveResult + main task saveResult
        //verify(client, times(4)).update(any(), any());
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

        mockFullFlowExecution(client, sdkClient);
        mockGetFailToGet(sdkClient, "");
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        //verify(client, times(4)).update(any(), any());
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testHandleExistingDoc_Failed_Retry() throws IOException {
        Map<String, Object> source = new HashMap<>();
        source.put(IndexInsight.STATUS_FIELD, "FAILED");
        source.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli());

        mockFullFlowExecution(client, sdkClient);
        mockGetFailToGet(sdkClient, "test-storage");
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handleExistingDoc(source, "test-storage", "test-tenant", listener);

        //verify(client, times(4)).update(any(), any());
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_Success() throws IOException {
        mockFullFlowExecution(client, sdkClient);
        mockGetFailToGet(sdkClient, "");
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_PrerequisiteFailure() {
        mockClientUpdateResponse(client, mock(UpdateResponse.class));

        // Mock prerequisite task execution failure
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Prerequisite failed"));
            return null;
        }).when(client).get(any(), any());

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

        verify(listener).onFailure(any());
    }

    @Test
    public void testRunWithPrerequisites_PrerequisiteCompleted() {
        mockClientUpdateResponse(client, mock(UpdateResponse.class));

        // Mock prerequisite already completed with all required fields
        GetResponse prereqResponse = mock(GetResponse.class);
        Map<String, Object> prereqSource = new HashMap<>();
        prereqSource.put(IndexInsight.STATUS_FIELD, "COMPLETED");
        prereqSource.put(IndexInsight.CONTENT_FIELD, "prerequisite content");
        prereqSource.put(IndexInsight.INDEX_NAME_FIELD, "test-index");
        prereqSource.put(IndexInsight.TASK_TYPE_FIELD, "STATISTICAL_DATA");
        prereqSource.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli() - 3600);
        when(prereqResponse.isExists()).thenReturn(true);
        when(prereqResponse.getSourceAsMap()).thenReturn(prereqSource);
        mockClientGetResponse(client, prereqResponse);
        mockGetSuccess(sdkClient, prereqSource);
        mockUpdateSuccess(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-storage", "test-tenant", listener);

       // verify(client, times(1)).update(any(), any());
        verify(listener).onResponse(any(IndexInsight.class));
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
        private  final SdkClient sdkClient;

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
