/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.syncup;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TransportSyncUpOnNodeActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ModelHelper modelHelper;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLEngine mlEngine;

    private Settings settings;

    public TemporaryFolder testFolder = new TemporaryFolder();

    private TransportSyncUpOnNodeAction action;

    private Map<String, Set<String>> runningDeployModelTasks;

    @Mock
    private MLModelCacheHelper mlModelCacheHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mockSettings(true);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));
        action = new TransportSyncUpOnNodeAction(
            transportService,
            settings,
            actionFilters,
            modelHelper,
            mlTaskManager,
            mlModelManager,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            mlEngine,
            mlModelCacheHelper
        );
        runningDeployModelTasks = new HashMap<>();
        runningDeployModelTasks.put("model1", ImmutableSet.of("node1"));
        when(mlTaskManager.getLocalRunningDeployModelTasks())
            .thenReturn(Arrays.asList(new String[] { "deploy_task_id1" }, new String[] { "model_id1" }));
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testNewResponse() {
        final MLSyncUpNodesRequest nodesRequest = Mockito.mock(MLSyncUpNodesRequest.class);
        final List<MLSyncUpNodeResponse> responses = new ArrayList<MLSyncUpNodeResponse>();
        final List<FailedNodeException> failures = new ArrayList<FailedNodeException>();
        final MLSyncUpNodesResponse response = action.newResponse(nodesRequest, responses, failures);
        assertNotNull(response);
    }

    public void testNewRequest() {
        final MLSyncUpNodeRequest request = action.newNodeRequest(new MLSyncUpNodesRequest(new String[] {}, prepareRequest()));
        assertNotNull(request);
    }

    public void testNewNodeResponse() throws IOException {
        final DiscoveryNode mlNode1 = new DiscoveryNode(
            "123",
            buildNewFakeTransportAddress(),
            emptyMap(),
            ImmutableSet.of(ML_ROLE),
            Version.CURRENT
        );
        String[] deployedModelIds = new String[] { "123" };
        String[] runningDeployModelIds = new String[] { "model1" };
        String[] runningDeployModelTaskIds = new String[] { "1" };
        MLSyncUpNodeResponse response = new MLSyncUpNodeResponse(
            mlNode1,
            "DEPLOYED",
            deployedModelIds,
            runningDeployModelIds,
            runningDeployModelTaskIds
        );
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        final MLSyncUpNodeResponse response1 = action.newNodeResponse(output.bytes().streamInput());
        assertNotNull(response1);
    }

    public void testNodeOperation_AddedWorkerNodes() throws IOException {
        testFolder.create();
        File file1 = testFolder.newFolder();
        File file2 = testFolder.newFolder();
        File file3 = testFolder.newFolder();
        for (int i = 0; i < 5; i++) {
            File.createTempFile("Hello" + i, "1.txt", file1);
            File.createTempFile("Hello" + i, "1.txt", file2);
            File.createTempFile("Hello" + i, "1.txt", file3);
        }
        when(mlEngine.getModelCachePath(any())).thenReturn(Paths.get(file3.getCanonicalPath()));
        when(mlEngine.getDeployModelPath(any())).thenReturn(Paths.get(file2.getCanonicalPath()));
        when(mlEngine.getRegisterModelPath(any())).thenReturn(Paths.get(file1.getCanonicalPath()));
        DiscoveryNode localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.localNode()).thenReturn(localNode);
        when(mlEngine.getRegisterModelRootPath()).thenReturn(Paths.get(file1.getCanonicalPath()));
        when(mlEngine.getDeployModelRootPath()).thenReturn(Paths.get(file2.getCanonicalPath()));
        when(mlEngine.getModelCacheRootPath()).thenReturn(Paths.get(file3.getCanonicalPath()));
        final MLSyncUpNodeRequest request = action.newNodeRequest(new MLSyncUpNodesRequest(new String[] {}, prepareRequest()));
        final MLSyncUpNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
        file1.deleteOnExit();
        file2.deleteOnExit();
        file3.deleteOnExit();
        testFolder.delete();
    }

    public void testNodeOperation_RemovedWorkerNodes() throws IOException {
        testFolder.create();
        File file1 = testFolder.newFolder();
        File file2 = testFolder.newFolder();
        File file3 = testFolder.newFolder();
        for (int i = 0; i < 5; i++) {
            File.createTempFile("Hello" + i, "1.txt", file1);
            File.createTempFile("Hello" + i, "1.txt", file2);
            File.createTempFile("Hello" + i, "1.txt", file3);
        }
        when(mlEngine.getModelCachePath(any())).thenReturn(Paths.get(file3.getCanonicalPath()));
        when(mlEngine.getDeployModelPath(any())).thenReturn(Paths.get(file2.getCanonicalPath()));
        when(mlEngine.getRegisterModelPath(any())).thenReturn(Paths.get(file1.getCanonicalPath()));
        DiscoveryNode localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.localNode()).thenReturn(localNode);
        when(mlEngine.getRegisterModelRootPath()).thenReturn(Paths.get(file1.getCanonicalPath()));
        when(mlEngine.getDeployModelRootPath()).thenReturn(Paths.get(file2.getCanonicalPath()));
        when(mlEngine.getModelCacheRootPath()).thenReturn(Paths.get(file3.getCanonicalPath()));
        when(mlTaskManager.contains(any())).thenReturn(true);
        when(mlTaskManager.containsModel(any())).thenReturn(true);
        when(mlModelManager.isModelRunningOnNode(anyString())).thenReturn(true);
        final MLSyncUpNodeRequest request = action.newNodeRequest(new MLSyncUpNodesRequest(new String[] {}, prepareRequest2()));
        final MLSyncUpNodeResponse response = action.nodeOperation(request);
        assertNotNull(response);
        file1.deleteOnExit();
        file2.deleteOnExit();
        file3.deleteOnExit();
        testFolder.delete();
    }

    public void testCleanUpLocalCache_NoTasks() {
        when(mlTaskManager.getAllTaskIds()).thenReturn(null);
        action.cleanUpLocalCache(runningDeployModelTasks);
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testCleanUpLocalCache_EmptyTasks() {
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] {});
        action.cleanUpLocalCache(runningDeployModelTasks);
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testCleanUpLocalCache_NotExpiredMLTask() {
        String taskId = randomAlphaOfLength(5);
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] { taskId });
        MLTask mlTask = MLTask.builder().lastUpdateTime(Instant.now()).build();
        MLTaskCache taskCache = MLTaskCache.builder().mlTask(mlTask).build();
        when(mlTaskManager.getMLTaskCache(taskId)).thenReturn(taskCache);
        action.cleanUpLocalCache(runningDeployModelTasks);
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testCleanUpLocalCache_ExpiredMLTask_Register() {
        String taskId = randomAlphaOfLength(5);
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] { taskId });
        MLTask mlTask = MLTask.builder().taskType(MLTaskType.REGISTER_MODEL).lastUpdateTime(Instant.now().minusSeconds(86400)).build();
        MLTaskCache taskCache = MLTaskCache.builder().mlTask(mlTask).build();
        when(mlTaskManager.getMLTaskCache(taskId)).thenReturn(taskCache);
        action.cleanUpLocalCache(runningDeployModelTasks);
        verify(mlTaskManager, times(1)).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
        verify(mlModelManager, never()).updateModel(anyString(), any(), any());
    }

    public void testCleanUpLocalCache_ExpiredMLTask_Deploy_NullWorkerNode() {
        testCleanUpLocalCache_ExpiredMLTask_DeployStatus(MLModelState.DEPLOY_FAILED);
    }

    public void testCleanUpLocalCache_ExpiredMLTask_Deploy_PartiallyDEPLOYED() {
        testCleanUpLocalCache_ExpiredMLTask_DeployStatus(MLModelState.PARTIALLY_DEPLOYED);
    }

    public void testCleanUpLocalCache_ExpiredMLTask_Deploy_DEPLOYED() {
        testCleanUpLocalCache_ExpiredMLTask_DeployStatus(MLModelState.DEPLOYED);
    }

    private void testCleanUpLocalCache_ExpiredMLTask_DeployStatus(MLModelState modelState) {
        String taskId = randomAlphaOfLength(5);
        String modelId = randomAlphaOfLength(5);
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] { taskId });
        MLTask.MLTaskBuilder mlTaskBuilder = MLTask
            .builder()
            .modelId(modelId)
            .taskType(MLTaskType.DEPLOY_MODEL)
            .lastUpdateTime(Instant.now().minusSeconds(86400));
        if (MLModelState.PARTIALLY_DEPLOYED == modelState) {
            mlTaskBuilder.workerNodes(ImmutableList.of("node1", "node2"));
        } else if (MLModelState.DEPLOYED == modelState) {
            mlTaskBuilder.workerNodes(ImmutableList.of("node1"));
        }

        MLTask mlTask = mlTaskBuilder.build();
        MLTaskCache taskCache = MLTaskCache.builder().mlTask(mlTask).build();
        if (MLModelState.DEPLOY_FAILED != modelState) {
            when(mlModelManager.getWorkerNodes(modelId, FunctionName.REMOTE)).thenReturn(new String[] { "node1" });
        }
        when(mlTaskManager.getMLTaskCache(taskId)).thenReturn(taskCache);
        action.cleanUpLocalCache(runningDeployModelTasks);
        verify(mlTaskManager, times(1)).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager, never()).updateModel(eq(modelId), eq(false), argumentCaptor.capture());
    }

    private MLSyncUpInput prepareRequest() {
        Map<String, String[]> addedWorkerNodes = new HashMap<>();
        addedWorkerNodes.put("modelId1", new String[] { "nodeId1", "nodeId2", "nodeId3" });
        Map<String, Set<String>> modelRoutingTable = new HashMap<>();
        Map<String, Set<String>> runningDeployModelTasks = new HashMap<>();
        final HashSet<String> set = new HashSet<>();
        set.addAll(Arrays.asList(new String[] { "nodeId3", "nodeId4", "nodeId5" }));
        modelRoutingTable.put("modelId2", set);
        MLSyncUpInput syncUpInput = MLSyncUpInput
            .builder()
            .getDeployedModels(true)
            .addedWorkerNodes(addedWorkerNodes)
            .modelRoutingTable(modelRoutingTable)
            .runningDeployModelTasks(runningDeployModelTasks)
            .clearRoutingTable(true)
            .syncRunningDeployModelTasks(true)
            .build();
        return syncUpInput;
    }

    private MLSyncUpInput prepareRequest2() {
        Map<String, String[]> removedWorkerNodes = new HashMap<>();
        removedWorkerNodes.put("modelId2", new String[] { "nodeId3", "nodeId4", "nodeId5" });
        Map<String, Set<String>> modelRoutingTable = new HashMap<>();
        Map<String, Set<String>> runningDeployModelTasks = new HashMap<>();
        final HashSet<String> set = new HashSet<>();
        set.addAll(Arrays.asList(new String[] { "nodeId3", "nodeId4", "nodeId5" }));
        modelRoutingTable.put("modelId2", set);
        MLSyncUpInput syncUpInput = MLSyncUpInput
            .builder()
            .getDeployedModels(true)
            .removedWorkerNodes(removedWorkerNodes)
            .modelRoutingTable(modelRoutingTable)
            .runningDeployModelTasks(runningDeployModelTasks)
            .clearRoutingTable(false)
            .syncRunningDeployModelTasks(true)
            .build();
        return syncUpInput;
    }

    private void mockSettings(boolean onlyRunOnMLNode) {
        settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), onlyRunOnMLNode)
            .put(ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS.getKey(), 30)
            .build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ONLY_RUN_ON_ML_NODE, ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
    }
}
