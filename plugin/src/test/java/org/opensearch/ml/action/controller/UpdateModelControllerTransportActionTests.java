/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
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
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesResponse;
import org.opensearch.ml.common.transport.controller.MLUpdateModelControllerRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class UpdateModelControllerTransportActionTests extends OpenSearchTestCase {
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
    MLDeployModelControllerNodesResponse mlDeployModelControllerNodesResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MLModelController modelController;
    MLModelController updatedModelController;
    UpdateModelControllerTransportAction updateModelControllerTransportAction;
    MLUpdateModelControllerRequest updateModelControllerRequest;
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

        updateModelControllerTransportAction = spy(
            new UpdateModelControllerTransportAction(
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

        modelController = MLModelController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("testUser", rateLimiter);
            }
        }).build();

        MLRateLimiter updateRateLimiter = MLRateLimiter.builder().limit("2").unit(TimeUnit.NANOSECONDS).build();

        updatedModelController = MLModelController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("newUser", updateRateLimiter);
            }
        }).build();

        updateModelControllerRequest = MLUpdateModelControllerRequest.builder().updateModelControllerInput(updatedModelController).build();

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
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onResponse(modelController);
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));

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
    public void testUpdateModelControllerSuccess() {
        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithTextEmbeddingModelSuccess() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller, model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelControllerWithModelAccessControlOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelControllerWithModelControllerEnabledNull() {
        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsModelControllerEnabled()).thenReturn(null);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model controller haven't been created for the model. Consider calling create model controller api instead. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelControllerWithModelControllerNotEnabled() {
        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsModelControllerEnabled()).thenReturn(false);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Model controller haven't been created for the model. Consider calling create model controller api instead. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelControllerWithModelControllerEnabledNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));
        when(mlModel.getIsModelControllerEnabled()).thenReturn(true);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelControllerWithModelFunctionUnsupported() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.METRICS_CORRELATION);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Creating model controller on this operation on the function category METRICS_CORRELATION is not supported.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void tesUpdateModelControllerWithGetModelNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find model to create the corresponding model controller with the provided model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelControllerWithUpdateResponseNoop() {
        when(updateResponse.getResult()).thenReturn(DocWriteResponse.Result.NOOP);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithNullUpdateResponse() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).update(any(), any());
        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update model controller with model ID: testModelId", argumentCaptor.getValue().getMessage());

    }

    @Test
    public void testUpdateModelControllerWithDeploySuccessNullFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(null);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithDeployNotRequiredAfterUpdateSuccess() {
        updateModelControllerRequest = MLUpdateModelControllerRequest.builder().updateModelControllerInput(modelController).build();
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);
        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithModelNDeployedAndDeployNotRequiredAfterUpdateSuccess() {
        updateModelControllerRequest = MLUpdateModelControllerRequest.builder().updateModelControllerInput(modelController).build();
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(false);
        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithUndeploySuccessEmptyFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(new ArrayList<>());

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testUpdateModelControllerWithUndeploySuccessPartiallyFailures() {
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(failures);

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully update model controller index with model ID testModelId "
                + "but deploy model controller to cache was failed on following nodes [foo1], please retry.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testUpdateModelControllerWithUndeployNullResponse() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Successfully update model controller index with model ID testModelId " +
                        "but deploy model controller to cache was failed on following nodes [foo1, foo2], please retry.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateModelControllerWithUndeployOtherException() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> actionListener = invocation.getArgument(2);
            actionListener
                    .onFailure(
                            new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());

        updateModelControllerTransportAction.doExecute(null, updateModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Exception occurred. Please check log for more details.",
                argumentCaptor.getValue().getMessage());
    }

}
