/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_GENERATING_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_UPDATE_INTERVAL;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class AbstractIndexInsightTaskTests {

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
        task.handleExistingDoc(source, "test-tenant", listener);

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
        task.handleExistingDoc(source, "test-tenant", listener);

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
        task.handleExistingDoc(source, "test-tenant", listener);

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
        task.handleExistingDoc(source, "test-tenant", listener);

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
        task.handleExistingDoc(source, "test-tenant", listener);

        verify(client, times(5)).threadPool();
        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_Success() throws IOException {
        mockFullFlowExecution(sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-tenant", listener);

        verify(listener).onResponse(any(IndexInsight.class));
    }

    @Test
    public void testRunWithPrerequisites_PrerequisiteFailure() {
        // Mock SDK Client get failure
        CompletableFuture<GetDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("Prerequisite failed"));
        when(sdkClient.getDataObjectAsync(any())).thenReturn(failedFuture);

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runWithPrerequisites("test-tenant", listener);

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
        task.runWithPrerequisites("test-tenant", listener);

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
        task.execute("test-tenant", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());
        IndexInsight result = captor.getValue();
        assertEquals("test-index", result.getIndex());
        assertEquals("pattern matched content", result.getContent());
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testGetAgentIdToRun_Success() {
        Client client = mock(Client.class);
        ActionListener<String> actionListener = mock(ActionListener.class);
        String tenantId = "test-tenant";
        String expectedAgentId = "test-agent-id";

        Configuration configuration = Configuration.builder().agentId(expectedAgentId).build();
        MLConfig mlConfig = MLConfig.builder().configuration(configuration).build();
        MLConfigGetResponse response = MLConfigGetResponse.builder().mlConfig(mlConfig).build();

        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(MLConfigGetRequest.class), any(ActionListener.class));

        AbstractIndexInsightTask.getAgentIdToRun(client, tenantId, actionListener);

        verify(actionListener).onResponse(expectedAgentId);
    }

    @Test
    public void testGetAgentIdToRun_Failure() {
        Client client = mock(Client.class);
        ActionListener<String> actionListener = mock(ActionListener.class);
        String tenantId = "test-tenant";
        Exception expectedException = new RuntimeException("Test error");

        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> listener = invocation.getArgument(2);
            listener.onFailure(expectedException);
            return null;
        }).when(client).execute(any(), any(MLConfigGetRequest.class), any(ActionListener.class));

        AbstractIndexInsightTask.getAgentIdToRun(client, tenantId, actionListener);

        verify(actionListener).onFailure(expectedException);
    }

    @Test
    public void testExtractFieldNamesTypes_BasicFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        mappingSource.put("field1", Map.of("type", "text"));
        mappingSource.put("field2", Map.of("type", "keyword"));

        Map<String, String> fieldsToType = new HashMap<>();
        AbstractIndexInsightTask.extractFieldNamesTypes(mappingSource, fieldsToType, "", false);

        assertEquals("text", fieldsToType.get("field1"));
        assertEquals("keyword", fieldsToType.get("field2"));
        assertEquals(2, fieldsToType.size());
    }

    @Test
    public void testExtractFieldNamesTypes_NestedProperties() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> nestedField = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        properties.put("other_field", Map.of("type", "text"));
        nestedField.put("properties", properties);
        mappingSource.put("nested", nestedField);

        Map<String, String> fieldsToType = new HashMap<>();
        AbstractIndexInsightTask.extractFieldNamesTypes(mappingSource, fieldsToType, "", false);

        assertEquals("text", fieldsToType.get("nested.other_field"));
        assertFalse(fieldsToType.containsKey("nested.fields.keyword"));
    }

    @Test
    public void testExtractFieldNamesTypes_SkipAliasAndObject() {
        Map<String, Object> mappingSource = new HashMap<>();
        mappingSource.put("alias_field", Map.of("type", "alias"));
        mappingSource.put("object_field", Map.of("type", "object"));
        mappingSource.put("text_field", Map.of("type", "text"));

        Map<String, String> fieldsToType = new HashMap<>();
        AbstractIndexInsightTask.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertFalse(fieldsToType.containsKey("alias_field"));
        assertFalse(fieldsToType.containsKey("object_field"));
        assertEquals("text", fieldsToType.get("text_field"));
    }

    @Test
    public void testExtractFieldNamesTypes_WithFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> textField = new HashMap<>();
        textField.put("type", "text");
        textField.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        mappingSource.put("text_field", textField);

        Map<String, String> fieldsToType = new HashMap<>();
        AbstractIndexInsightTask.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertEquals("text", fieldsToType.get("text_field"));
        assertEquals("keyword", fieldsToType.get("text_field.keyword"));
    }

    @Test
    public void testExtractFieldNamesTypes_ExcludeFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> textField = new HashMap<>();
        textField.put("type", "text");
        textField.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        mappingSource.put("text_field", textField);

        Map<String, String> fieldsToType = new HashMap<>();
        AbstractIndexInsightTask.extractFieldNamesTypes(mappingSource, fieldsToType, "", false);

        assertEquals("text", fieldsToType.get("text_field"));
        assertFalse(fieldsToType.containsKey("text_field.keyword"));
    }

    @Test
    public void testCallLLMWithAgent_SuccessWithJson() {
        Client client = mock(Client.class);
        ActionListener<String> listener = mock(ActionListener.class);
        String agentId = "test-agent";
        String prompt = "test prompt";
        String sourceIndex = "test-index";
        String jsonResponse = "{\"response\":\"parsed response\"}";

        ModelTensor modelTensor = mock(ModelTensor.class);
        when(modelTensor.getResult()).thenReturn(jsonResponse);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Collections.singletonList(modelTensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Collections.singletonList(modelTensors));

        MLExecuteTaskResponse response = mock(MLExecuteTaskResponse.class);
        when(response.getOutput()).thenReturn(output);

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> responseListener = invocation.getArgument(2);
            responseListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(MLExecuteTaskRequest.class), any(ActionListener.class));

        AbstractIndexInsightTask.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onResponse("parsed response");
    }

    @Test
    public void testCallLLMWithAgent_SuccessWithPlainText() {
        Client client = mock(Client.class);
        ActionListener<String> listener = mock(ActionListener.class);
        String agentId = "test-agent";
        String prompt = "test prompt";
        String sourceIndex = "test-index";
        String plainResponse = "plain text response";

        ModelTensor modelTensor = mock(ModelTensor.class);
        when(modelTensor.getResult()).thenReturn(plainResponse);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Collections.singletonList(modelTensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Collections.singletonList(modelTensors));

        MLExecuteTaskResponse response = mock(MLExecuteTaskResponse.class);
        when(response.getOutput()).thenReturn(output);

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> responseListener = invocation.getArgument(2);
            responseListener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(MLExecuteTaskRequest.class), any(ActionListener.class));

        AbstractIndexInsightTask.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onResponse(plainResponse);
    }

    @Test
    public void testCallLLMWithAgent_Failure() {
        Client client = mock(Client.class);
        ActionListener<String> listener = mock(ActionListener.class);
        String agentId = "test-agent";
        String prompt = "test prompt";
        String sourceIndex = "test-index";
        Exception expectedException = new RuntimeException("Test error");

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> responseListener = invocation.getArgument(2);
            responseListener.onFailure(expectedException);
            return null;
        }).when(client).execute(any(), any(MLExecuteTaskRequest.class), any(ActionListener.class));

        AbstractIndexInsightTask.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onFailure(expectedException);
    }

    // Test implementation with prerequisites
    private static class TestIndexInsightTask extends AbstractIndexInsightTask {
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
        public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
            saveResult("main task result", tenantId, listener);
        }

        @Override
        public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
            return new SimpleTestTask(client, sdkClient);
        }
    }

    // Simple task without prerequisites
    private static class SimpleTestTask extends AbstractIndexInsightTask {
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
        public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
            saveResult("prerequisite result", tenantId, listener);
        }

        @Override
        public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
            return new SimpleTestTask(client, sdkClient);
        }
    }
}
