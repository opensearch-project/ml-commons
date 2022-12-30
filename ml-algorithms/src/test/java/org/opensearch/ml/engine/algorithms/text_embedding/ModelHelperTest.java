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
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ModelHelper modelHelper;
    private String modelId;
    private MLEngine mlEngine;

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Mock
    ActionListener<MLUploadInput> uploadInputListener;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        modelId = "model_id";
        mlEngine = new MLEngine(Path.of("/tmp/test" + modelId));
        modelHelper = new ModelHelper(mlEngine);
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        modelHelper.downloadAndSplit(modelId, "model_name", "1", "http://testurl", actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper.downloadAndSplit(modelId, "model_name", "1", modelUrl, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }

    @Test
    public void testDownloadPrebuiltModelConfig_WrongModelName() {
        String taskId = "test_task_id";
        MLUploadInput unloadInput = MLUploadInput.builder()
                .modelName("test_model_name")
                .version("1.0.0")
                .loadModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, unloadInput, uploadInputListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(uploadInputListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadPrebuiltModelConfig() {
        String taskId = "test_task_id";
        MLUploadInput unloadInput = MLUploadInput.builder()
                .modelName("huggingface/sentence-transformers/all-mpnet-base-v2")
                .version("1.0.0")
                .loadModel(false)
                .modelNodeIds(new String[]{"node_id1"})
                .build();
        modelHelper.downloadPrebuiltModelConfig(taskId, unloadInput, uploadInputListener);
        ArgumentCaptor<MLUploadInput> argumentCaptor = ArgumentCaptor.forClass(MLUploadInput.class);
        verify(uploadInputListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        MLModelConfig modelConfig = argumentCaptor.getValue().getModelConfig();
        assertNotNull(modelConfig);
        assertEquals("mpnet", modelConfig.getModelType());
    }
}
