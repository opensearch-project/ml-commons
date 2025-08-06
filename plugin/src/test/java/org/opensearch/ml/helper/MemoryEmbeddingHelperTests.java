/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MemoryEmbeddingHelperTests {

    @Mock
    private Client client;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private ActionListener<Object> objectListener;

    @Mock
    private ActionListener<List<Object>> listListener;

    @Mock
    private ActionListener<Boolean> booleanListener;

    @Mock
    private MLTaskResponse predictionResponse;

    @Mock
    private MLModel mlModel;

    private MemoryEmbeddingHelper helper;
    private MemoryStorageConfig storageConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create real ThreadContext since it's final and can't be mocked
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        helper = new MemoryEmbeddingHelper(client, mlModelManager);

        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .semanticStorageEnabled(true)
            .build();
    }

    @Test
    public void testGenerateEmbeddingSuccess() {
        String text = "Test text for embedding";
        Number[] embeddingData = { 0.1f, 0.2f, 0.3f };

        setupMockMLModel(MLModelState.DEPLOYED);
        setupMockPredictionResponse(createDenseEmbeddingOutput(embeddingData));

        helper.generateEmbedding(text, storageConfig, objectListener);

        // Verify model validation
        verify(mlModelManager).getModel(eq("model-123"), any());

        // Verify prediction request
        ArgumentCaptor<MLPredictionTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), requestCaptor.capture(), any());

        MLPredictionTaskRequest request = requestCaptor.getValue();
        assertEquals("model-123", request.getModelId());
        assertEquals(FunctionName.TEXT_EMBEDDING, request.getMlInput().getAlgorithm());

        // Verify response
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());

        float[] embedding = (float[]) responseCaptor.getValue();
        assertEquals(3, embedding.length);
        assertEquals(0.1f, embedding[0], 0.001f);
        assertEquals(0.2f, embedding[1], 0.001f);
        assertEquals(0.3f, embedding[2], 0.001f);
    }

    @Test
    public void testGenerateEmbeddingWithSparseEncoding() {
        String text = "Test text for sparse embedding";
        Map<String, Float> sparseData = new HashMap<>();
        sparseData.put("token1", 0.5f);
        sparseData.put("token2", 0.8f);

        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model-123")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);
        setupMockPredictionResponse(createSparseEmbeddingOutput(sparseData));

        helper.generateEmbedding(text, storageConfig, objectListener);

        // Verify response
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());

        Map<String, ?> embedding = (Map<String, ?>) responseCaptor.getValue();
        assertEquals(2, embedding.size());
        assertEquals(0.5f, embedding.get("token1"));
        assertEquals(0.8f, embedding.get("token2"));
    }

    @Test
    public void testGenerateEmbeddingWithNullStorageConfig() {
        helper.generateEmbedding("test text", null, objectListener);
        verify(objectListener).onResponse(null);
        verify(mlModelManager, never()).getModel(any(), any());
    }

    @Test
    public void testGenerateEmbeddingWithSemanticStorageDisabled() {
        storageConfig = MemoryStorageConfig.builder().semanticStorageEnabled(false).build();

        helper.generateEmbedding("test text", storageConfig, objectListener);
        verify(objectListener).onResponse(null);
        verify(mlModelManager, never()).getModel(any(), any());
    }

    @Test
    public void testGenerateEmbeddingWithMissingModelConfig() {
        storageConfig = MemoryStorageConfig.builder().semanticStorageEnabled(true).embeddingModelId(null).embeddingModelType(null).build();

        helper.generateEmbedding("test text", storageConfig, objectListener);
        verify(objectListener).onResponse(null);
        verify(mlModelManager, never()).getModel(any(), any());
    }

    @Test
    public void testGenerateEmbeddingModelNotDeployed() {
        setupMockMLModel(MLModelState.REGISTERED);

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
        verify(client, never()).execute(any(), any(), any());
    }

    @Test
    public void testGenerateEmbeddingModelValidationFailure() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Model not found"));
            return null;
        }).when(mlModelManager).getModel(any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
        verify(client, never()).execute(any(), any(), any());
    }

    @Test
    public void testGenerateEmbeddingPredictionFailure() {
        setupMockMLModel(MLModelState.DEPLOYED);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Prediction failed"));
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    @Test
    public void testGenerateEmbeddingUnexpectedOutputType() {
        setupMockMLModel(MLModelState.DEPLOYED);

        MLOutput unexpectedOutput = mock(MLOutput.class);
        when(predictionResponse.getOutput()).thenReturn(unexpectedOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsSuccess() {
        List<String> texts = Arrays.asList("text1", "text2", "text3");
        Number[] embedding1 = { 0.1f, 0.2f };
        Number[] embedding2 = { 0.3f, 0.4f };
        Number[] embedding3 = { 0.5f, 0.6f };

        setupMockMLModel(MLModelState.DEPLOYED);
        setupMockPredictionResponse(createMultipleDenseEmbeddingOutput(Arrays.asList(embedding1, embedding2, embedding3)));

        helper.generateEmbeddingsForMultipleTexts(texts, storageConfig, listListener);

        // Verify response
        ArgumentCaptor<List<Object>> responseCaptor = ArgumentCaptor.forClass(List.class);
        verify(listListener).onResponse(responseCaptor.capture());

        List<Object> embeddings = responseCaptor.getValue();
        assertEquals(3, embeddings.size());

        float[] emb1 = (float[]) embeddings.get(0);
        assertArrayEquals(new float[] { 0.1f, 0.2f }, emb1, 0.001f);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsEmptyList() {
        helper.generateEmbeddingsForMultipleTexts(new ArrayList<>(), storageConfig, listListener);

        ArgumentCaptor<List<Object>> responseCaptor = ArgumentCaptor.forClass(List.class);
        verify(listListener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isEmpty());
        verify(mlModelManager, never()).getModel(any(), any());
    }

    @Test
    public void testValidateEmbeddingModelStateDeployed() {
        when(mlModel.getModelState()).thenReturn(MLModelState.DEPLOYED);
        
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("model-123"), any());

        helper.validateEmbeddingModelState("model-123", FunctionName.TEXT_EMBEDDING, booleanListener);

        verify(booleanListener).onResponse(true);
    }

    @Test
    public void testValidateEmbeddingModelStateNotDeployed() {
        when(mlModel.getModelState()).thenReturn(MLModelState.REGISTERED);
        
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("model-123"), any());

        helper.validateEmbeddingModelState("model-123", FunctionName.TEXT_EMBEDDING, booleanListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(booleanListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalStateException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("DEPLOYED"));
    }

    @Test
    public void testValidateEmbeddingModelStateRemoteModel() {
        helper.validateEmbeddingModelState("remote-model", FunctionName.REMOTE, booleanListener);
        verify(booleanListener).onResponse(true);
        verify(mlModelManager, never()).getModel(any(), any());
    }

    @Test
    public void testValidateEmbeddingModelStateFailure() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get model"));
            return null;
        }).when(mlModelManager).getModel(eq("model-123"), any());

        helper.validateEmbeddingModelState("model-123", FunctionName.TEXT_EMBEDDING, booleanListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(booleanListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalStateException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Failed to validate embedding model state"));
    }

    @Test
    public void testGenerateEmbeddingWithNestedSparseResponse() {
        String text = "Test text";
        Map<String, Float> sparseData = new HashMap<>();
        sparseData.put("word1", 1.5f);
        sparseData.put("word2", 2.5f);

        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);
        setupMockPredictionResponse(createNestedSparseEmbeddingOutput(sparseData));

        helper.generateEmbedding(text, storageConfig, objectListener);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());

        Map<String, ?> embedding = (Map<String, ?>) responseCaptor.getValue();
        assertEquals(2, embedding.size());
        assertEquals(1.5f, embedding.get("word1"));
        assertEquals(2.5f, embedding.get("word2"));
    }

    @Test
    public void testGenerateEmbeddingWithNullTensorData() {
        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getName()).thenReturn("sentence_embedding");
        when(tensor.getData()).thenReturn(null);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsWithNullStorageConfig() {
        try {
            helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), null, listListener);
        } catch (NullPointerException e) {
            // Expected - storageConfig is null
            return;
        }
        // If we reach here, the test should fail
        assertTrue("Expected NullPointerException", false);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsWithSemanticStorageDisabled() {
        // When semantic storage is disabled, embedding config should be null
        storageConfig = MemoryStorageConfig.builder().semanticStorageEnabled(false).embeddingModelId(null).embeddingModelType(null).build();

        // When embeddingModelId is null but method tries to validate model, it passes null to validateEmbeddingModelState
        // which should handle the null case gracefully
        setupMockMLModel(MLModelState.DEPLOYED);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new IllegalArgumentException("Model ID is null"));
            return null;
        }).when(mlModelManager).getModel(eq(null), any());

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        // Should fail due to null model ID
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsValidationFailure() {
        setupMockMLModel(MLModelState.REGISTERED);

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalStateException);
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsPredictionFailure() {
        setupMockMLModel(MLModelState.DEPLOYED);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Prediction failed"));
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listListener).onFailure(exceptionCaptor.capture());
        assertEquals("Prediction failed", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsWithSparseEncoding() {
        Map<String, Float> sparseData1 = new HashMap<>();
        sparseData1.put("token1", 0.5f);
        Map<String, Float> sparseData2 = new HashMap<>();
        sparseData2.put("token2", 0.8f);

        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);

        // Create a response with multiple sparse embeddings
        ModelTensorOutput output = createMultipleSparseEmbeddingOutput(Arrays.asList(sparseData1, sparseData2));
        setupMockPredictionResponse(output);

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        ArgumentCaptor<List<Object>> responseCaptor = ArgumentCaptor.forClass(List.class);
        verify(listListener).onResponse(responseCaptor.capture());

        List<Object> embeddings = responseCaptor.getValue();
        assertEquals(2, embeddings.size());
        Map<String, ?> emb1 = (Map<String, ?>) embeddings.get(0);
        assertEquals(0.5f, emb1.get("token1"));
        Map<String, ?> emb2 = (Map<String, ?>) embeddings.get(1);
        assertEquals(0.8f, emb2.get("token2"));
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsUnexpectedOutputType() {
        setupMockMLModel(MLModelState.DEPLOYED);

        MLOutput unexpectedOutput = mock(MLOutput.class);
        when(predictionResponse.getOutput()).thenReturn(unexpectedOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalStateException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Unexpected ML output type"));
    }

    @Test
    public void testGenerateEmbeddingsForMultipleTextsWithNullMlModelOutputs() {
        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(null);
        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1", "text2"), storageConfig, listListener);

        ArgumentCaptor<List<Object>> responseCaptor = ArgumentCaptor.forClass(List.class);
        verify(listListener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isEmpty());
    }

    @Test
    public void testGenerateEmbeddingWithEmptyModelTensors() {
        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(new ArrayList<>());

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    @Test
    public void testGenerateEmbeddingWithExceptionDuringExtraction() {
        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getName()).thenReturn("sentence_embedding");
        // This will cause a NullPointerException during extraction
        when(tensor.getData()).thenReturn(null);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    @Test
    public void testGenerateEmbeddingWithSparseEncodingEmptyDataMap() {
        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getDataAsMap()).thenReturn(new HashMap<>());

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue() instanceof Map);
        assertTrue(((Map) responseCaptor.getValue()).isEmpty());
    }

    @Test
    public void testGenerateEmbeddingWithSparseEncodingInvalidNestedResponse() {
        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);

        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("response", "not a list"); // Invalid type for response

        ModelTensor tensor = mock(ModelTensor.class);
        doReturn(nestedData).when(tensor).getDataAsMap();

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());
        assertEquals(nestedData, responseCaptor.getValue());
    }

    @Test
    public void testGenerateEmbeddingWithSparseEncodingEmptyResponseList() {
        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);

        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("response", new ArrayList<>()); // Empty list

        ModelTensor tensor = mock(ModelTensor.class);
        doReturn(nestedData).when(tensor).getDataAsMap();

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());
        assertEquals(nestedData, responseCaptor.getValue());
    }

    @Test
    public void testGenerateEmbeddingWithSparseEncodingInvalidListItem() {
        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("sparse-model")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .semanticStorageEnabled(true)
            .build();

        setupMockMLModel(MLModelState.DEPLOYED);

        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("response", Arrays.asList("not a map")); // Invalid type in list

        ModelTensor tensor = mock(ModelTensor.class);
        doReturn(nestedData).when(tensor).getDataAsMap();

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectListener).onResponse(responseCaptor.capture());
        assertEquals(nestedData, responseCaptor.getValue());
    }

    @Test
    public void testGenerateEmbeddingsWithUnsupportedModelType() {
        // Test case where generateEmbeddingsForMultipleTexts is called with an unsupported embedding type
        // First create a valid storage config with TEXT_EMBEDDING to pass validation
        storageConfig = MemoryStorageConfig
            .builder()
            .embeddingModelId("test-model")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .semanticStorageEnabled(true)
            .dimension(768)
            .build();

        // Now manually override the type to an unsupported one (this simulates a future case)
        // Since we can't change the type after building, we'll test the extractDenseEmbedding method behavior
        // when the if/else conditions don't match
        setupMockMLModel(MLModelState.DEPLOYED);

        // Create a mock response that looks like neither dense nor sparse
        ModelTensorOutput output = mock(ModelTensorOutput.class);
        ModelTensors modelTensors = mock(ModelTensors.class);
        ModelTensor tensor = mock(ModelTensor.class);

        when(tensor.getName()).thenReturn("unknown_tensor");
        when(tensor.getData()).thenReturn(null);
        when(tensor.getDataAsMap()).thenReturn(null);

        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));
        when(output.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        setupMockPredictionResponse(output);

        helper.generateEmbeddingsForMultipleTexts(Arrays.asList("text1"), storageConfig, listListener);

        ArgumentCaptor<List<Object>> responseCaptor = ArgumentCaptor.forClass(List.class);
        verify(listListener).onResponse(responseCaptor.capture());

        List<Object> embeddings = responseCaptor.getValue();
        assertEquals(1, embeddings.size());
        assertNull(embeddings.get(0)); // Should be null when extraction fails
    }

    @Test
    public void testGenerateEmbeddingWithDenseEmbeddingNoSentenceEmbeddingTensor() {
        setupMockMLModel(MLModelState.DEPLOYED);

        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getName()).thenReturn("some_other_tensor"); // Not "sentence_embedding"
        when(tensor.getData()).thenReturn(new Number[] { 0.1f, 0.2f });

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput tensorOutput = mock(ModelTensorOutput.class);
        when(tensorOutput.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        when(predictionResponse.getOutput()).thenReturn(tensorOutput);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        helper.generateEmbedding("test text", storageConfig, objectListener);

        verify(objectListener).onResponse(null);
    }

    // Helper methods to create mock responses
    private void setupMockMLModel(MLModelState state) {
        when(mlModel.getModelState()).thenReturn(state);
        
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any());
    }

    private void setupMockPredictionResponse(ModelTensorOutput output) {
        when(predictionResponse.getOutput()).thenReturn(output);
        
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(predictionResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());
    }

    private ModelTensorOutput createDenseEmbeddingOutput(Number[] data) {
        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getName()).thenReturn("sentence_embedding");
        when(tensor.getData()).thenReturn(data);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        return output;
    }

    private ModelTensorOutput createSparseEmbeddingOutput(Map<String, Float> data) {
        ModelTensor tensor = mock(ModelTensor.class);
        // Use doReturn to avoid generic type issues
        doReturn(data).when(tensor).getDataAsMap();

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        return output;
    }

    private ModelTensorOutput createNestedSparseEmbeddingOutput(Map<String, Float> data) {
        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("response", Arrays.asList(data));

        ModelTensor tensor = mock(ModelTensor.class);
        // Use doReturn to avoid generic type issues
        doReturn(nestedData).when(tensor).getDataAsMap();

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Arrays.asList(modelTensors));

        return output;
    }

    private ModelTensorOutput createMultipleDenseEmbeddingOutput(List<Number[]> embeddings) {
        List<ModelTensors> modelTensorsList = new ArrayList<>();

        for (Number[] data : embeddings) {
            ModelTensor tensor = mock(ModelTensor.class);
            when(tensor.getName()).thenReturn("sentence_embedding");
            when(tensor.getData()).thenReturn(data);

            ModelTensors modelTensors = mock(ModelTensors.class);
            when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

            modelTensorsList.add(modelTensors);
        }

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(modelTensorsList);

        return output;
    }

    private ModelTensorOutput createMultipleSparseEmbeddingOutput(List<Map<String, Float>> embeddings) {
        List<ModelTensors> modelTensorsList = new ArrayList<>();

        for (Map<String, Float> data : embeddings) {
            ModelTensor tensor = mock(ModelTensor.class);
            doReturn(data).when(tensor).getDataAsMap();

            ModelTensors modelTensors = mock(ModelTensors.class);
            when(modelTensors.getMlModelTensors()).thenReturn(Arrays.asList(tensor));

            modelTensorsList.add(modelTensors);
        }

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(modelTensorsList);

        return output;
    }
}
