/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class RegisterAgentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    SdkClient sdkClient;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private TransportService transportService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLRegisterAgentResponse> actionListener;

    IndexResponse indexResponse;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ThreadPool threadPool;

    private TransportRegisterAgentAction transportRegisterAgentAction;
    private Settings settings;
    private ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        RegisterAgentTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
        transportRegisterAgentAction = new TransportRegisterAgentAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlIndicesHandler,
            clusterService,
            mlFeatureEnabledSetting
        );
        indexResponse = new IndexResponse(new ShardId(ML_AGENT_INDEX, "_na_", 0), "AGENT_ID", 1, 0, 2, true);
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void test_execute_registerAgent_success() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onResponse(indexResponse);
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_execute_registerAgent_AgentIndexNotInitialized() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create ML agent index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_IndexFailure() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("index failure"));
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());

        assertEquals("index failure", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_InitAgentIndexFailure() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("agent index initialization failed"));
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("agent index initialization failed", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_ModelNotHidden() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true); // Simulate successful index initialization
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onResponse(indexResponse);
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        assertNotNull(argumentCaptor.getValue());
    }

    @Test
    public void test_execute_registerAgent_Othertype() throws InterruptedException {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent.builder().name("agent").type(MLAgentType.FLOW.name()).description("description").build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true); // Simulate successful index initialization
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onResponse(indexResponse);
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLRegisterAgentResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportRegisterAgentAction.doExecute(task, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        assertNotNull(argumentCaptor.getValue());
    }

}
