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

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.transport.client.Client;

public class FieldDescriptionTaskTests {

    private Client client;
    private FieldDescriptionTask task;
    private ActionListener<IndexInsight> listener;

    @Before
    public void setUp() {
        client = mock(Client.class);
        task = new FieldDescriptionTask("test-index", client);
        listener = mock(ActionListener.class);
    }

    private void mockGetResponse(String content) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> getListener = invocation.getArgument(1);
            GetResponse mockResponse = mock(GetResponse.class);
            when(mockResponse.isExists()).thenReturn(true);
            Map<String, Object> sourceMap = Map.of("content", content);
            when(mockResponse.getSourceAsMap()).thenReturn(sourceMap);
            getListener.onResponse(mockResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());
    }

    private void mockMLConfigSuccess() {
        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> configListener = invocation.getArgument(2);
            MLConfig config = MLConfig.builder().type("test").configuration(Configuration.builder().agentId("test-agent").build()).build();
            configListener.onResponse(new MLConfigGetResponse(config));
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(MLConfigGetRequest.class), any(ActionListener.class));
    }

    private void mockMLConfigFailure(String errorMessage) {
        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> configListener = invocation.getArgument(2);
            configListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(MLConfigGetRequest.class), any(ActionListener.class));
    }

    private void mockMLExecuteSuccess(String response) {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> executeListener = invocation.getArgument(2);
            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            executeListener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    private void mockMLExecuteFailure(String errorMessage) {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> executeListener = invocation.getArgument(2);
            executeListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    private void mockUpdateSuccess() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
            updateListener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any(ActionListener.class));
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

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePrerequisiteTask_UnsupportedType() {
        task.createPrerequisiteTask(MLIndexInsightType.LOG_RELATED_INDEX_CHECK);
    }

    @Test
    public void testBatchProcessing_LargeFieldCount() {
        StringBuilder mappingBuilder = new StringBuilder("{\"mapping\": {");
        for (int i = 0; i < 66; i++) {
            if (i > 0)
                mappingBuilder.append(",");
            mappingBuilder.append("\"field").append(i).append("\": {\"type\": \"text\"}");
        }
        mappingBuilder.append("}}");

        mockGetResponse(mappingBuilder.toString());
        mockMLConfigSuccess();

        task.runTask("storage-index", "tenant-id", listener);

        verify(client, times(2)).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    @Test
    public void testRunTask_InvalidJsonContent() {
        String invalidStatisticalContent = "invalid json content";

        mockGetResponse(invalidStatisticalContent);
        mockMLConfigSuccess();

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(
            exceptionCaptor.getValue().getMessage().contains("Failed to parse statistic content for field description task to get mappings")
        );
    }

    @Test
    public void testRunTask_MLConfigRetrievalFailure() {
        String statisticalContent = "{\"mapping\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetResponse(statisticalContent);
        mockMLConfigFailure("Config not found");

        task.runTask("storage-index", "tenant-id", listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testRunTask_MLExecuteFailure() {
        String statisticalContent = "{\"mapping\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetResponse(statisticalContent);
        mockMLConfigSuccess();
        mockMLExecuteFailure("ML execution failed");

        task.runTask("storage-index", "tenant-id", listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testRunTask_EmptyMappingSource() {
        String statisticalContentWithEmptyMapping = "{\"mapping\": {}}";

        mockGetResponse(statisticalContentWithEmptyMapping);
        mockMLConfigSuccess();

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No mapping properties found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_NullMappingSource() {
        String statisticalContentWithNullMapping = "{\"other_field\": \"value\"}";

        mockGetResponse(statisticalContentWithNullMapping);
        mockMLConfigSuccess();

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No mapping properties found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_EmptyFieldsList() {
        String statisticalContentWithNoTypeFields = "{"
            + "\"mapping\": {"
            + "\"nested_object\": {"
            + "\"properties\": {"
            + "\"inner_object\": {\"properties\": {}}"
            + "}"
            + "}"
            + "}"
            + "}";

        mockGetResponse(statisticalContentWithNoTypeFields);
        mockMLConfigSuccess();
        mockUpdateSuccess();

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
            + "\"mapping\": {"
            + "\"user_name\": {\"type\": \"text\"},"
            + "\"user_age\": {\"type\": \"integer\"}"
            + "}"
            + "}";

        mockGetResponse(statisticalContent);
        mockMLConfigSuccess();
        mockMLExecuteSuccess("user_name: name of the user\nuser_age: age of the user in years");
        mockUpdateSuccess();

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IndexInsight> insightCaptor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(insightCaptor.capture());

        IndexInsight insight = insightCaptor.getValue();
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, insight.getTaskType());
        assertTrue(insight.getContent().contains("\"user_name\":\"name of the user\""));
        assertTrue(insight.getContent().contains("\"user_age\":\"age of the user in years\""));
    }
}
