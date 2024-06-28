/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
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
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetAgentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    SdkClient sdkClient;
    @Mock
    ThreadPool threadPool;

    private ClusterSettings clusterSettings;

    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private TransportService transportService;

    @Mock
    ClusterState clusterState;
    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private GetAgentTransportAction getAgentTransportAction;

    ThreadContext threadContext;
    MLAgent mlAgent;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        GetAgentTransportActionTests.class.getName(),
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
        getAgentTransportAction = spy(
            new GetAgentTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                clusterService,
                xContentRegistry,
                mlFeatureEnabledSetting
            )
        );
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testDoExecute_Failure_Get_Agent() throws InterruptedException {
        String agentId = "test-agent-id-no-existed";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new Exception("Failed to get ML agent " + agentId);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(exceptionToThrow);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get ML agent " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_IndexNotFound() throws InterruptedException {
        String agentId = "test-agent-id-IndexNotFound";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new IndexNotFoundException("Failed to get agent index " + agentId);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(exceptionToThrow);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get agent index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_OpenSearchStatus() throws IOException, InterruptedException {
        String agentId = "test-agent-id-OpenSearchStatus";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new OpenSearchStatusException(
            "Failed to find agent with the provided agent id: " + agentId,
            RestStatus.NOT_FOUND
        );

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(exceptionToThrow);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find agent with the provided agent id: " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_RuntimeException() throws InterruptedException {
        String agentId = "test-agent-id-RuntimeException";
        Task task = mock(Task.class);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new RuntimeException("Failed to get ML agent " + agentId));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get ML agent " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTask_NullResponse() throws InterruptedException {
        String agentId = "test-agent-id-NullResponse";
        Task task = mock(Task.class);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(null);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find agent with the provided agent id: " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_Context_Exception() {
        String agentId = "test-agent-id";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);
        Task task = mock(Task.class);
        GetAgentTransportAction getAgentTransportActionNullContext = new GetAgentTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            clusterService,
            xContentRegistry,
            mlFeatureEnabledSetting
        );
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException());

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new RuntimeException());
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
            getAgentTransportActionNullContext.doExecute(task, getRequest, latchedActionListener);
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            assertEquals(e.getClass(), RuntimeException.class);
        }
    }

    @Test
    public void testDoExecute_NoAgentId() throws IOException {
        GetResponse getResponse = prepareMLAgent(null, false, null);
        String agentId = "test-agent-id";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);
        Task task = mock(Task.class);

        GetResponse agentGetResponse = prepareMLAgent(agentId, false, null);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(agentGetResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
            getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            assertEquals(e.getClass(), IllegalArgumentException.class);
        }
    }

    @Test
    public void testDoExecute_Success() throws IOException, InterruptedException {

        String agentId = "test-agent-id";
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId, true, null);
        Task task = mock(Task.class);
        GetResponse agentGetResponse = prepareMLAgent(agentId, false, null);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(agentGetResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, getRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onResponse(any(MLAgentGetResponse.class));

        ArgumentCaptor<MLAgentGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLAgentGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals("test", argumentCaptor.getValue().getMlAgent().getName());
    }

    @Test
    public void testRemoveModelIDIfHiddenAndNotSuperUser() throws IOException, InterruptedException {

        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest request = new MLAgentGetRequest(agentId, true, null);
        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        doReturn(false).when(getAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testNotRemoveModelIDIfHiddenAndSuperUser() throws IOException, InterruptedException {

        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest request = new MLAgentGetRequest(agentId, true, null);
        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        doReturn(true).when(getAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLAgentGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getAgentTransportAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<MLAgentGetResponse> captor = ArgumentCaptor.forClass(MLAgentGetResponse.class);
        verify(actionListener, times(1)).onResponse(captor.capture());
        MLAgentGetResponse mlAgentGetResponse = captor.getValue();
        assertNotNull(mlAgentGetResponse.getMlAgent().getLlm());
    }

    public GetResponse prepareMLAgent(String agentId, boolean isHidden, String tenantId) throws IOException {

        mlAgent = new MLAgent(
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
