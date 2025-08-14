/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.transport.client.Client;

public class IndexInsightUtilsTests {

    @Test
    public void testGetAgentIdToRunSuccess() {
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

        IndexInsightUtils.getAgentIdToRun(client, tenantId, actionListener);

        verify(actionListener).onResponse(expectedAgentId);
    }

    @Test
    public void testGetAgentIdToRunFailure() {
        Client client = mock(Client.class);
        ActionListener<String> actionListener = mock(ActionListener.class);
        String tenantId = "test-tenant";
        Exception expectedException = new RuntimeException("Test error");

        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> listener = invocation.getArgument(2);
            listener.onFailure(expectedException);
            return null;
        }).when(client).execute(any(), any(MLConfigGetRequest.class), any(ActionListener.class));

        IndexInsightUtils.getAgentIdToRun(client, tenantId, actionListener);

        verify(actionListener).onFailure(expectedException);
    }

    @Test
    public void testExtractFieldNamesTypesBasicFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        mappingSource.put("field1", Map.of("type", "text"));
        mappingSource.put("field2", Map.of("type", "keyword"));

        Map<String, String> fieldsToType = new HashMap<>();
        IndexInsightUtils.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertEquals("text", fieldsToType.get("field1"));
        assertEquals("keyword", fieldsToType.get("field2"));
        assertEquals(2, fieldsToType.size());
    }

    @Test
    public void testExtractFieldNamesTypesNestedProperties() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> nestedField = new HashMap<>();
        nestedField.put("properties", Map.of("subfield", Map.of("type", "text")));
        mappingSource.put("nested", nestedField);

        Map<String, String> fieldsToType = new HashMap<>();
        IndexInsightUtils.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertEquals("text", fieldsToType.get("nested.subfield"));
    }

    @Test
    public void testExtractFieldNamesTypesSkipAliasAndObject() {
        Map<String, Object> mappingSource = new HashMap<>();
        mappingSource.put("alias_field", Map.of("type", "alias"));
        mappingSource.put("object_field", Map.of("type", "object"));
        mappingSource.put("text_field", Map.of("type", "text"));

        Map<String, String> fieldsToType = new HashMap<>();
        IndexInsightUtils.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertFalse(fieldsToType.containsKey("alias_field"));
        assertFalse(fieldsToType.containsKey("object_field"));
        assertEquals("text", fieldsToType.get("text_field"));
    }

    @Test
    public void testExtractFieldNamesTypesWithFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> textField = new HashMap<>();
        textField.put("type", "text");
        textField.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        mappingSource.put("text_field", textField);

        Map<String, String> fieldsToType = new HashMap<>();
        IndexInsightUtils.extractFieldNamesTypes(mappingSource, fieldsToType, "", true);

        assertEquals("text", fieldsToType.get("text_field"));
        assertEquals("keyword", fieldsToType.get("text_field.keyword"));
    }

    @Test
    public void testExtractFieldNamesTypesExcludeFields() {
        Map<String, Object> mappingSource = new HashMap<>();
        Map<String, Object> textField = new HashMap<>();
        textField.put("type", "text");
        textField.put("fields", Map.of("keyword", Map.of("type", "keyword")));
        mappingSource.put("text_field", textField);

        Map<String, String> fieldsToType = new HashMap<>();
        IndexInsightUtils.extractFieldNamesTypes(mappingSource, fieldsToType, "", false);

        assertEquals("text", fieldsToType.get("text_field"));
        assertFalse(fieldsToType.containsKey("text_field.keyword"));
    }

    @Test
    public void testGenerateDocId() {
        String sourceIndex = "test-index";
        MLIndexInsightType taskType = MLIndexInsightType.STATISTICAL_DATA;

        String docId1 = IndexInsightUtils.generateDocId(sourceIndex, taskType);
        String docId2 = IndexInsightUtils.generateDocId(sourceIndex, taskType);

        assertEquals(docId1, docId2);
        assertTrue(docId1.length() == 64);
    }

    @Test
    public void testGenerateDocIdDifferentInputs() {
        String docId1 = IndexInsightUtils.generateDocId("index1", MLIndexInsightType.STATISTICAL_DATA);
        String docId2 = IndexInsightUtils.generateDocId("index2", MLIndexInsightType.STATISTICAL_DATA);

        assertFalse(docId1.equals(docId2));
    }

    @Test
    public void testExtractModelResponseChoices() {
        Map<String, Object> data = Map
            .of("choices", Collections.singletonList(Map.of("message", Map.of("content", "response from choices"))));

        String result = IndexInsightUtils.extractModelResponse(data);
        assertEquals("response from choices", result);
    }

    @Test
    public void testExtractModelResponseContent() {
        Map<String, Object> data = Map.of("content", Collections.singletonList(Map.of("text", "response from content")));

        String result = IndexInsightUtils.extractModelResponse(data);
        assertEquals("response from content", result);
    }

    @Test
    public void testExtractModelResponseDefault() {
        Map<String, Object> data = Map.of("response", "default response");

        String result = IndexInsightUtils.extractModelResponse(data);
        assertEquals("default response", result);
    }

    @Test
    public void testCallLLMWithAgentSuccessWithJson() {
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

        IndexInsightUtils.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onResponse("parsed response");
    }

    @Test
    public void testCallLLMWithAgentSuccessWithPlainText() {
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

        IndexInsightUtils.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onResponse(plainResponse);
    }

    @Test
    public void testCallLLMWithAgentFailure() {
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

        IndexInsightUtils.callLLMWithAgent(client, agentId, prompt, sourceIndex, listener);

        verify(listener).onFailure(expectedException);
    }
}
