/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.action.undeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

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

    private final String[] modelIds = { "modelId1" };

    private final String[] nodeIds = { "nodeId1", "nodeId2" };

    private final ActionListener<MLUndeployModelsResponse> actionListener = mock(ActionListener.class);

    ThreadContext threadContext;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        TransportUndeployModelsActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
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
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
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

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
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

        doReturn(true).when(transportUndeployModelsAction).isSuperAdminUserWrapper(clusterService, client);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
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
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds, null);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLUndeployModelsResponse.class));
    }

    public void testDoExecute_modelAccessControl_notEnabled() {
        when(modelAccessControlHelper.isModelAccessControlEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

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
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

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
}
