/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.NOT_FOUND;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportUndeployModelsActionTests extends OpenSearchTestCase {

    @Mock
    TransportService transportService;

    @Mock
    ModelHelper modelHelper;

    @Mock
    MLTaskManager mlTaskManager;

    @Mock
    ClusterService clusterService;

    @Mock
    ThreadPool threadPool;

    @Mock
    private ClusterName clusterName;

    @Mock
    Client client;
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    ActionFilters actionFilters;

    @Mock
    DiscoveryNodeHelper nodeFilter;

    @Mock
    MLTaskDispatcher mlTaskDispatcher;

    @Mock
    MLModelManager mlModelManager;

    @Mock
    MLUndeployModelNodeResponse mlUndeployModelNodeResponse;

    @Mock
    ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    Task task;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    TransportUndeployModelsAction transportUndeployModelsAction;

    private String[] modelIds = { "modelId1" };

    private String[] nodeIds = { "nodeId1", "nodeId2" };

    private ActionListener<MLUndeployModelsResponse> actionListener = mock(ActionListener.class);

    ThreadContext threadContext;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = Mockito.spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));

        transportUndeployModelsAction = spy(
            new TransportUndeployModelsAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                sdkClient,
                settings,
                xContentRegistry,
                nodeFilter,
                mlTaskDispatcher,
                mlModelManager,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );
        when(modelAccessControlHelper.isModelAccessControlEnabled()).thenReturn(true);

        threadContext = new ThreadContext(Settings.builder().build());
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getSettings()).thenReturn(settings);
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name("Test Model")
            .modelId("someModelId")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(false)
            .build();
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));
    }

    public void testDoExecute_undeployModelIndex_WhenNoNodesServiceModel() {
        String modelId = "someModelId";
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name("Test Model")
            .modelId(modelId)
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(true)
            .build();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));

        doReturn(true).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse nodesResponse = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);

        // Send back a response with no nodes associated to the model. Thus, will write back to the model index that its UNDEPLOYED
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(nodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        ArgumentCaptor<BulkRequest> bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);

        BulkResponse bulkResponse = getSuccessBulkResponse();
        // mock the bulk response that can be captured for inspecting the contents of the write to index
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(bulkRequestCaptor.capture(), any(ActionListener.class));

        String[] modelIds = new String[] { modelId };
        String[] nodeIds = new String[] { "test_node_id1", "test_node_id2" };
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);

        transportUndeployModelsAction.doExecute(task, request, actionListener);

        BulkRequest capturedBulkRequest = bulkRequestCaptor.getValue();
        assertEquals(1, capturedBulkRequest.numberOfActions());
        UpdateRequest updateRequest = (UpdateRequest) capturedBulkRequest.requests().get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> updateDoc = updateRequest.doc().sourceAsMap();
        String modelIdFromBulkRequest = updateRequest.id();
        String indexNameFromBulkRequest = updateRequest.index();

        assertEquals("Check that the write happened at the model index", ML_MODEL_INDEX, indexNameFromBulkRequest);
        assertEquals("Check that the result bulk write hit this specific modelId", modelId, modelIdFromBulkRequest);

        assertEquals(MLModelState.UNDEPLOYED.name(), updateDoc.get(MLModel.MODEL_STATE_FIELD));
        assertEquals(0, updateDoc.get(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD));
        assertEquals(0, updateDoc.get(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD));
        assertEquals(List.of(), updateDoc.get(MLModel.PLANNING_WORKER_NODES_FIELD));
        assertTrue(updateDoc.containsKey(MLModel.LAST_UPDATED_TIME_FIELD));

        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
        verify(client).bulk(any(BulkRequest.class), any(ActionListener.class));
    }

    public void testDoExecute_noBulkRequestFired_WhenSomeNodesServiceModel() {
        String modelId = "someModelId";
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name("Test Model")
            .modelId(modelId)
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(true)
            .build();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));

        doReturn(true).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        responseList.add(mock(MLUndeployModelNodeResponse.class));
        responseList.add(mock(MLUndeployModelNodeResponse.class));
        List<FailedNodeException> failuresList = new ArrayList<>();
        failuresList.add(mock(FailedNodeException.class));
        failuresList.add(mock(FailedNodeException.class));

        MLUndeployModelNodesResponse nodesResponse = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);

        // Send back a response with nodes associated to the model
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(nodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        String[] modelIds = new String[] { modelId };
        String[] nodeIds = new String[] { "test_node_id1", "test_node_id2" };
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);

        transportUndeployModelsAction.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
        // Check that no bulk write occurred Since there were nodes servicing the model
        verify(client, never()).bulk(any(BulkRequest.class), any(ActionListener.class));
    }

    public void testHiddenModelSuccess() {
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name("Test Model")
            .modelId("someModelId")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(true)
            .build();
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        BulkResponse bulkResponse = getSuccessBulkResponse();
        // Mock the client.bulk call
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), any(ActionListener.class));

        doReturn(true).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
        verify(client).bulk(any(BulkRequest.class), any(ActionListener.class));
    }

    public void testDoExecute_bulkRequestFired_WhenModelNotFoundInAllNodes() {
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name(this.modelIds[0])
            .modelId(this.modelIds[0])
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(true)
            .build();

        // Mock MLModel manager response
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));

        doReturn(true).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();

        for (String nodeId : this.nodeIds) {
            Map<String, String> stats = new HashMap<>();
            stats.put(this.modelIds[0], NOT_FOUND);
            MLUndeployModelNodeResponse nodeResponse = mock(MLUndeployModelNodeResponse.class);
            when(nodeResponse.getModelUndeployStatus()).thenReturn(stats);
            responseList.add(nodeResponse);
        }

        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse nodesResponse = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(nodesResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        BulkResponse bulkResponse = getSuccessBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), any(ActionListener.class));

        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);

        transportUndeployModelsAction.doExecute(task, request, actionListener);

        // Verify that bulk request was fired because all nodes reported "not_found"
        verify(client).bulk(any(BulkRequest.class), any(ActionListener.class));
        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
    }

    public void testHiddenModelPermissionError() {
        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .modelGroupId("111")
            .version("111")
            .name("Test Model")
            .modelId("someModelId")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .isHidden(true)
            .build();
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        doReturn(false).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));

        BulkResponse bulkResponse = getSuccessBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());

        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));

        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
        verify(client).bulk(any(BulkRequest.class), any(ActionListener.class));
    }

    public void testDoExecute_modelAccessControl_notEnabled() {
        when(modelAccessControlHelper.isModelAccessControlEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        MLUndeployModelsResponse mlUndeployModelsResponse = new MLUndeployModelsResponse(mock(MLUndeployModelNodesResponse.class));
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelsResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(Exception.class));
    }

    public void testDoExecute_validate_false() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IllegalArgumentException());
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(IllegalArgumentException.class));
    }

    public void testDoExecute_getModel_exception() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(RuntimeException.class));
    }

    public void testDoExecute_validateAccess_exception() {
        doThrow(new RuntimeException("runtime exception"))
            .when(mlModelManager)
            .getModel(any(), any(), any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(RuntimeException.class));
    }

    public void testDoExecute_modelIds_moreThan1() {
        expectedException.expect(IllegalArgumentException.class);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(new String[] { "modelId1", "modelId2" }, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
    }

    private BulkResponse getSuccessBulkResponse() {
        return new BulkResponse(
            new BulkItemResponse[] {
                new BulkItemResponse(
                    1,
                    DocWriteRequest.OpType.UPDATE,
                    new UpdateResponse(new ShardId(ML_MODEL_INDEX, "modelId123", 0), "id1", 1, 1, 1, DocWriteResponse.Result.UPDATED)
                ) },
            100L
        );
    }
}
