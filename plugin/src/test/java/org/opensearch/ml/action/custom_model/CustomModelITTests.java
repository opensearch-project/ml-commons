/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom_model;

import static org.opensearch.ml.utils.TestData.IRIS_DATA_SIZE;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_URL;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileResponse;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.common.transport.unload.UnloadModelNodeResponse;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesResponse;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

//TODO: DJL can't load models on multiple virtual nodes under OS integ test framework, so have to use "numDataNodes = 1"
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class CustomModelITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_model_serving_test";
        loadIrisData(irisIndexName);
        ClusterUpdateSettingsRequest updateSettingRequest = new ClusterUpdateSettingsRequest();
        updateSettingRequest.transientSettings(ImmutableMap.of("logger.org.opensearch.ml", "DEBUG"));
        admin().cluster().updateSettings(updateSettingRequest).actionGet(5000);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        ClusterUpdateSettingsRequest updateSettingRequest = new ClusterUpdateSettingsRequest();
        Map<String, ?> setting = new HashMap<>();
        setting.put("logger.org.opensearch.ml", null);
        updateSettingRequest.transientSettings(setting);
        admin().cluster().updateSettings(updateSettingRequest).actionGet(5000);
    }

    @Ignore
    public void testCustomModelWorkflow() throws InterruptedException {
        testTextEmbeddingModel(ImmutableSet.of());
        testKMeans(ImmutableSet.of());
    }

    protected void testTextEmbeddingModel(Set<String> modelWorkerNodes) throws InterruptedException {
        FunctionName functionName = FunctionName.TEXT_EMBEDDING;
        String modelName = "small-model";
        String version = "1.0.0";
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        String modelType = "bert";
        TextEmbeddingModelConfig.FrameworkType frameworkType = TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
        int dimension = 768;
        String allConfig = null;

        // upload model
        String taskId = uploadModel(
            functionName,
            modelName,
            version,
            modelFormat,
            modelType,
            frameworkType,
            dimension,
            allConfig,
            SENTENCE_TRANSFORMER_MODEL_URL,
            false
        );
        assertNotNull(taskId);

        // profile all
        MLProfileResponse allProfileAfterUploading = getAllProfile();
        verifyRunningTask(
            taskId,
            MLTaskType.UPLOAD_MODEL,
            ImmutableSet.of(MLTaskState.RUNNING),
            allProfileAfterUploading,
            modelWorkerNodes
        );

        AtomicReference<String> modelId = new AtomicReference<>();
        AtomicReference<MLModel> mlModel = new AtomicReference<>();
        waitUntil(() -> {
            String id = getTask(taskId).getModelId();
            modelId.set(id);
            MLModel model = null;
            try {
                if (id != null) {
                    model = getModel(id + "_" + 0);
                    mlModel.set(model);
                }
            } catch (Exception e) {
                logger.error("Failed to get model " + id, e);
            }
            return modelId.get() != null;
        }, 20, TimeUnit.SECONDS);
        assertNotNull(modelId.get());
        MLModel model = getModel(modelId.get());
        assertNotNull(model);
        assertEquals(1, model.getTotalChunks().intValue());
        assertNotNull(mlModel.get());
        assertEquals(0, mlModel.get().getChunkNumber().intValue());

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

        // load model
        String loadTaskId = loadModel(modelId.get(), modelWorkerNodes.toArray(new String[0]));

        // profile all
        MLProfileResponse allProfileAfterLoading = getAllProfile();
        // Thread.sleep(10);
        verifyRunningTask(
            loadTaskId,
            MLTaskType.LOAD_MODEL,
            ImmutableSet.of(MLTaskState.CREATED, MLTaskState.RUNNING),
            allProfileAfterLoading,
            modelWorkerNodes
        );

        waitUntilLoaded(loadTaskId);

        Thread.sleep(300);
        // profile model
        MLProfileResponse modelProfile = getModelProfile(modelId.get());
        verifyNoRunningTask(modelProfile);
        verifyLoadedModel(modelId.get(), 0, modelProfile, modelWorkerNodes);

        // predict
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

        // sync up running tasks/models
        MLSyncUpNodesResponse syncUpResponse = syncUp_RunningModelAndTask();
        verifyLoadedModelOfSyncupResponse(modelWorkerNodes, modelId.get(), syncUpResponse);

        MLProfileResponse allProfile = getAllProfile();
        verifyLoadedModel(modelId.get(), 1, allProfile, modelWorkerNodes);

        // unload model
        UnloadModelNodesResponse unloadModelResponse = unloadModel(modelId.get());
        int nodeNumber = clusterService().state().getNodes().getSize();
        assertEquals(nodeNumber, unloadModelResponse.getNodes().size());
        for (UnloadModelNodeResponse node : unloadModelResponse.getNodes()) {
            if (!node.getModelUnloadStatus().isEmpty()) {
                Map<String, String> unloadStatus = node.getModelUnloadStatus();
                assertEquals(1, unloadStatus.size());
                assertEquals("unloaded", unloadStatus.get(modelId.get()));
            }
        }

        // sync up running tasks/models
        MLSyncUpNodesResponse syncUpResponseAfterUnload = syncUp_RunningModelAndTask();
        for (MLSyncUpNodeResponse nodeResponse : syncUpResponseAfterUnload.getNodes()) {
            if (nodeResponse.getNode().isDataNode()) {
                assertEquals(0, nodeResponse.getRunningLoadModelTaskIds().length);
                assertEquals(0, nodeResponse.getLoadedModelIds().length);
            }
        }

        MLProfileResponse noRuningTaskProfile = getAllProfile();
        verifyNoRunningTask(noRuningTaskProfile);

        MLSyncUpNodesResponse syncUpClearResponse = syncUp_Clear();
        for (MLSyncUpNodeResponse nodeResponse : syncUpClearResponse.getNodes()) {
            assertNull(nodeResponse.getLoadedModelIds());
            assertNull(nodeResponse.getRunningLoadModelTaskIds());
        }
    }

    private void verifyLoadedModelOfSyncupResponse(Set<String> modelWorkerNodes, String modelId, MLSyncUpNodesResponse syncUpResponse) {
        boolean hasLoadedModel = false;
        for (MLSyncUpNodeResponse nodeResponse : syncUpResponse.getNodes()) {
            DiscoveryNode node = nodeResponse.getNode();
            if (modelWorkerNodes.size() > 0 && !modelWorkerNodes.contains(node.getId())) {
                continue;
            }
            if (node.isDataNode()) {
                assertEquals(0, nodeResponse.getRunningLoadModelTaskIds().length);
                assertArrayEquals(new String[] { modelId }, nodeResponse.getLoadedModelIds());
                hasLoadedModel = true;
            }
        }
        assertTrue(hasLoadedModel);
    }

    private void waitUntilLoaded(String loadTaskId) throws InterruptedException {
        AtomicBoolean loaded = new AtomicBoolean(false);
        waitUntil(() -> {
            MLProfileResponse modelProfile = getModelProfile(loadTaskId);
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
    }

    protected void testKMeans(Set<String> modelWorkerNodes) throws InterruptedException {
        String modelId = trainKmeansWithIrisData(irisIndexName, false);
        // load model
        String loadTaskId = loadModel(modelId, modelWorkerNodes.toArray(new String[0]));
        waitUntilLoaded(loadTaskId);

        Thread.sleep(300);
        // profile model
        MLProfileResponse modelProfile = getModelProfile(modelId);
        verifyNoRunningTask(modelProfile);
        verifyLoadedModel(modelId, 0, modelProfile, modelWorkerNodes);

        // predict
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQuery());
        predictAndVerify(modelId, inputDataset, FunctionName.KMEANS, null, IRIS_DATA_SIZE);

        Thread.sleep(300);
        // profile model
        MLProfileResponse modelProfileAfterPredict = getModelProfile(modelId);
        verifyNoRunningTask(modelProfileAfterPredict);
        verifyLoadedModel(modelId, 1, modelProfileAfterPredict, modelWorkerNodes);
    }

    private void verifyRunningTask(
        String taskId,
        MLTaskType taskType,
        Set<MLTaskState> states,
        MLProfileResponse allProfileAfterUploading,
        Set<String> modelWorkerNodes
    ) {
        for (MLProfileNodeResponse nodeResponse : allProfileAfterUploading.getNodes()) {
            DiscoveryNode node = nodeResponse.getNode();
            if (modelWorkerNodes.size() > 0 && !modelWorkerNodes.contains(node.getId())) {
                continue;
            }
            if (node.isDataNode()) {
                if (nodeResponse.getMlNodeTasks().containsKey(taskId)) {
                    assertTrue(states.contains(nodeResponse.getMlNodeTasks().get(taskId).getState()));
                    assertEquals(taskType, nodeResponse.getMlNodeTasks().get(taskId).getTaskType());
                }
            }
        }
    }

    private void verifyNoRunningTask(MLProfileResponse allProfileAfterUploading) {
        for (MLProfileNodeResponse nodeResponse : allProfileAfterUploading.getNodes()) {
            if (nodeResponse.getNode().isDataNode()) {
                assertEquals(0, nodeResponse.getMlNodeTasks().size());
            }
        }
    }

    private void verifyLoadedModel(
        String modelId,
        long predictCounts,
        MLProfileResponse allProfileAfterUploading,
        Set<String> modelWorkerNodes
    ) {
        boolean hasLoadedModel = false;
        for (MLProfileNodeResponse nodeResponse : allProfileAfterUploading.getNodes()) {
            MLModelProfile mlModelProfile = nodeResponse.getMlNodeModels().get(modelId);
            DiscoveryNode node = nodeResponse.getNode();
            String[] workerNodes = mlModelProfile.getWorkerNodes();
            Set<String> targetDataNodes = modelWorkerNodes.size() == 0 ? getAllDataNodeIds() : modelWorkerNodes;
            if (targetDataNodes.size() > 0) {
                assertEquals(targetDataNodes.size(), workerNodes.length);
                for (String nodeId : workerNodes) {
                    assertTrue(targetDataNodes.contains(nodeId));
                }
            }
            if (modelWorkerNodes.size() > 0 && !modelWorkerNodes.contains(node.getId())) {
                continue;
            }
            if (node.isDataNode() && mlModelProfile != null && mlModelProfile.getModelState() != null) {
                assertTrue(nodeResponse.getMlNodeModels().containsKey(modelId));
                assertEquals(MLModelState.LOADED, mlModelProfile.getModelState());
                hasLoadedModel = true;
                if (predictCounts == 0) {
                    assertNull(mlModelProfile.getModelInferenceStats());
                } else {
                    assertEquals(predictCounts, mlModelProfile.getModelInferenceStats().getCount().longValue());
                }
            }
        }
        assertTrue(hasLoadedModel);
    }
}
