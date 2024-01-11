/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.test.OpenSearchTestCase;

public class MLModelCacheHelperTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ClusterService clusterService;
    private Settings settings;

    private MLModelCacheHelper cacheHelper;

    private String modelId;
    private String nodeId;
    private TextEmbeddingDenseModel predictor;
    private int maxMonitoringRequests;

    private List<String> targetWorkerNodes;

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
        predictor = spy(new TextEmbeddingDenseModel());
        targetWorkerNodes = new ArrayList<>();
        targetWorkerNodes.add(nodeId);
    }

    public void testModelState() {
        assertFalse(cacheHelper.isModelDeployed(modelId));
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertFalse(cacheHelper.isModelDeployed(modelId));
        cacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
        assertTrue(cacheHelper.isModelDeployed(modelId));
        assertEquals(FunctionName.TEXT_EMBEDDING, cacheHelper.getFunctionName(modelId));
    }

    public void testMemSizeEstimationCPU() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertTrue(cacheHelper.getMemEstCPU(modelId) == null);
        cacheHelper.setMemSizeEstimation(modelId, MLModelFormat.TORCH_SCRIPT, 1000L);
        assertTrue(cacheHelper.getMemEstCPU(modelId) == 1200L);
    }

    public void testMemSizeEstimationCPUONNX() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertTrue(cacheHelper.getMemEstCPU(modelId) == null);
        cacheHelper.setMemSizeEstimation(modelId, MLModelFormat.ONNX, 1000L);
        assertTrue(cacheHelper.getMemEstCPU(modelId) == 1500L);
    }

    public void testMemSizeEstimationGPU() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertTrue(cacheHelper.getMemEstGPU(modelId) == null);
        cacheHelper.setMemSizeEstimation(modelId, MLModelFormat.TORCH_SCRIPT, 1000L);
        assertTrue(cacheHelper.getMemEstGPU(modelId) == 1200L);
    }

    public void testMemSizeEstimationGPUONNX() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertTrue(cacheHelper.getMemEstGPU(modelId) == null);
        cacheHelper.setMemSizeEstimation(modelId, MLModelFormat.ONNX, 1000L);
        assertTrue(cacheHelper.getMemEstGPU(modelId) == 1500L);
    }

    public void testModelState_DuplicateError() {
        expectedEx.expect(MLLimitExceededException.class);
        expectedEx.expectMessage("Duplicate deploy model task");
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
    }

    public void testPredictor_NotFoundException() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Model not found in cache");
        cacheHelper.setPredictor("modelId1", predictor);
    }

    public void testPredictor() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertNull(cacheHelper.getPredictor(modelId));
        cacheHelper.setPredictor(modelId, predictor);
        assertEquals(predictor, cacheHelper.getPredictor(modelId));
    }

    public void testGetAndRemoveModel() {
        assertFalse(cacheHelper.isModelRunningOnNode(modelId));
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        String[] deployedModels = cacheHelper.getDeployedModels();
        assertEquals(0, deployedModels.length);

        assertTrue(cacheHelper.isModelRunningOnNode(modelId));

        cacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
        deployedModels = cacheHelper.getDeployedModels();
        assertArrayEquals(new String[] { modelId }, deployedModels);

        cacheHelper.removeModel(modelId);
        deployedModels = cacheHelper.getDeployedModels();
        assertEquals(0, deployedModels.length);
    }

    public void testRemoveModel_WrongModelId() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.removeModel("wrong_model_id");
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
    }

    public void testModelDeployed() {
        cacheHelper.addWorkerNode(modelId, nodeId);
        String[] deployedModels = cacheHelper.getDeployedModels();
        assertEquals(0, deployedModels.length);

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

        cacheHelper.removeWorkerNode("wrong_model_id", nodeId, false);
        cacheHelper.removeWorkerNode(modelId, nodeId2, true);
        assertArrayEquals(new String[] { nodeId }, cacheHelper.getWorkerNodes(modelId));

        cacheHelper.removeWorkerNodes(Set.of(nodeId), true);
        assertNull(cacheHelper.getWorkerNodes(modelId));

        cacheHelper.addWorkerNode(modelId, nodeId);
        assertArrayEquals(new String[] { nodeId }, cacheHelper.getWorkerNodes(modelId));
        cacheHelper.removeWorkerNode(modelId, nodeId, false);
        assertEquals(0, cacheHelper.getAllModels().length);
    }

    public void testRemoveWorkerNode_ModelState() {
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.setModelState(modelId, MLModelState.DEPLOYING);
        cacheHelper.removeWorkerNodes(Set.of(nodeId), false);
        assertEquals(0, cacheHelper.getWorkerNodes(modelId).length);
        assertTrue(cacheHelper.isModelRunningOnNode(modelId));

        cacheHelper.removeModel(modelId);
        assertFalse(cacheHelper.isModelRunningOnNode(modelId));
    }

    public void testRemoveModel_Deployed() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
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
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYED, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
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
        modelWorkerNodes.put(modelId, Set.of(newNodeId));
        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertArrayEquals(new String[] { modelId }, cacheHelper.getAllModels());
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(modelId));
    }

    public void testSyncWorkerNodes_ModelState() {
        String modelId2 = "model_id2";
        cacheHelper.initModelState(modelId2, MLModelState.DEPLOYED, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.addWorkerNode(modelId2, nodeId);

        String newNodeId = "new_node_id";
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put(modelId, Set.of(newNodeId));
        cacheHelper.syncWorkerNodes(modelWorkerNodes);
        assertEquals(2, cacheHelper.getAllModels().length);
        assertEquals(0, cacheHelper.getWorkerNodes(modelId2).length);
        assertNull(cacheHelper.getModelInfo(modelId2));
        assertArrayEquals(new String[] { newNodeId }, cacheHelper.getWorkerNodes(modelId));
    }

    public void testSyncWorkerNodes_ModelState_NoModelDeployed() {
        cacheHelper.addWorkerNode(modelId, nodeId);

        String newModelId = "new_model_id";
        String newNodeId = "new_node_id";
        Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
        modelWorkerNodes.put(newModelId, Set.of(newNodeId));
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
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.setModelState(modelId, MLModelState.DEPLOYED);
        cacheHelper.setPredictor(modelId, predictor);
        cacheHelper.addWorkerNode(modelId, nodeId);
        MLModelProfile modelProfile = cacheHelper.getModelProfile(modelId);
        assertNotNull(modelProfile);
        assertTrue(modelProfile.getPredictor().contains("TextEmbeddingDenseModel"));
        assertEquals(MLModelState.DEPLOYED, modelProfile.getModelState());
        assertArrayEquals(new String[] { nodeId }, modelProfile.getWorkerNodes());
        assertNull(modelProfile.getModelInferenceStats());

        for (int i = 1; i <= maxMonitoringRequests * 2; i++) {
            cacheHelper.addModelInferenceDuration(modelId, i);
        }
        MLPredictRequestStats predictStats = cacheHelper.getModelProfile(modelId).getModelInferenceStats();
        assertNotNull(predictStats);
        assertEquals(maxMonitoringRequests + 1, predictStats.getMin(), 1e-5);
        assertEquals(maxMonitoringRequests * 2, predictStats.getMax(), 1e-5);
        assertEquals((maxMonitoringRequests + 1 + maxMonitoringRequests * 2) / 2.0, predictStats.getAverage(), 1e-5);
        assertEquals(maxMonitoringRequests, predictStats.getCount().longValue());
    }

    public void testGetModelProfile_Deploying() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        MLModelProfile modelProfile = cacheHelper.getModelProfile(modelId);
        assertNotNull(modelProfile);
        assertEquals(MLModelState.DEPLOYING, modelProfile.getModelState());
        assertNull(modelProfile.getPredictor());
        assertNull(modelProfile.getWorkerNodes());
        assertNull(modelProfile.getModelInferenceStats());
    }

    public void testGetFunctionName() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYING, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        assertEquals(FunctionName.TEXT_EMBEDDING, cacheHelper.getFunctionName(modelId));
        assertEquals(FunctionName.TEXT_EMBEDDING, cacheHelper.getOptionalFunctionName(modelId).get());
        assertFalse(cacheHelper.getOptionalFunctionName(randomAlphaOfLength(10)).isPresent());
    }

    public void test_removeWorkerNodes_with_deployToAllNodesStatus_isTrue() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYED, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        cacheHelper.addWorkerNode(modelId, nodeId);
        cacheHelper.removeWorkerNodes(Set.of(nodeId), false);
        cacheHelper.removeWorkerNode(modelId, nodeId, false);
        assertEquals(0, cacheHelper.getWorkerNodes(modelId).length);
        assertNull(cacheHelper.getModelInfo(modelId));
    }

    public void test_setModelInfo_success() {
        cacheHelper.initModelState(modelId, MLModelState.DEPLOYED, FunctionName.TEXT_EMBEDDING, targetWorkerNodes, true);
        MLModel model = mock(MLModel.class);
        when(model.getModelId()).thenReturn("mockId");
        cacheHelper.setModelInfo(modelId, model);
        assertEquals("mockId", cacheHelper.getModelInfo(modelId).getModelId());
    }

}
