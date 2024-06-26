/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.agents;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.replication.ReplicationResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteAgentTransportActionTests {

    @Mock
    private Client client;
    SdkClient sdkClient;
    @Mock
    ThreadPool threadPool;
    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private TransportService transportService;

    @Mock
    ClusterService clusterService;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private DeleteAgentTransportAction deleteAgentTransportAction;

    ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        DeleteAgentTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        deleteAgentTransportAction = spy(
            new DeleteAgentTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                mlFeatureEnabledSetting
            )
        );
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

        when(deleteResponse.getId()).thenReturn("AGENT_ID");
        when(deleteResponse.getShardId()).thenReturn(mock(ShardId.class));
        when(deleteResponse.getShardInfo()).thenReturn(mock(ReplicationResponse.ShardInfo.class));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testConstructor() {
        // Verify that the dependencies were correctly injected
        assertEquals(deleteAgentTransportAction.client, client);
        assertEquals(deleteAgentTransportAction.xContentRegistry, xContentRegistry);
    }

    @Test
    public void testDoExecute_Success() throws InterruptedException, IOException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent("AGENT_ID", false, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
        doReturn(true).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals("AGENT_ID", captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    @Test
    public void testDoExecute_Failure() throws IOException, InterruptedException {
        String agentId = "test-non-existed-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new NullPointerException("Failed to delete ML Agent " + agentId));
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete ML Agent " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_HiddenAgentSuperAdmin() throws IOException, InterruptedException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_HiddenAgentDeletionByNonSuperAdmin() throws IOException, InterruptedException {
        String agentId = "hidden-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
        doReturn(false).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, argumentCaptor.getValue().status());
    }

    @Test
    public void testDoExecute_NonHiddenAgentDeletionByNonSuperAdmin() throws IOException, InterruptedException {
        String agentId = "non-hidden-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
        doReturn(false).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        Task task = mock(Task.class);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals("AGENT_ID", captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    @Test
    public void testDoExecute_GetFails() throws InterruptedException {
        String agentId = "test-agent-id";
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);
        Exception expectedException = new RuntimeException("Failed to fetch agent");

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(expectedException);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to fetch agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_DeleteFails() throws IOException, InterruptedException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);
        Exception expectedException = new RuntimeException("Deletion failed");

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onFailure(expectedException);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        // Execute the action
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteAgentTransportAction.doExecute(task, deleteRequest, latchedActionListener);
        latch.await();

        // Verify that actionListener.onFailure() was called with the expected exception
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Deletion failed", argumentCaptor.getValue().getMessage());
    }

    private GetResponse prepareMLAgent(String agentId, boolean isHidden, String tenantId) throws IOException {

        MLAgent mlAgent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)),
            Map.of("test", "test"),
            new MLMemorySpec("test", "123", 0),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            isHidden,
            tenantId
        );

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

}
