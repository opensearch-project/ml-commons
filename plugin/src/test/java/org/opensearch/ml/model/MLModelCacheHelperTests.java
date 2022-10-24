/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLModelCacheHelperTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ClusterService clusterService;
    private Settings settings;

    private MLModelCacheHelper cacheHelper;

    private String modelId;
    private String nodeId;
    private TextEmbeddingModel predictor;
    private int maxMonitoringRequests;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        maxMonitoringRequests = 10;
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), maxMonitoringRequests).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_MONITORING_REQUEST_COUNT);
        clusterService = spy(new ClusterService(settings, clusterSettings, null));

        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        cacheHelper = new MLModelCacheHelper(clusterService, settings);

        modelId = "model_id1";
        nodeId = "node_id1";
        predictor = spy(new TextEmbeddingModel());
    }

    public void testModelState() {
        assertFalse(cacheHelper.isModelLoaded(modelId));
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        assertFalse(cacheHelper.isModelLoaded(modelId));
        cacheHelper.setModelState(modelId, MLModelState.LOADED);
        assertTrue(cacheHelper.isModelLoaded(modelId));
        assertEquals(FunctionName.TEXT_EMBEDDING, cacheHelper.getFunctionName(modelId));
    }

    public void testModelState_DuplicateError() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Duplicate model task");
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
    }

    public void testPredictor_NotFoundException() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Model not found in cache");
        cacheHelper.setPredictor("modelId1", predictor);
    }

    public void testPredictor() {
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        assertNull(cacheHelper.getPredictor(modelId));
        cacheHelper.setPredictor(modelId, predictor);
        assertEquals(predictor, cacheHelper.getPredictor(modelId));
    }

    public void testGetAndRemoveModel() {
        assertFalse(cacheHelper.isModelRunningOnNode(modelId));
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        String[] loadedModels = cacheHelper.getLoadedModels();
        assertEquals(0, loadedModels.length);

        assertTrue(cacheHelper.isModelRunningOnNode(modelId));

        cacheHelper.setModelState(modelId, MLModelState.LOADED);
        loadedModels = cacheHelper.getLoadedModels();
        assertArrayEquals(new String[] { modelId }, loadedModels);

        cacheHelper.removeModel(modelId);
        loadedModels = cacheHelper.getLoadedModels();
        assertEquals(0, loadedModels.length);
    }

    public void testRemoveModel_WrongModelId() {
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        cacheHelper.removeModel("wrong_model_id");
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
    }

    public void testModelLoaded() {
        cacheHelper.addWorkerNode(modelId, nodeId);
        String[] loadedModels = cacheHelper.getLoadedModels();
        assertEquals(0, loadedModels.length);

        String[] allModels = cacheHelper.getAllModels();
        assertArrayEquals(new String[] { modelId }, allModels);
    }

    public void testGetWorkerNode() {
        String[] workerNodes = cacheHelper.getWorkerNodes(modelId);
        assertNull(workerNodes);
        cacheHelper.addWorkerNode(modelId, nodeId);
        workerNodes = cacheHelper.getWorkerNodes(modelId);
        assertArrayEquals(new String[] { nodeId }, workerNodes);
    }

    public void testRemoveWorkerNode_NullModelState() {
        String nodeId2 = "node_id2";
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.addWorkerNode(modelId, nodeId2);
        assertEquals(2, cacheHelper.getWorkerNodes(modelId).length);

        cacheHelper.removeWorkerNode("wrong_model_id", nodeId);
        cacheHelper.removeWorkerNode(modelId, nodeId2);
        assertArrayEquals(new String[] { nodeId }, cacheHelper.getWorkerNodes(modelId));

        cacheHelper.removeWorkerNodes(ImmutableSet.of(nodeId));
        assertNull(cacheHelper.getWorkerNodes(modelId));

        cacheHelper.addWorkerNode(modelId, nodeId);
        assertArrayEquals(new String[] { nodeId }, cacheHelper.getWorkerNodes(modelId));
        cacheHelper.removeWorkerNode(modelId, nodeId);
        assertEquals(0, cacheHelper.getAllModels().length);
    }

    public void testRemoveWorkerNode_ModelState() {
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.setModelState(modelId, MLModelState.LOADING);
        cacheHelper.removeWorkerNodes(ImmutableSet.of(nodeId));
        assertEquals(0, cacheHelper.getWorkerNodes(modelId).length);
        assertTrue(cacheHelper.isModelRunningOnNode(modelId));

        cacheHelper.removeModel(modelId);
        assertFalse(cacheHelper.isModelRunningOnNode(modelId));
    }

    public void testRemoveModel_Loaded() {
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        cacheHelper.setModelState(modelId, MLModelState.LOADED);
        cacheHelper.setPredictor(modelId, predictor);
        cacheHelper.removeModel(modelId);
        verify(predictor, times(1)).close();
    }

    public void testClearWorkerNodes_NullModelState() {
        String modelId2 = "model_id2";
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.addWorkerNode(modelId2, nodeId);
        cacheHelper.clearWorkerNodes();
        assertEquals(0, cacheHelper.getAllModels().length);
    }

    public void testClearWorkerNodes_ModelState() {
        cacheHelper.initModelState(modelId, MLModelState.LOADED, FunctionName.TEXT_EMBEDDING);
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.clearWorkerNodes();
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
    }

    public void testClearWorkerNodes_WrongModelId() {
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.clearWorkerNodes("wrong_model_id");
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
    }

    public void testSyncWorkerNodes_NullModelState() {
        String modelId2 = "model_id2";
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.addWorkerNode(modelId2, nodeId);

        String newNodeId = "new_node_id";
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put(modelId, ImmutableSet.of(newNodeId));
        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(modelId));
    }

    public void testSyncWorkerNodes_ModelState() {
        String modelId2 = "model_id2";
        cacheHelper.initModelState(modelId2, MLModelState.LOADED, FunctionName.TEXT_EMBEDDING);
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.addWorkerNode(modelId2, nodeId);

        String newNodeId = "new_node_id";
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put(modelId, ImmutableSet.of(newNodeId));
        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertEquals(2, cacheHelper.getAllModels().length);
        assertEquals(0, cacheHelper.getWorkerNodes(modelId2).length);
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(modelId));
    }

    public void testSyncWorkerNodes_ModelState_NoModelLoaded() {
        cacheHelper.addWorkerNode(modelId, nodeId);

        String newModelId = "new_model_id";
        String newNodeId = "new_node_id";
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put(newModelId, ImmutableSet.of(newNodeId));
        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertArrayEquals(new String[] { newModelId }, cacheHelper.getAllModels());
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(newModelId));
        assertNull(cacheHelper.getWorkerNodes(modelId));

        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertArrayEquals(new String[] { newModelId }, cacheHelper.getAllModels());
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(newModelId));
        assertNull(cacheHelper.getWorkerNodes(modelId));
    }

    public void testGetModelProfile_WrongModelId() {
        MLModelProfile modelProfile = cacheHelper.getModelProfile(modelId);
        assertNull(modelProfile);
    }

    public void testGetModelProfile() {
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        cacheHelper.setModelState(modelId, MLModelState.LOADED);
        cacheHelper.setPredictor(modelId, predictor);
        cacheHelper.addWorkerNode(modelId, nodeId);
        MLModelProfile modelProfile = cacheHelper.getModelProfile(modelId);
        assertNotNull(modelProfile);
        assertTrue(modelProfile.getPredictor().contains("TextEmbeddingModel"));
        assertEquals(MLModelState.LOADED, modelProfile.getModelState());
        assertArrayEquals(new String[] { nodeId }, modelProfile.getWorkerNodes());
        assertNull(modelProfile.getPredictStats());

        for (int i = 1; i <= maxMonitoringRequests * 2; i++) {
            cacheHelper.addInferenceDuration(modelId, i);
        }
        MLPredictRequestStats predictStats = cacheHelper.getModelProfile(modelId).getPredictStats();
        assertNotNull(predictStats);
        assertEquals(maxMonitoringRequests + 1, predictStats.getMin(), 1e-5);
        assertEquals(maxMonitoringRequests * 2, predictStats.getMax(), 1e-5);
        assertEquals((maxMonitoringRequests + 1 + maxMonitoringRequests * 2) / 2.0, predictStats.getAverage(), 1e-5);
        assertEquals(maxMonitoringRequests, predictStats.getCount().longValue());
    }

    public void testGetModelProfile_Loading() {
        cacheHelper.initModelState(modelId, MLModelState.LOADING, FunctionName.TEXT_EMBEDDING);
        MLModelProfile modelProfile = cacheHelper.getModelProfile(modelId);
        assertNotNull(modelProfile);
        assertEquals(MLModelState.LOADING, modelProfile.getModelState());
        assertNull(modelProfile.getPredictor());
        assertNull(modelProfile.getWorkerNodes());
        assertNull(modelProfile.getPredictStats());
    }
}
