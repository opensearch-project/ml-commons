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
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerRequest;
import org.opensearch.ml.common.transport.controller.MLCreateModelControllerResponse;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class CreateModelControllerTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLCreateModelControllerResponse> actionListener;

    @Mock
    IndexResponse indexResponse;

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
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    MLModel mlModel;

    @Mock
    MLDeployModelControllerNodesResponse mlDeployModelControllerNodesResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    CreateModelControllerTransportAction createModelControllerTransportAction;
    MLCreateModelControllerRequest createModelControllerRequest;
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

        createModelControllerTransportAction = spy(
            new CreateModelControllerTransportAction(
                transportService,
                actionFilters,
                mlIndicesHandler,
                client,
                clusterService,
                modelAccessControlHelper,
                mlModelCacheHelper,
                mlModelManager
            )
        );

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();

        MLModelController modelController = MLModelController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("testUser", rateLimiter);
            }
        }).build();

        createModelControllerRequest = MLCreateModelControllerRequest.builder().modelControllerInput(modelController).build();

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

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLModelControllerIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(isA(IndexRequest.class), isA(ActionListener.class));
        when(indexResponse.getId()).thenReturn("testModelId");
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

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
    public void testCreateModelControllerSuccess() {
        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        verify(actionListener).onResponse(any(MLCreateModelControllerResponse.class));
    }

    @Test
    public void testCreateModelControllerWithTextEmbeddingModelSuccess() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        verify(actionListener).onResponse(any(MLCreateModelControllerResponse.class));
    }

    @Test
    public void testCreateModelControllerWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller, model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testCreateModelControllerWithModelAccessControlOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithModelNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find model to create the corresponding model controller with the provided model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testCreateModelControllerWithModelStateDeploying() {
        when(mlModel.getModelState()).thenReturn(MLModelState.DEPLOYING);

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Creating a model controller during its corresponding model in DEPLOYING state is not allowed, please either create the model controller after it is deployed or before deploying it. Model ID: testModelId",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithModelFunctionUnsupported() {
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.METRICS_CORRELATION);

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Creating model controller on this operation on the function category METRICS_CORRELATION is not supported.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithIndexCreatedFailure() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLModelControllerIndex(isA(ActionListener.class));

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create model controller index.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithIndexCreatedOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlIndicesHandler).initMLModelControllerIndex(isA(ActionListener.class));

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithIndexResponseUpdated() {
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        verify(actionListener).onResponse(any(MLCreateModelControllerResponse.class));
    }

    @Test
    public void testCreateModelControllerWithDeploySuccessNullFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(null);

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        verify(actionListener).onResponse(any(MLCreateModelControllerResponse.class));
    }

    @Test
    public void testCreateModelControllerWithUndeploySuccessEmptyFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(new ArrayList<>());

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        verify(actionListener).onResponse(any(MLCreateModelControllerResponse.class));
    }

    @Test
    public void testCreateModelControllerWithUndeploySuccessPartiallyFailures() {
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlDeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());
        when(mlDeployModelControllerNodesResponse.failures()).thenReturn(failures);

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Successfully create model controller index with model ID testModelId "
                + "but deploy model controller to cache was failed on following nodes [foo1], please retry.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testCreateModelControllerWithUndeployNullResponse() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });
        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Successfully create model controller index with model ID testModelId " +
                        "but deploy model controller to cache was failed on following nodes [foo1, foo2], please retry.",
                argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateModelControllerWithUndeployOtherException() {
        when(mlModelCacheHelper.getWorkerNodes("testModelId")).thenReturn(new String[] { "foo1", "foo2" });

        doAnswer(invocation -> {
            ActionListener<MLDeployModelControllerNodesResponse> actionListener = invocation.getArgument(2);
            actionListener
                    .onFailure(
                            new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).execute(eq(MLDeployModelControllerAction.INSTANCE), any(), any());

        createModelControllerTransportAction.doExecute(null, createModelControllerRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Exception occurred. Please check log for more details.",
                argumentCaptor.getValue().getMessage());
    }
}
