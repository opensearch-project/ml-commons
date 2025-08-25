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

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.transport.client.Client;

public class FieldDescriptionTaskTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Client client;
    private FieldDescriptionTask task;
    private ActionListener<IndexInsight> listener;

    @Before
    public void setUp() {
        client = mock(Client.class);
        task = new FieldDescriptionTask("test-index", client);
        listener = mock(ActionListener.class);
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
        StringBuilder mappingBuilder = new StringBuilder("{\"mapping\": {");
        for (int i = 0; i < 66; i++) {
            if (i > 0)
                mappingBuilder.append(",");
            mappingBuilder.append("\"field").append(i).append("\": {\"type\": \"text\"}");
        }
        mappingBuilder.append("}}");

        mockGetSuccess(client, mappingBuilder.toString());
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        verify(client, times(2)).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    @Test
    public void testRunTask_InvalidJsonContent() {
        String invalidStatisticalContent = "invalid json content";

        mockGetSuccess(client, invalidStatisticalContent);
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No mapping properties found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_MLConfigRetrievalFailure() {
        String statisticalContent = "{\"mapping\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetSuccess(client, statisticalContent);
        mockMLConfigFailure(client, "Config not found");

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> captor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(captor.capture());

        IllegalStateException exception = captor.getValue();
        assertEquals("Config not found", exception.getMessage());
    }

    @Test
    public void testRunTask_MLExecuteFailure() {
        String statisticalContent = "{\"mapping\": {\"field1\": {\"type\": \"text\"}}}";

        mockGetSuccess(client, statisticalContent);
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

        mockGetSuccess(client, statisticalContentWithEmptyMapping);
        mockMLConfigSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("No mapping properties found for index: test-index", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testRunTask_NullMappingSource() {
        String statisticalContentWithNullMapping = "{\"other_field\": \"value\"}";

        mockGetSuccess(client, statisticalContentWithNullMapping);
        mockMLConfigSuccess(client);

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

        mockGetSuccess(client, statisticalContentWithNoTypeFields);
        mockMLConfigSuccess(client);
        mockUpdateSuccess(client);

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

        mockGetSuccess(client, statisticalContent);
        mockMLConfigSuccess(client);
        mockMLExecuteSuccess(client, "user_name: name of the user\nuser_age: age of the user in years");
        mockUpdateSuccess(client);

        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IndexInsight> insightCaptor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(insightCaptor.capture());

        IndexInsight insight = insightCaptor.getValue();
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, insight.getTaskType());
        assertTrue(insight.getContent().contains("\"user_name\":\"name of the user\""));
        assertTrue(insight.getContent().contains("\"user_age\":\"age of the user in years\""));
    }
}
