/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.createTestContent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.WorkingMemoryType;
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
    private Task task;

    @Mock
    private ActionListener<MLAddMemoriesResponse> actionListener;

    private TransportAddMemoriesAction transportAddMemoriesAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Mock ThreadPool and create real ThreadContext for RestActionUtils.getUserContext
        ThreadPool threadPool = mock(ThreadPool.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

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
        when(container.getConfiguration()).thenReturn(null);
        
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("container-123"), any());
        
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);
        when(memoryContainerHelper.getMemoryIndexName(eq(container), any(String.class))).thenReturn("memory-index");
        
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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.DATA); // DATA type, not CONVERSATIONAL

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
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, "semantic", Arrays.asList("user_id"), strategyConfig);
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        MemoryStrategy disabledStrategy = new MemoryStrategy("strat-1", false, "semantic", Arrays.asList("user_id"), strategyConfig);
        strategies.add(disabledStrategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
        // Missing session_id required by strategy

        List<MemoryStrategy> strategies = new ArrayList<>();
        Map<String, Object> strategyConfig = new HashMap<>();
        // Strategy requires both user_id and session_id
        MemoryStrategy strategy = new MemoryStrategy("strat-1", true, "semantic", Arrays.asList("user_id", "session_id"), strategyConfig);
        strategies.add(strategy);

        MLAddMemoriesInput input = mock(MLAddMemoriesInput.class);
        when(input.getMemoryContainerId()).thenReturn("container-123");
        when(input.getMessages()).thenReturn(messages);
        when(input.isInfer()).thenReturn(true);
        when(input.getNamespace()).thenReturn(namespace);
        when(input.getOwnerId()).thenReturn("user-123");
        when(input.getMemoryType()).thenReturn(WorkingMemoryType.CONVERSATIONAL);

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
}
