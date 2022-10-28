/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom_model;

import static org.opensearch.ml.utils.TestData.HUGGINGFACE_TRANSFORMER_MODEL_URL;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileResponse;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesResponse;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class CustomModelITTests extends MLCommonsIntegTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testCustomModelWorkflow() throws InterruptedException {
        FunctionName functionName = FunctionName.TEXT_EMBEDDING;
        String modelName = "all-MiniLM-L6-v2";
        String version = "1.0.0";
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        String modelType = "bert";
        TextEmbeddingModelConfig.FrameworkType frameworkType = TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS;
        int dimension = 384;
        String allConfig = null;
        String taskId = uploadModel(
            functionName,
            modelName,
            version,
            modelFormat,
            modelType,
            frameworkType,
            dimension,
            allConfig,
            HUGGINGFACE_TRANSFORMER_MODEL_URL,
            false
        );
        assertNotNull(taskId);

        AtomicReference<String> modelId = new AtomicReference<>();
        AtomicReference<MLModel> mlModel = new AtomicReference<>();
        waitUntil(() -> {
            String id = getTask(taskId).getModelId();
            modelId.set(id);
            MLModel model = null;
            try {
                if (id != null) {
                    model = getModel(id + "_" + 1);
                    mlModel.set(model);
                }
            } catch (Exception e) {
                logger.error("Failed to get model " + id, e);
            }
            return id != null && model != null;
        }, 20, TimeUnit.SECONDS);
        assertNotNull(modelId.get());
        MLModel model = getModel(modelId.get());
        assertNotNull(model);
        assertEquals(9, model.getTotalChunks().intValue());
        assertNotNull(mlModel.get());
        assertEquals(1, mlModel.get().getChunkNumber().intValue());

        waitUntil(() -> {
            SearchResponse response = searchModelChunks(modelId.get());
            AtomicBoolean modelChunksReady = new AtomicBoolean(false);
            if (response != null) {
                long totalHits = response.getHits().getTotalHits().value;
                if (totalHits == 9) {
                    modelChunksReady.set(true);
                }
            }
            return modelChunksReady.get();
        }, 20, TimeUnit.SECONDS);

        loadModel(modelId.get());

        AtomicBoolean loaded = new AtomicBoolean(false);
        waitUntil(() -> {
            MLProfileResponse modelProfile = getModelProfile(taskId);
            if (modelProfile != null) {
                List<MLProfileNodeResponse> nodes = modelProfile.getNodes();
                if (nodes != null) {
                    nodes.forEach(node -> {
                        node.getMlNodeModels().entrySet().forEach(e -> {
                            if (e.getValue().getModelState() == MLModelState.LOADED) {
                                loaded.set(true);
                            }
                        });
                    });
                }
            }
            return loaded.get();
        }, 20, TimeUnit.SECONDS);
        assertTrue(loaded.get());

        // Predict
        MLTaskResponse response = predict(
            modelId.get(),
            functionName,
            TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny")).build(),
            null
        );
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        assertEquals(1, output.getMlModelOutputs().size());
        assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        assertEquals(dimension, output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getData().length);

        // Unload
        UnloadModelNodesResponse unloadModelResponse = unloadModel(modelId.get());
        assertEquals(1, unloadModelResponse.getNodes().size());
        Map<String, String> unloadStatus = unloadModelResponse.getNodes().get(0).getModelUnloadStatus();
        assertEquals(1, unloadStatus.size());
        assertEquals("unloaded", unloadStatus.get(modelId.get()));
    }
}
