/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.transport.controller.MLDeployControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesResponse;
import org.opensearch.ml.common.transport.controller.MLUpdateControllerRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class UpdateControllerTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<UpdateResponse> actionListener;

    @Mock
    UpdateResponse updateResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    MLModelCacheHelper mlModelCacheHelper;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    MLModel mlModel;

    @Mock
    MLDeployControllerNodesResponse mlDeployControllerNodesResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MLController controller;
    MLController updatedController;
    UpdateControllerTransportAction updateControllerTransportAction;
    MLUpdateControllerRequest updateControllerRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        InetAddress inetAddress1 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 1 });
        InetAddress inetAddress2 = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 0, (byte) 2 });

        DiscoveryNode node1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(inetAddress1, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNode node2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(inetAddress2, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node1).add(node2).build();
        String[] targetNodeIds = new String[] { node1.getId(), node2.getId() };

        updateControllerTransportAction = spy(
            new UpdateControllerTransportAction(
                transportService,
                actionFilters,
                client,
                clusterService,
                modelAccessControlHelper,
                mlModelCacheHelper,
                mlModelManager
            )
        );

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();

        controller = MLController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("testUser", rateLimiter);
            }
        }).build();

        MLRateLimiter updateRateLimiter = MLRateLimiter.builder().limit("2").unit(TimeUnit.NANOSECONDS).build();

        updatedController = MLController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("newUser", updateRateLimiter);
            }
        }).build();

        updateControllerRequest = MLUpdateControllerRequest.builder().updateControllerInput(updatedController).build();

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getModelId()).thenReturn("testModelId");

        doAnswer(invocation -> {
            ActionListener<MLController> listener = invocation.getArgument(1);
            listener.onResponse(controller);
            return null;
        }).when(mlModelManager).getController(eq("testModelId"), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());
        when(updateResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(false);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.nodes()).thenReturn(nodes);
        when(mlModelManager.getWorkerNodes("testModelId", FunctionName.REMOTE)).thenReturn(targetNodeIds);
    }

    @Test
    public void testUpdateControllerSuccess() {
        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithTextEmbeddingModelSuccess() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithModelAccessControlNoPermissionHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithModelAccessControlOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateControllerWithModelAccessControlOtherExceptionHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Permission denied: Unable to create the model controller for the model. Details: "));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Permission denied: Unable to create the model controller for the model. Details: ",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithControllerEnabledNull() {
        doAnswer(invocation -> {
            ActionListener<MLController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsControllerEnabled()).thenReturn(null);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model controller haven't been created for the model. Consider calling create model controller api instead. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithControllerEnabledNullHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<MLController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsControllerEnabled()).thenReturn(null);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model controller haven't been created for the model. Consider calling create model controller api instead.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithControllerNotEnabled() {
        doAnswer(invocation -> {
            ActionListener<MLController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsControllerEnabled()).thenReturn(false);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model controller haven't been created for the model. Consider calling create model controller api instead. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithControllerEnabledNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsControllerEnabled()).thenReturn(true);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateControllerWithModelFunctionUnsupported() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.METRICS_CORRELATION);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Creating model controller on this operation on the function category METRICS_CORRELATION is not supported.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void tesUpdateControllerWithGetModelNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find model to create the corresponding model controller with the provided model ID",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUpdateResponseNoop() {
        when(updateResponse.getResult()).thenReturn(DocWriteResponse.Result.NOOP);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithNullUpdateResponse() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).update(any(), any());
        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update model controller. Model ID: testModelId", argumentCaptor.getValue().getMessage());

    }

    @Test
    public void testUpdateControllerWithDeploySuccessNullFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());
        when(mlDeployControllerNodesResponse.failures()).thenReturn(null);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithDeployNotRequiredAfterUpdateSuccess() {
        updateControllerRequest = MLUpdateControllerRequest.builder().updateControllerInput(controller).build();
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);
        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithModelNDeployedAndDeployNotRequiredAfterUpdateSuccess() {
        updateControllerRequest = MLUpdateControllerRequest.builder().updateControllerInput(controller).build();
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(false);
        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithUndeploySuccessEmptyFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());
        when(mlDeployControllerNodesResponse.failures()).thenReturn(new ArrayList<>());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateControllerWithUndeploySuccessPartiallyFailures() {
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());
        when(mlDeployControllerNodesResponse.failures()).thenReturn(failures);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update model controller index but deploy model controller to cache was failed on following nodes [foo1], please retry. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUndeploySuccessPartiallyFailuresHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        when(mlModel.getModelId()).thenReturn("testModelId");
        doAnswer(invocation -> {
            ActionListener<MLModel> mllistener = invocation.getArgument(3);
            mllistener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());
        when(mlDeployControllerNodesResponse.failures()).thenReturn(failures);

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update model controller index "
                + "but deploy model controller to cache was failed on following nodes [foo1], please retry.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUndeployNullResponse() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Successfully update model controller index but deploy model controller to cache was failed on following nodes [foo1, foo2], please retry. Model ID: testModelId",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateControllerWithUndeployNullResponseHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        when(mlModel.getModelId()).thenReturn("testModelId");
        doAnswer(invocation -> {
            ActionListener<MLModel> mllistener = invocation.getArgument(3);
            mllistener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update model controller index "
                + "but deploy model controller to cache was failed on following nodes [foo1, foo2], please retry.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateControllerWithUndeployOtherException() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> actionListener = invocation.getArgument(2);
            actionListener
                    .onFailure(
                            new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Exception occurred. Please check log for more details.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateControllerWithUndeployOtherExceptionHiddenModel() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(mlModel.getIsHidden()).thenReturn(Boolean.TRUE);
        when(mlModel.getModelId()).thenReturn("testModelId");
        doAnswer(invocation -> {
            ActionListener<MLModel> mllistener = invocation.getArgument(3);
            mllistener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployControllerNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).execute(eq(MLDeployControllerAction.INSTANCE), any(), any());

        updateControllerTransportAction.doExecute(null, updateControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

}
