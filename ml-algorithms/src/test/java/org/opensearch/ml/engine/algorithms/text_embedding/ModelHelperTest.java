/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ModelHelper modelHelper;
    private MLModelFormat modelFormat;
    private String modelId;
    private MLEngine mlEngine;
    private String hashValue = "e13b74006290a9d0f58c1376f9629d4ebc05a0f9385f40db837452b167ae9021";

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Mock
    ActionListener<MLRegisterModelInput> registerModelListener;

    Encryptor encryptor;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        modelFormat = MLModelFormat.TORCH_SCRIPT;
        modelId = "model_id";
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + modelId), encryptor);
        modelHelper = new ModelHelper(mlEngine);
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        modelId = "url_failure_model_id";
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", "http://testurl", null, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, hashValue, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testDownloadAndSplit_nullHashCode() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper.downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, null, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IllegalArgumentException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit_HashFailure() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(
                modelFormat,
                modelId,
                "model_name",
                "1",
                modelUrl,
                "wrong_hash_value",
                FunctionName.TEXT_EMBEDDING,
                actionListener
            );
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IllegalArgumentException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit_Hash() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper
            .downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, hashValue, FunctionName.TEXT_EMBEDDING, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testVerifyModelZipFile() throws IOException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_ONNX() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is TORCH_SCRIPT, but find .onnx file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_TORCH_SCRIPT() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is ONNX, but find .pt file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(MLModelFormat.ONNX, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_DuplicateModelFile() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Find multiple model files, but expected only one");
        String modelUrl = getClass().getResource("traced_small_model_duplicate_pt.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testVerifyModelZipFile_MissingTokenizer() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No tokenizer file");
        String modelUrl = getClass().getResource("traced_small_model_missing_tokenizer.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl, FunctionName.TEXT_EMBEDDING.toString(), FunctionName.TEXT_EMBEDDING);
    }

    @Test
    public void testDownloadPrebuiltModelConfig_WrongModelName() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("test_model_name")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(registerModelListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testDownloadPrebuiltModelConfig() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<MLRegisterModelInput> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelInput.class);
        verify(registerModelListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        MLModelConfig modelConfig = argumentCaptor.getValue().getModelConfig();
        assertNotNull(modelConfig);
        assertEquals("mpnet", modelConfig.getModelType());
    }

    @Test
    public void testDownloadPrebuiltModelMetaList() throws IOException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.2")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertEquals("huggingface/sentence-transformers/all-distilroberta-v1", ((Map<String, String>) modelMetaList.get(0)).get("name"));
    }

    @Test
    public void testIsModelAllowed_true() throws IOException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("1.0.2")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertTrue(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelName() throws IOException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2-wrong")
            .version("1.0.1")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }

    @Test
    public void testIsModelAllowed_WrongModelVersion() throws IOException {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
            .version("000")
            .modelGroupId("mockGroupId")
            .modelFormat(modelFormat)
            .deployModel(false)
            .modelNodeIds(new String[] { "node_id1" })
            .build();
        List modelMetaList = modelHelper.downloadPrebuiltModelMetaList(taskId, registerModelInput);
        assertFalse(modelHelper.isModelAllowed(registerModelInput, modelMetaList));
    }
}
