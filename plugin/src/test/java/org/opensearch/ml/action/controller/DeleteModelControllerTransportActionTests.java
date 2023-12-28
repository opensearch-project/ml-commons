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
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.delete.DeleteResponse;
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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.transport.controller.MLModelControllerDeleteRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteModelControllerTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    DeleteResponse deleteResponse;

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
    MLModelController mlModelController;

    @Mock
    MLUndeployModelControllerNodesResponse mlUndeployModelControllerNodesResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteModelControllerTransportAction deleteModelControllerTransportAction;
    MLModelControllerDeleteRequest mlModelControllerDeleteRequest;
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

        deleteModelControllerTransportAction = spy(
            new DeleteModelControllerTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                clusterService,
                mlModelManager,
                mlModelCacheHelper,
                modelAccessControlHelper
            )
        );

        mlModelControllerDeleteRequest = MLModelControllerDeleteRequest.builder().modelId("testModelId").build();

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

        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onResponse(mlModelController);
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(false);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.nodes()).thenReturn(nodes);
    }

    @Test
    public void testDeleteModelControllerSuccess() {
        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Test
    public void testDeleteModelControllerWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller, model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testDeleteModelControllerWithModelAccessControlOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModelControllerWithGetModelNotFoundSuccess() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Test
    public void testDeleteModelControllerOtherException() {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).delete(any(), any());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModelControllerWithGetModelControllerOtherException() {
        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModelControllerWithGetModelNotFoundWithGetModelControllerOtherException() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLModelController> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModelController(eq("testModelId"), isA(ActionListener.class));

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteModelControllerWithUndeploySuccessNullFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLUndeployModelControllerAction.INSTANCE), any(), any());
        when(mlUndeployModelControllerNodesResponse.failures()).thenReturn(null);

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Test
    public void testDeleteModelControllerWithUndeploySuccessEmptyFailures() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLUndeployModelControllerAction.INSTANCE), any(), any());
        when(mlUndeployModelControllerNodesResponse.failures()).thenReturn(new ArrayList<>());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    @Test
    public void testDeleteModelControllerWithUndeploySuccessPartiallyFailures() {
        List<FailedNodeException> failures = List
            .of(new FailedNodeException("foo1", "Undeploy failed.", new RuntimeException("Exception occurred.")));
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelControllerNodesResponse);
            return null;
        }).when(client).execute(eq(MLUndeployModelControllerAction.INSTANCE), any(), any());
        when(mlUndeployModelControllerNodesResponse.failures()).thenReturn(failures);

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to undeploy model controller with model ID testModelId on following nodes [foo1], deletion is aborted. Please retry or undeploy the model manually and then perform the deletion.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testDeleteModelControllerWithUndeployNullResponse() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelControllerNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLUndeployModelControllerAction.INSTANCE), any(), any());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Failed to undeploy model controller with model ID testModelId on following nodes [foo1, foo2], deletion is aborted. Please retry or undeploy the model manually and then perform the deletion.",
                argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testDeleteModelControllerWithUndeployOtherException() {
        when(mlModelCacheHelper.isModelDeployed("testModelId")).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLUndeployModelControllerNodesResponse> actionListener = invocation.getArgument(2);
            actionListener
                    .onFailure(
                            new RuntimeException("Exception occurred. Please check log for more details.")
                    );
            return null;
        }).when(client).execute(eq(MLUndeployModelControllerAction.INSTANCE), any(), any());

        deleteModelControllerTransportAction.doExecute(null, mlModelControllerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Exception occurred. Please check log for more details.",
                argumentCaptor.getValue().getMessage()
        );
    }
}
