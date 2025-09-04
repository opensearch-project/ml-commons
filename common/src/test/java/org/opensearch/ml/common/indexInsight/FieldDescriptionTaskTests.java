/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockGetSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLConfigFailure;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLConfigSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLExecuteFailure;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLExecuteSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockUpdateSuccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class FieldDescriptionTaskTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Client client;
    private SdkClient sdkClient;
    private FieldDescriptionTask task;
    private ActionListener<IndexInsight> listener;
    private ThreadContext threadContext;
    private ThreadPool threadPool;

    @Before
    public void setUp() {
        client = mock(Client.class);
        sdkClient = mock(SdkClient.class);
        task = new FieldDescriptionTask("test-index", client, sdkClient);
        listener = mock(ActionListener.class);
        threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testGetTaskType() {
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, task.getTaskType());
    }

    @Test
    public void testGetSourceIndex() {
        assertEquals("test-index", task.getSourceIndex());
    }

    @Test
    public void testGetPrerequisites() {
        List<MLIndexInsightType> prerequisites = task.getPrerequisites();
        assertEquals(1, prerequisites.size());
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, prerequisites.get(0));
    }

    @Test
    public void testGetClient() {
        assertEquals(client, task.getClient());
    }

    @Test
    public void testCreatePrerequisiteTask_CorrectType() {
        IndexInsightTask prerequisiteTask = task.createPrerequisiteTask(MLIndexInsightType.STATISTICAL_DATA);

        assertTrue(prerequisiteTask instanceof StatisticalDataTask);
        assertEquals("test-index", prerequisiteTask.getSourceIndex());
    }

    @Test
    public void testCreatePrerequisiteTask_UnsupportedType() {
        MLIndexInsightType unsupportedType = MLIndexInsightType.LOG_RELATED_INDEX_CHECK;
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported prerequisite type: " + unsupportedType);
        task.createPrerequisiteTask(unsupportedType);
    }

    @Test
    public void testBatchProcessing_LargeFieldCount() {
        StringBuilder mappingBuilder = new StringBuilder("{\"important_column_and_distribution\": {");
        for (int i = 0; i < 66; i++) {
            if (i > 0)
                mappingBuilder.append(",");
            mappingBuilder.append("\"field").append(i).append("\": {\"type\": \"text\"}");
        }
        mappingBuilder.append("}}");

        mockGetSuccess(sdkClient, mappingBuilder.toString());
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        verify(client, times(2)).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    @Test
    public void testRunTask_InvalidJsonContent() {
        String invalidStatisticalContent = "invalid json content";

        mockGetSuccess(sdkClient, invalidStatisticalContent);
        mockUpdateSuccess(sdkClient);
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No data distribution found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_MLConfigRetrievalFailure() {
        String statisticalContent = "{\"mapping\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetSuccess(sdkClient, statisticalContent);
        mockMLConfigFailure(client, "Config not found");

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> captor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(captor.capture());

        IllegalStateException exception = captor.getValue();
        assertEquals("Config not found", exception.getMessage());
    }

    @Test
    public void testRunTask_MLExecuteFailure() {
        String statisticalContent = "{\"important_column_and_distribution\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetSuccess(sdkClient, statisticalContent);
        mockUpdateSuccess(sdkClient);
        mockMLConfigSuccess(client);
        mockMLExecuteFailure(client, "ML execution failed");

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertEquals("Batch processing failed", exception.getMessage());
    }

    @Test
    public void testRunTask_EmptyMappingSource() {
        String statisticalContentWithEmptyMapping = "{\"mapping\": {}}";

        mockGetSuccess(sdkClient, statisticalContentWithEmptyMapping);
        mockUpdateSuccess(sdkClient);
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No data distribution found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_NullMappingSource() {
        String statisticalContentWithNullMapping = "{\"other_field\": \"value\"}";

        mockGetSuccess(sdkClient, statisticalContentWithNullMapping);
        mockUpdateSuccess(sdkClient);
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No data distribution found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_EmptyFieldsList() {
        String statisticalContentWithNoTypeFields = "{" + "\"important_column_and_distribution\": {" + "}}";

        mockGetSuccess(sdkClient, statisticalContentWithNoTypeFields);
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        task.runTask("storage-index", "tenant-id", listener);
        ArgumentCaptor<IndexInsight> insightCaptor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(insightCaptor.capture());

        IndexInsight insight = insightCaptor.getValue();
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, insight.getTaskType());
        assertEquals("", insight.getContent());

    }

    @Test
    public void testRunTask_SuccessfulFieldDescriptionGeneration() {
        String statisticalContent = "{"
            + "\"important_column_and_distribution\": {"
            + "\"user_name\": {\"type\": \"text\"},"
            + "\"user_age\": {\"type\": \"integer\"}"
            + "}"
            + "}";

        mockGetSuccess(sdkClient, statisticalContent);
        mockMLConfigSuccess(client);
        mockMLExecuteSuccess(client, "user_name: name of the user\nuser_age: age of the user in years");
        mockUpdateSuccess(sdkClient);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IndexInsight> insightCaptor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(insightCaptor.capture());

        IndexInsight insight = insightCaptor.getValue();
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, insight.getTaskType());
        assertTrue(insight.getContent().contains("\"user_name\":\"name of the user\""));
        assertTrue(insight.getContent().contains("\"user_age\":\"age of the user in years\""));
    }

    @Test
    public void testHandlePatternResult_FilterFields() {
        Map<String, Object> patternSource = new HashMap<>();
        patternSource.put(IndexInsight.CONTENT_FIELD, "{\"field1\": \"desc1\", \"field2\": \"desc2\"}");
        patternSource.put(IndexInsight.LAST_UPDATE_FIELD, System.currentTimeMillis());

        // Mock mappings with only field1 present
        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesClient = mock(IndicesAdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesClient);

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            GetMappingsResponse response = mock(GetMappingsResponse.class);
            MappingMetadata metadata = mock(MappingMetadata.class);
            when(metadata.getSourceAsMap()).thenReturn(Map.of("properties", Map.of("field1", Map.of("type", "text"))));
            when(response.getMappings()).thenReturn(Map.of("test-index", metadata));
            listener.onResponse(response);
            return null;
        }).when(indicesClient).getMappings(any(), any());

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.handlePatternResult(patternSource, "storage-index", "tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals("test-index", result.getIndex());
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, result.getTaskType());
        assertTrue(result.getContent().contains("field1"));
        assertTrue(!result.getContent().contains("field2"));
    }
}
