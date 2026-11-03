/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.deploy;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.deploy.MLDeployModelInput;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodeRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesRequest;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * A worker that finished deploying must keep the model when the task's coordinating node has left the cluster. Before
 * the fix, reporting the result to a missing node threw inside the model manager's deploy listener, and the model manager
 * reacted by unloading the model the worker had just deployed.
 */
public class TransportDeployModelOnNodeActionCoordinatorGoneTests extends OpenSearchTestCase {

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
    private MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    private MLStats mlStats;

    private TransportDeployModelOnNodeAction action;
    private DiscoveryNode worker;
    private DiscoveryNode coordinator;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.EMPTY));
        worker = new DiscoveryNode("worker", buildNewFakeTransportAddress(), emptyMap(), emptySet(), Version.CURRENT);
        coordinator = new DiscoveryNode("coordinator", buildNewFakeTransportAddress(), emptyMap(), emptySet(), Version.CURRENT);
        when(clusterService.localNode()).thenReturn(worker);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("test"));
        action = new TransportDeployModelOnNodeAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            mlModelManager,
            clusterService,
            threadPool,
            client,
            NamedXContentRegistry.EMPTY,
            mlCircuitBreakerService,
            mlStats
        );
    }

    private void clusterHas(DiscoveryNode... nodes) {
        DiscoveryNodes.Builder builder = DiscoveryNodes.builder();
        for (DiscoveryNode node : nodes) {
            builder.add(node);
        }
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).nodes(builder).build());
    }

    private MLDeployModelNodeRequest request() {
        MLTask mlTask = MLTask
            .builder()
            .taskId("task1")
            .modelId("model1")
            .taskType(MLTaskType.DEPLOY_MODEL)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .state(MLTaskState.CREATED)
            .workerNodes(List.of("worker"))
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();
        MLDeployModelInput input = new MLDeployModelInput("model1", "task1", "hash", 1, "coordinator", true, mlTask, null);
        return new MLDeployModelNodeRequest(new MLDeployModelNodesRequest(new DiscoveryNode[] { worker }, input));
    }

    /** Captures what the model manager's own listener sees: an exception here would make it unload the model. */
    private AtomicReference<Exception> deploySucceedsAndCaptureListenerException() {
        AtomicReference<Exception> thrown = new AtomicReference<>();
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(7);
            try {
                listener.onResponse("successful");
            } catch (Exception e) {
                thrown.set(e);
            }
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
        return thrown;
    }

    public void testCoordinatorLeft_deployResultNotSent_listenerDoesNotThrow() {
        clusterHas(worker);
        AtomicReference<Exception> thrown = deploySucceedsAndCaptureListenerException();

        assertNotNull(action.nodeOperation(request()));

        assertNull("the deploy listener must not throw, or the model manager unloads the model", thrown.get());
        verify(transportService, never()).sendRequest(any(), anyString(), any(), any());
    }

    public void testCoordinatorPresent_deployResultSent() {
        clusterHas(worker, coordinator);
        AtomicReference<Exception> thrown = deploySucceedsAndCaptureListenerException();

        action.nodeOperation(request());

        assertNull(thrown.get());
        ArgumentCaptor<DiscoveryNode> target = ArgumentCaptor.forClass(DiscoveryNode.class);
        verify(transportService).sendRequest(target.capture(), eq(MLForwardAction.NAME), any(MLForwardRequest.class), any());
        assertEquals("coordinator", target.getValue().getId());
    }

    public void testSendFails_listenerDoesNotThrow() {
        clusterHas(worker, coordinator);
        doThrow(new IllegalStateException("transport closed")).when(transportService).sendRequest(any(), anyString(), any(), any());
        AtomicReference<Exception> thrown = deploySucceedsAndCaptureListenerException();

        action.nodeOperation(request());

        assertNull(thrown.get());
    }

    public void testCoordinatorLeft_failureResultNotSent_listenerDoesNotThrow() {
        clusterHas(worker);
        AtomicReference<Exception> thrown = new AtomicReference<>();
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(7);
            try {
                listener.onFailure(new RuntimeException("deploy failed"));
            } catch (Exception e) {
                thrown.set(e);
            }
            return null;
        }).when(mlModelManager).deployModel(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());

        action.nodeOperation(request());

        assertNull(thrown.get());
        verify(transportService, never()).sendRequest(any(), anyString(), any(), any());
    }
}
