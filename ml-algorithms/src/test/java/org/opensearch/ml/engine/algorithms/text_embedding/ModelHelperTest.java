/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ModelHelper modelHelper;
    private MLModelFormat modelFormat;
    private String modelId;
    private MLEngine mlEngine;

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Mock
    ActionListener<MLRegisterModelInput> registerModelListener;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        modelFormat = MLModelFormat.TORCH_SCRIPT;
        modelId = "model_id";
        mlEngine = new MLEngine(Path.of("/tmp/test" + modelId), null);
        modelHelper = new ModelHelper(mlEngine);
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        modelHelper.downloadAndSplit(modelFormat, modelId, "model_name", "1", "http://testurl", null, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper.downloadAndSplit(modelFormat, modelId, "model_name", "1", modelUrl, null, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testVerifyModelZipFile() throws IOException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_ONNX() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is TORCH_SCRIPT, but find .onnx file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl);
    }

    @Test
    public void testVerifyModelZipFile_WrongModelFormat_TORCH_SCRIPT() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model format is ONNX, but find .pt file");
        String modelUrl = getClass().getResource("traced_small_model_wrong_onnx.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(MLModelFormat.ONNX, modelUrl);
    }

    @Test
    public void testVerifyModelZipFile_DuplicateModelFile() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Find multiple model files, but expected only one");
        String modelUrl = getClass().getResource("traced_small_model_duplicate_pt.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl);
    }

    @Test
    public void testVerifyModelZipFile_MissingTokenizer() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No tokenizer file");
        String modelUrl = getClass().getResource("traced_small_model_missing_tokenizer.zip").toString().substring(5);
        modelHelper.verifyModelZipFile(modelFormat, modelUrl);
    }

    @Test
    public void testDownloadPrebuiltModelConfig_WrongModelName() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("test_model_name")
                .version("1.0.1")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(registerModelListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadPrebuiltModelConfig() {
        String taskId = "test_task_id";
        MLRegisterModelInput registerModelInput = MLRegisterModelInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("1.0.1")
                .modelFormat(modelFormat)
                .deployModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, registerModelInput, registerModelListener);
        ArgumentCaptor<MLRegisterModelInput> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelInput.class);
        verify(registerModelListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        MLModelConfig modelConfig = argumentCaptor.getValue().getModelConfig();
        assertNotNull(modelConfig);
        assertEquals("mpnet", modelConfig.getModelType());
    }
}
