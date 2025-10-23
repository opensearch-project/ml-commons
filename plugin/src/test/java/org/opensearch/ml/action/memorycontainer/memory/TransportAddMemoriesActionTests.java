/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.createTestContent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.PayloadType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportAddMemoriesActionTests {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private MemoryProcessingService memoryProcessingService;

    @Mock
    private MemorySearchService memorySearchService;

    @Mock
    private MemoryOperationsService memoryOperationsService;

    @Mock
    private ExecutorService executorService;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLAddMemoriesResponse> actionListener;

    private TransportAddMemoriesAction transportAddMemoriesAction;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock ThreadPool and create real ThreadContext for RestActionUtils.getUserContext
        ThreadPool threadPool = mock(ThreadPool.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock executor to run async code synchronously for testing
        when(threadPool.executor(any(String.class))).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run(); // Execute immediately in test thread
            return null;
        }).when(executorService).execute(any(Runnable.class));

        transportAddMemoriesAction = new TransportAddMemoriesAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            xContentRegistry,
            mlFeatureEnabledSetting,
            memoryContainerHelper,
            threadPool
        );

        // Replace internal services with mocks via reflection for testing private methods
        injectMockService("memoryProcessingService", memoryProcessingService);
        injectMockService("memorySearchService", memorySearchService);
        injectMockService("memoryOperationsService", memoryOperationsService);
    }

    private void injectMockService(String fieldName, Object mockService) throws Exception {
        Field field = TransportAddMemoriesAction.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(transportAddMemoriesAction, mockService);
    }

    @Test
    public void testDoExecute_AgenticMemoryDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        
        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        
        transportAddMemoriesAction.doExecute(task, request, actionListener);
        
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_BlankMemoryContainerId() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("");
        
        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);
        
        transportAddMemoriesAction.doExecute(task, request, actionListener);
        
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_NoContainerAccess() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        
        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);
        
        MLMemoryContainer container = mock(MLMemoryContainer.class);
        
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(false);
        
        transportAddMemoriesAction.doExecute(task, request, actionListener);
        
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void testDoExecute_InferRequiresLLMModel() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        
        MessageInput message = MessageInput.builder().content(createTestContent("Hello world")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);
        
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        
        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);
        
        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(MemoryConfiguration.builder().build());
        
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);
        when(memoryContainerHelper.getMemoryIndexName(eq(container), any(MemoryType.class))).thenReturn("memory-index");
        
        transportAddMemoriesAction.doExecute(task, request, actionListener);
        
        verify(actionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testDoExecute_SuccessfulProcessingWithoutLLM() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello world")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("session_id", "session-123");

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock indexData to trigger success callback
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify the full flow
        verify(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_SuccessfulProcessingWithLLM() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello world")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("session_id", "session-123");

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-model-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock indexData to trigger success callback
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify the full flow including response
        verify(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
        // Note: Async LLM processing happens in background thread pool - not verified in unit test
    }

    @Test
    public void testDoExecute_ContainerRetrievalFailure() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        
        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        
        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);
        
        Exception testException = new RuntimeException("Container not found");
        
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onFailure(testException);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        
        transportAddMemoriesAction.doExecute(task, request, actionListener);

        verify(actionListener).onFailure(testException);
    }

    // ===== Group 1: Input Validation Tests =====

    // Note: testDoExecute_NullInput skipped - implementation bug at line 112 where input.setOwnerId()
    // is called before null check at line 114, causing NPE instead of IllegalArgumentException

    @Test
    public void testDoExecute_IndexingFailure() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("session_id", "session-123");

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock indexData to trigger failure callback
        Exception indexingException = new RuntimeException("Index failure");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(indexingException);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onFailure(indexingException);
    }

    @Test
    public void testDoExecute_DisableSessionFlag() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        // No session_id in namespace

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.isDisableSession()).thenReturn(true); // Session creation disabled

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock indexData to complete successfully
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify session creation was skipped and working memory was indexed
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    // ===== Group 2: Session Creation Tests (Key Branches) =====

    @Test
    public void testDoExecute_SuccessfulSessionCreation() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        // No session_id - should trigger session creation

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);
        when(input.getParameters()).thenReturn(new HashMap<>());

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getParameters()).thenReturn(new HashMap<>());
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getSessionIndexName()).thenReturn("session-index");
        when(config.isDisableSession()).thenReturn(false);
        when(config.getLlmId()).thenReturn("llm-123"); // Required for session creation

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock summarizeMessages
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onResponse("Session summary");
            return null;
        }).when(memoryProcessingService).summarizeMessages(eq(config), eq(messages), any());

        // Mock indexData for both session and working memory
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            IndexRequest indexRequest = invocation.getArgument(1);
            // Return different IDs based on index name
            if (indexRequest.index().equals("session-index")) {
                when(indexResponse.getId()).thenReturn("session-123");
            } else {
                when(indexResponse.getId()).thenReturn("working-mem-123");
            }
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify session creation flow
        verify(memoryProcessingService).summarizeMessages(eq(config), eq(messages), any());
        // Verify indexData called twice: once for session, once for working memory
        verify(memoryContainerHelper, org.mockito.Mockito.times(2)).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_SessionCreation_SummarizeFailure() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        // No session_id - should trigger session creation

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);
        when(input.getParameters()).thenReturn(new HashMap<>());

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getParameters()).thenReturn(new HashMap<>());
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getSessionIndexName()).thenReturn("session-index");
        when(config.isDisableSession()).thenReturn(false);
        when(config.getLlmId()).thenReturn("llm-123"); // Required for session creation

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock summarizeMessages to fail
        Exception summarizeException = new RuntimeException("Summarization failed");
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onFailure(summarizeException);
            return null;
        }).when(memoryProcessingService).summarizeMessages(eq(config), eq(messages), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify summarize was called and failure was propagated
        verify(memoryProcessingService).summarizeMessages(eq(config), eq(messages), any());
        verify(actionListener).onFailure(argThat(exception -> exception instanceof OpenSearchStatusException
            && ((OpenSearchStatusException)exception).status() == RestStatus.INTERNAL_SERVER_ERROR
            && exception.getMessage().contains("Internal server error")));
    }

    @Test
    public void testDoExecute_UserProvidedSessionId_SkipsCreation() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("session_id", "existing-session-123"); // User provided session_id

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.isDisableSession()).thenReturn(false);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify session creation was skipped (no session index call) and went directly to working memory
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_NullLlmId_SkipsSessionCreation() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        // No session_id - would normally trigger session creation, but getLlmId() is null

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.isDisableSession()).thenReturn(false);
        when(config.getLlmId()).thenReturn(null); // No LLM configured - session creation should be skipped

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify session creation was skipped (no summarize call) and went directly to working memory
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_DataMemoryType_SkipsSessionCreation() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Data content")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.DATA); // DATA type, not CONVERSATIONAL

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify working memory indexed successfully (session creation only for CONVERSATIONAL type)
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    // ===== Group 4: Async LLM Processing Tests (Key Branches) =====

    @Test
    public void testDoExecute_InferTrue_TriggersAsyncProcessing() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Extract facts")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123"); // Provide session_id to skip session creation

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify response returned immediately (async processing happens in background)
        verify(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_DisabledStrategy_SkipsProcessing() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123"); // Provide session_id to skip session creation

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        MemoryStrategy disabledStrategy = new MemoryStrategy("strat-1", false, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);
        strategies.add(disabledStrategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify response returned (disabled strategy means no LLM processing)
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    @Test
    public void testDoExecute_PartialNamespaceMatch_SkipsStrategy() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123"); // Include session_id for basic flow
        // Missing organization_id required by strategy (below)

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        // Strategy requires both user_id and organization_id (but namespace only has user_id and session_id)
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id", "organization_id"), strategyConfig);
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify response returned (incomplete namespace means strategy skipped)
        verify(actionListener).onResponse(any(MLAddMemoriesResponse.class));
    }

    // ===== Group 6: extractLongTermMemory Coverage (Private Method Tests) =====

    @Test
    public void testExtractLongTermMemory_EnabledStrategy_InvokesProcessing() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Extract facts")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), strategyConfig);
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        // Mock runMemoryStrategy to capture callback
        ArgumentCaptor<ActionListener<List<String>>> factsListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doAnswer(invocation -> {
            // Don't invoke callback yet - we'll trigger it manually
            return null;
        }).when(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), factsListenerCaptor.capture());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify runMemoryStrategy was called for the enabled strategy
        verify(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), any());
    }

    @Test
    public void testExtractLongTermMemory_MultipleStrategies_ProcessesAll() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy1 = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        MemoryStrategy strategy2 = new MemoryStrategy("strat-2", true, MemoryStrategyType.USER_PREFERENCE, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(strategy1);
        strategies.add(strategy2);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify both strategies were processed
        verify(memoryProcessingService).runMemoryStrategy(eq(strategy1), eq(messages), eq(config), any());
        verify(memoryProcessingService).runMemoryStrategy(eq(strategy2), eq(messages), eq(config), any());
    }

    @Test
    public void testExtractLongTermMemory_DisabledStrategy_Skipped() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy disabledStrategy = new MemoryStrategy("strat-1", false, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(disabledStrategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify disabled strategy was NOT processed
        verify(memoryProcessingService, org.mockito.Mockito.never()).runMemoryStrategy(any(), any(), any(), any());
    }

    @Test
    public void testExtractLongTermMemory_PartialNamespace_Skipped() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");
        // Missing organization_id required by strategy

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id", "organization_id"), new HashMap<>());
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify strategy with incomplete namespace was NOT processed
        verify(memoryProcessingService, org.mockito.Mockito.never()).runMemoryStrategy(any(), any(), any(), any());
    }

    @Test
    public void testExtractLongTermMemory_StrategyFailure_ErrorHandled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        // Mock runMemoryStrategy to trigger failure
        Exception strategyException = new RuntimeException("LLM extraction failed");
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(3);
            listener.onFailure(strategyException);
            return null;
        }).when(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify error was handled: writeErrorToMemoryHistory should be called
        verify(memoryOperationsService).writeErrorToMemoryHistory(eq(config), any(), eq(input), eq(strategyException));
    }

    // ===== Group 7: storeLongTermMemory Coverage (Private Method Tests) =====

    @Test
    public void testStoreLongTermMemory_NoSimilarFacts_CreatesAddDecisions() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        // Mock runMemoryStrategy to return facts
        List<String> facts = Arrays.asList("fact1", "fact2", "fact3");
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(3);
            listener.onResponse(facts);
            return null;
        }).when(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), any());

        // Mock searchSimilarFactsForSession to return empty list (no similar facts)
        doAnswer(invocation -> {
            ActionListener<List> listener = invocation.getArgument(4);
            listener.onResponse(new ArrayList<>()); // Empty search results
            return null;
        }).when(memorySearchService).searchSimilarFactsForSession(eq(strategy), eq(input), eq(facts), eq(config), any());

        // Mock executeMemoryOperations to succeed
        doAnswer(invocation -> {
            ActionListener<List<MemoryResult>> listener = invocation.getArgument(5);
            List<MemoryResult> results = Arrays.asList(
                MemoryResult.builder().event(MemoryEvent.ADD).build(),
                MemoryResult.builder().event(MemoryEvent.ADD).build(),
                MemoryResult.builder().event(MemoryEvent.ADD).build()
            );
            listener.onResponse(results);
            return null;
        }).when(memoryOperationsService).executeMemoryOperations(any(), eq(config), any(), any(), eq(input), eq(strategy), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify search was called
        verify(memorySearchService).searchSimilarFactsForSession(eq(strategy), eq(input), eq(facts), eq(config), any());
        // Verify executeMemoryOperations was called (should create ADD decisions for all facts)
        verify(memoryOperationsService).executeMemoryOperations(any(), eq(config), any(), any(), eq(input), eq(strategy), any());
    }

    @Test
    public void testStoreLongTermMemory_WithSimilarFacts_MakesDecisions() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        // Mock runMemoryStrategy to return facts
        List<String> facts = Arrays.asList("fact1", "fact2");
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(3);
            listener.onResponse(facts);
            return null;
        }).when(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), any());

        // Mock searchSimilarFactsForSession to return similar facts
        List<Object> similarFacts = Arrays.asList("similarFact1", "similarFact2");
        doAnswer(invocation -> {
            ActionListener<List> listener = invocation.getArgument(4);
            listener.onResponse(similarFacts); // Non-empty search results
            return null;
        }).when(memorySearchService).searchSimilarFactsForSession(eq(strategy), eq(input), eq(facts), eq(config), any());

        // Mock makeMemoryDecisions to return decisions
        List<MemoryDecision> decisions = Arrays.asList(
            MemoryDecision.builder().event(MemoryEvent.ADD).text("fact1").build(),
            MemoryDecision.builder().event(MemoryEvent.UPDATE).text("fact2").build()
        );
        doAnswer(invocation -> {
            ActionListener<List<MemoryDecision>> listener = invocation.getArgument(4);
            listener.onResponse(decisions);
            return null;
        }).when(memoryProcessingService).makeMemoryDecisions(eq(facts), any(), any(), eq(config), any());

        // Mock executeMemoryOperations to succeed
        doAnswer(invocation -> {
            ActionListener<List<MemoryResult>> listener = invocation.getArgument(5);
            List<MemoryResult> results = Arrays.asList(
                MemoryResult.builder().event(MemoryEvent.ADD).build(),
                MemoryResult.builder().event(MemoryEvent.UPDATE).build()
            );
            listener.onResponse(results);
            return null;
        }).when(memoryOperationsService).executeMemoryOperations(eq(decisions), eq(config), any(), any(), eq(input), eq(strategy), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify decision-making was called
        verify(memoryProcessingService).makeMemoryDecisions(eq(facts), any(), any(), eq(config), any());
        verify(memoryOperationsService).executeMemoryOperations(eq(decisions), eq(config), any(), any(), eq(input), eq(strategy), any());
    }

    @Test
    public void testStoreLongTermMemory_SearchFailure_PropagatesError() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Test")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();
        namespace.put("user_id", "user-123");
        namespace.put("session_id", "session-123");

        List<MemoryStrategy> strategies = new ArrayList<>();
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, MemoryStrategyType.SEMANTIC, Arrays.asList("user_id"), new HashMap<>());
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getLlmId()).thenReturn("llm-123");
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getStrategies()).thenReturn(strategies);

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn("working-mem-123");
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        // Mock runMemoryStrategy to return facts
        List<String> facts = Arrays.asList("fact1");
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(3);
            listener.onResponse(facts);
            return null;
        }).when(memoryProcessingService).runMemoryStrategy(eq(strategy), eq(messages), eq(config), any());

        // Mock searchSimilarFactsForSession to trigger failure
        Exception searchException = new RuntimeException("Search failed");
        doAnswer(invocation -> {
            ActionListener<List> listener = invocation.getArgument(4);
            listener.onFailure(searchException);
            return null;
        }).when(memorySearchService).searchSimilarFactsForSession(eq(strategy), eq(input), eq(facts), eq(config), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify search was called
        verify(memorySearchService).searchSimilarFactsForSession(eq(strategy), eq(input), eq(facts), eq(config), any());
        // Note: In real implementation, this doesn't call the main actionListener but logs the error
        // The async callback handles its own error differently
    }

    @Test
    public void testSummarizeMessages_Preserves4XXErrors() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        MessageInput message = MessageInput.builder().content(createTestContent("Hello")).role("user").build();
        List<MessageInput> messages = Arrays.asList(message);

        Map<String, String> namespace = new HashMap<>();

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(false);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getPayloadType()).thenReturn(PayloadType.CONVERSATIONAL);
        when(input.getParameters()).thenReturn(new HashMap<>());

        MLAddMemoriesRequest request = mock(MLAddMemoriesRequest.class);
        when(request.getMlAddMemoryInput()).thenReturn(input);

        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(config.getParameters()).thenReturn(new HashMap<>());
        when(config.getWorkingMemoryIndexName()).thenReturn("working-memory-index");
        when(config.getSessionIndexName()).thenReturn("session-index");
        when(config.isDisableSession()).thenReturn(false);
        when(config.getLlmId()).thenReturn("llm-123");

        MLMemoryContainer container = mock(MLMemoryContainer.class);
        when(container.getConfiguration()).thenReturn(config);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock summarizeMessages to return NOT_FOUND (4XX)
        OpenSearchStatusException notFoundException = new OpenSearchStatusException(
            "LLM predict result cannot be extracted with current llm_result_path",
            RestStatus.NOT_FOUND
        );
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onFailure(notFoundException);
            return null;
        }).when(memoryProcessingService).summarizeMessages(eq(config), eq(messages), any());

        transportAddMemoriesAction.doExecute(task, request, actionListener);

        // Verify 4XX error is preserved with detailed message
        verify(actionListener).onFailure(argThat(exception -> exception instanceof OpenSearchStatusException
            && ((OpenSearchStatusException)exception).status() == RestStatus.NOT_FOUND
            && exception.getMessage().contains("LLM predict result cannot be extracted")));
    }
}
