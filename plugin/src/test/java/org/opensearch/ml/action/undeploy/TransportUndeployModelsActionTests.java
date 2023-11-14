/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.action.undeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
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
    Client client;

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
    ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    Task task;

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
        transportUndeployModelsAction = new TransportUndeployModelsAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            modelAccessControlHelper
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
            .build();
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), isA(ActionListener.class));
    }

    public void testDoExecute() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        MLUndeployModelsResponse mlUndeployModelsResponse = new MLUndeployModelsResponse(mock(MLUndeployModelNodesResponse.class));
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelsResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(Exception.class));
    }

    public void testDoExecute_modelAccessControl_notEnabled() {
        when(modelAccessControlHelper.isModelAccessControlEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        MLUndeployModelsResponse mlUndeployModelsResponse = new MLUndeployModelsResponse(mock(MLUndeployModelNodesResponse.class));
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelsResponse);
            return null;
        }).when(client).execute(any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds);
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
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(IllegalArgumentException.class));
    }

    public void testDoExecute_getModel_exception() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(mlModelManager).getModel(any(), any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(RuntimeException.class));
    }

    public void testDoExecute_validateAccess_exception() {
        doThrow(new RuntimeException("runtime exception")).when(mlModelManager).getModel(any(), any(), any(), isA(ActionListener.class));
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(modelIds, nodeIds);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(isA(RuntimeException.class));
    }

    public void testDoExecute_modelIds_moreThan1() {
        expectedException.expect(IllegalArgumentException.class);
        MLUndeployModelsRequest request = new MLUndeployModelsRequest(new String[] { "modelId1", "modelId2" }, nodeIds);
        transportUndeployModelsAction.doExecute(task, request, actionListener);
    }
}
