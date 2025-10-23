/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportUpdateMemoryContainerActionTests extends OpenSearchTestCase {

    private TransportUpdateMemoryContainerAction action;

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Task task;

    @Mock
    private ThreadPool threadPool;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Setup thread context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext())
            .thenReturn(new org.opensearch.common.util.concurrent.ThreadContext(org.opensearch.common.settings.Settings.builder().build()));

        action = new TransportUpdateMemoryContainerAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            xContentRegistry,
            connectorAccessControlHelper,
            mlFeatureEnabledSetting,
            mlModelManager,
            memoryContainerHelper,
            mlIndicesHandler
        );
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testDoExecuteWhenFeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("updated-name").build();
        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId("container-id")
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    public void testDoExecuteSuccess() {
        String containerId = "test-container-id";
        String newName = "updated-name";

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name(newName).description("new desc").build();
        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("old-name")
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock access control
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock update response
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testDoExecuteWhenGetContainerFails() {
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("updated-name").build();
        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId("container-id")
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval failure
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onFailure(new RuntimeException("Container not found"));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    public void testDoExecuteWhenAccessDenied() {
        String containerId = "test-container-id";

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("updated-name").build();
        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("old-name")
            .owner(
                new User(
                    "other-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock access control denial
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(false);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    public void testDoExecuteWhenUpdateFails() {
        String containerId = "test-container-id";

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("updated-name").build();
        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("old-name")
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock access control
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock update failure
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onFailure(new RuntimeException("Update failed"));
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Update failed", exception.getMessage());
    }

    public void testUpdateStrategies_UpdateExistingStrategy() {
        String containerId = "test-container-id";

        // Create existing strategies
        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        // Create configuration with existing strategies
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(existingStrategies)
            .build();

        // Create update strategy (disable existing)
        MemoryStrategy updateStrategy = MemoryStrategy.builder().id("semantic_123").enabled(false).build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(updateStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container with configuration
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock successful update
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateStrategies_AddNewStrategy() {
        String containerId = "test-container-id";

        // Create existing strategies
        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(existingStrategies)
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.USER_PREFERENCE)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(newStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateStrategies_InvalidStrategyId() {
        String containerId = "test-container-id";

        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(existingStrategies)
            .build();

        // Try to update non-existent strategy
        MemoryStrategy updateStrategy = MemoryStrategy.builder().id("nonexistent_999").enabled(false).build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(updateStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    public void testUpdateStrategies_CannotChangeType() {
        String containerId = "test-container-id";

        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(existingStrategies)
            .build();

        // Try to change strategy type
        MemoryStrategy updateStrategy = MemoryStrategy
            .builder()
            .id("semantic_123")
            .type(MemoryStrategyType.USER_PREFERENCE)  // Try to change type
            .enabled(false)
            .build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(updateStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    public void testUpdateStrategies_PartialFieldUpdate() {
        String containerId = "test-container-id";

        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-model-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(existingStrategies)
            .build();

        // Update only namespace, not enabled
        MemoryStrategy updateStrategy = MemoryStrategy
            .builder()
            .id("semantic_123")
            .namespace(Arrays.asList("user_id", "session_id"))
            .build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(updateStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateLlmId_Success() {
        String containerId = "test-container-id";
        String newLlmId = "new-llm-model-789";

        // Create existing configuration
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("old-llm-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-123")
            .dimension(768)
            .strategies(new ArrayList<>())
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .configuration(MemoryConfiguration.builder().llmId(newLlmId).build())
            .build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container with configuration
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock successful update
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateLlmId_WithStrategies() {
        String containerId = "test-container-id";
        String newLlmId = "new-llm-combined";

        // Create existing strategies
        List<MemoryStrategy> existingStrategies = new ArrayList<>();
        existingStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("old-llm-456")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-456")
            .dimension(768)
            .strategies(existingStrategies)
            .build();

        // Update both llmId and strategy
        MemoryStrategy updateStrategy = MemoryStrategy.builder().id("semantic_123").enabled(false).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .configuration(MemoryConfiguration.builder().llmId(newLlmId).strategies(Arrays.asList(updateStrategy)).build())
            .build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateLlmId_WithOtherFields() {
        String containerId = "test-container-id";
        String newLlmId = "new-llm-with-name";
        String newName = "updated-name";
        String newDescription = "updated-description";

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("old-llm-999")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-999")
            .dimension(768)
            .strategies(new ArrayList<>())
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name(newName)
            .description(newDescription)
            .configuration(MemoryConfiguration.builder().llmId(newLlmId).build())
            .build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("old-name")
            .configuration(config)
            .owner(
                new User(
                    "test-user",
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap()
                )
            )
            .build();

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateContainer_AddStrategyWithoutModels_ShouldFail() {
        // Test adding strategy to container without llm/embedding fails
        String containerId = "test-container-id";

        // Container with no strategies, no models
        MemoryConfiguration currentConfig = MemoryConfiguration.builder().indexPrefix("test").build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Try to add strategy without providing models
        MemoryStrategy newStrategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(newStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    public void testUpdateContainer_AddStrategyWithOnlyLlm_ShouldFail() {
        // Test adding strategy when container has LLM but no embedding fails
        String containerId = "test-container-id";

        // Container with LLM but no embedding
        MemoryConfiguration currentConfig = MemoryConfiguration.builder().indexPrefix("test").llmId("llm-123").build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Try to add strategy
        MemoryStrategy newStrategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build();
        MemoryConfiguration updateConfig = MemoryConfiguration.builder().strategies(Arrays.asList(newStrategy)).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    public void testUpdateContainer_ChangeEmbeddingModel_ShouldFail() {
        // Test that changing an existing embedding model fails when container has strategies
        String containerId = "test-container-id";

        // Container with strategies and existing embedding
        List<MemoryStrategy> strategies = Arrays
            .asList(MemoryStrategy.builder().id("strat-1").type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build());

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("old-embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(strategies)
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Try to change embedding model
        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .embeddingModelId("new-embedding-456")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    public void testUpdateContainer_SameEmbeddingValues_ShouldSucceed() {
        // Test that updating with same embedding values is allowed (idempotent) even when strategies exist
        String containerId = "test-container-id";

        // Container with strategies and existing embedding
        List<MemoryStrategy> strategies = Arrays
            .asList(MemoryStrategy.builder().id("strat-1").type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build());

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(strategies)
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Update with same embedding values
        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .embeddingModelId("embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateContainer_NoStrategies_AddEmbedding_ShouldSucceed() {
        // Test that adding embedding config to a container with no strategies succeeds
        String containerId = "test-container-id";

        // Container with NO strategies and no embedding
        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .strategies(new ArrayList<>()) // Empty strategies = no long-term index
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Add embedding config
        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .embeddingModelId("embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        // Should succeed - no strategies means no long-term index, so embedding can be added
        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateContainer_NoStrategies_ChangeEmbedding_ShouldSucceed() {
        // Test that changing embedding config on a container with no strategies succeeds
        String containerId = "test-container-id";

        // Container with NO strategies but has existing embedding
        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .embeddingModelId("old-embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(new ArrayList<>()) // Empty strategies = no long-term index
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Change embedding config to different values
        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .embeddingModelId("new-embedding-456")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        // Should succeed - no strategies means no long-term index, so embedding can be changed
        verify(listener).onResponse(updateResponse);
    }

    public void testUpdateContainer_TransitionToStrategies_SharedIndexExists_Success() {
        // Test transition with shared index that exists and is compatible - covers createHistoryIndexOnly
        String containerId = "test-container-id";
        String llmId = "llm-model-100";
        String embeddingModelId = "embedding-model-200";

        // Container with NO strategies initially
        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("shared-prefix")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Update that ADDS strategies (triggers transition)
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock LLM model validation
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            org.opensearch.ml.common.MLModel llmModel = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(llmId)
                .algorithm(FunctionName.REMOTE)
                .name("test-llm")
                .build();
            modelListener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq(llmId), any());

        // Mock embedding model validation
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            org.opensearch.ml.common.MLModel embeddingModel = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(embeddingModelId)
                .algorithm(FunctionName.TEXT_EMBEDDING)
                .name("test-embedding")
                .build();
            modelListener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq(embeddingModelId), any());

        // Mock admin clients
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        // Mock getMappings - index exists with compatible configuration
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            org.opensearch.cluster.metadata.MappingMetadata mappingMetadata = mock(org.opensearch.cluster.metadata.MappingMetadata.class);
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> memoryEmbeddingField = new HashMap<>();
            memoryEmbeddingField.put("type", "knn_vector");
            memoryEmbeddingField.put("dimension", 384);
            properties.put("memory_embedding", memoryEmbeddingField);
            when(mappingMetadata.getSourceAsMap()).thenReturn(Map.of("properties", properties));

            org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse mappingResponse = mock(
                org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse.class
            );
            when(mappingResponse.getMappings()).thenReturn(Map.of(".plugins-ml-am-shared-prefix-memory-long-term", mappingMetadata));
            mappingListener.onResponse(mappingResponse);
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        // Mock getPipeline - create REAL PipelineConfiguration instead of mocking (it's final)
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);

            // Create real PipelineConfiguration with correct JSON
            String pipelineJson = "{\"processors\":[{\"text_embedding\":{\"model_id\":\"" + embeddingModelId + "\",\"field_map\":{}}}]}";
            org.opensearch.ingest.PipelineConfiguration realPipelineConfig = new org.opensearch.ingest.PipelineConfiguration(
                ".plugins-ml-am-shared-prefix-memory-long-term-embedding",
                new org.opensearch.core.common.bytes.BytesArray(pipelineJson),
                org.opensearch.common.xcontent.XContentType.JSON
            );

            org.opensearch.action.ingest.GetPipelineResponse pipelineResponse = mock(
                org.opensearch.action.ingest.GetPipelineResponse.class
            );
            when(pipelineResponse.pipelines()).thenReturn(Arrays.asList(realPipelineConfig));
            pipelineListener.onResponse(pipelineResponse);
            return null;
        }).when(clusterAdmin).getPipeline(any(), any());

        // Mock history index creation - SUCCESS
        doAnswer(invocation -> {
            ActionListener<Boolean> historyListener = invocation.getArgument(2);
            historyListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryHistoryIndex(any(), any(), any());

        // Mock final update
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        // Verify success - createHistoryIndexOnly executed successfully
        verify(listener).onResponse(updateResponse);
        verify(mlIndicesHandler).createLongTermMemoryHistoryIndex(any(), any(), any());
        verify(mlIndicesHandler, never()).createLongTermMemoryIndex(any(), any(), any(), any());
    }

    public void testUpdateContainer_TransitionToStrategies_NewIndices_Success() {
        // Test transition with new indices creation - covers createLongTermAndHistoryIndices success callbacks
        String containerId = "test-container-id";
        String llmId = "llm-model-300";
        String embeddingModelId = "embedding-model-400";

        // Container with NO strategies initially
        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("new-index")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(512)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Update that ADDS strategies
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(512)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock model validations
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            String modelId = invocation.getArgument(0);
            org.opensearch.ml.common.MLModel model = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(modelId)
                .algorithm(modelId.equals(llmId) ? FunctionName.REMOTE : FunctionName.TEXT_EMBEDDING)
                .name("test-model")
                .build();
            modelListener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(any(String.class), any());

        // Mock admin clients
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        // Mock getMappings - index does NOT exist
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            mappingListener.onFailure(new org.opensearch.index.IndexNotFoundException(".plugins-ml-am-new-index-memory-long-term"));
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        // Mock getPipeline - pipeline doesn't exist yet (for createLongTermMemoryIngestPipeline)
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse pipelineResponse = mock(
                org.opensearch.action.ingest.GetPipelineResponse.class
            );
            when(pipelineResponse.pipelines()).thenReturn(Collections.emptyList());
            pipelineListener.onResponse(pipelineResponse);
            return null;
        }).when(clusterAdmin).getPipeline(any(org.opensearch.action.ingest.GetPipelineRequest.class), any());

        // Mock putPipeline - SUCCESS (create real AcknowledgedResponse)
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.support.clustermanager.AcknowledgedResponse> putListener = invocation.getArgument(1);
            org.opensearch.action.support.clustermanager.AcknowledgedResponse response =
                new org.opensearch.action.support.clustermanager.AcknowledgedResponse(true);
            putListener.onResponse(response);
            return null;
        }).when(clusterAdmin).putPipeline(any(org.opensearch.action.ingest.PutPipelineRequest.class), any());

        // Mock long-term index creation - SUCCESS
        doAnswer(invocation -> {
            ActionListener<Boolean> longTermListener = invocation.getArgument(3);
            longTermListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());

        // Mock history index creation - SUCCESS
        doAnswer(invocation -> {
            ActionListener<Boolean> historyListener = invocation.getArgument(2);
            historyListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryHistoryIndex(any(), any(), any());

        // Mock final update - SUCCESS
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        // Verify success - createLongTermAndHistoryIndices executed successfully with all callbacks
        verify(listener).onResponse(updateResponse);
        verify(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());
        verify(mlIndicesHandler).createLongTermMemoryHistoryIndex(any(), any(), any());
    }

    public void testUpdateContainer_TransitionToStrategies_NewIndices_HistoryDisabled_Success() {
        // Test transition with new indices but history disabled - covers disableHistory branch in createLongTermAndHistoryIndices
        String containerId = "test-container-id";
        String llmId = "llm-model-500";
        String embeddingModelId = "embedding-model-600";

        // Container with NO strategies and history DISABLED
        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("no-hist")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(256)
            .disableHistory(true)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        // Update that ADDS strategies (history still disabled)
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("app_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(256)
            .disableHistory(true)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock model validations
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            String modelId = invocation.getArgument(0);
            org.opensearch.ml.common.MLModel model = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(modelId)
                .algorithm(modelId.equals(llmId) ? FunctionName.REMOTE : FunctionName.TEXT_EMBEDDING)
                .name("test-model")
                .build();
            modelListener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(any(String.class), any());

        // Mock admin clients
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        // Mock getMappings - index does NOT exist
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            mappingListener.onFailure(new org.opensearch.index.IndexNotFoundException(".plugins-ml-am-no-hist-memory-long-term"));
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        // Mock getPipeline - pipeline doesn't exist yet
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse pipelineResponse = mock(
                org.opensearch.action.ingest.GetPipelineResponse.class
            );
            when(pipelineResponse.pipelines()).thenReturn(Collections.emptyList());
            pipelineListener.onResponse(pipelineResponse);
            return null;
        }).when(clusterAdmin).getPipeline(any(org.opensearch.action.ingest.GetPipelineRequest.class), any());

        // Mock putPipeline - SUCCESS (create real AcknowledgedResponse)
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.support.clustermanager.AcknowledgedResponse> putListener = invocation.getArgument(1);
            org.opensearch.action.support.clustermanager.AcknowledgedResponse response =
                new org.opensearch.action.support.clustermanager.AcknowledgedResponse(true);
            putListener.onResponse(response);
            return null;
        }).when(clusterAdmin).putPipeline(any(org.opensearch.action.ingest.PutPipelineRequest.class), any());

        // Mock long-term index creation - SUCCESS
        doAnswer(invocation -> {
            ActionListener<Boolean> longTermListener = invocation.getArgument(3);
            longTermListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());

        // Mock final update - SUCCESS
        UpdateResponse updateResponse = new UpdateResponse(
            new ShardId(new Index("test", "uuid"), 0),
            containerId,
            1L,
            1L,
            1L,
            org.opensearch.action.DocWriteResponse.Result.UPDATED
        );
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        action.doExecute(task, request, listener);

        // Verify success - history index NOT created because disabled
        verify(listener).onResponse(updateResponse);
        verify(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());
        verify(mlIndicesHandler, never()).createLongTermMemoryHistoryIndex(any(), any(), any());
    }

    public void testUpdateContainer_TransitionToStrategies_LlmValidationFails() {
        // Test LLM validation failure during transition
        String containerId = "test-container-id";
        String llmId = "invalid-llm";

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId(llmId)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId("embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock LLM validation failure
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            modelListener.onFailure(new RuntimeException("LLM model not found"));
            return null;
        }).when(mlModelManager).getModel(eq(llmId), any());

        action.doExecute(task, request, listener);

        // Verify failure - LLM validation should fail
        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    public void testUpdateContainer_TransitionToStrategies_EmbeddingValidationFails() {
        // Test embedding validation failure during transition
        String containerId = "test-container-id";
        String llmId = "llm-123";
        String embeddingModelId = "invalid-embedding";

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock LLM validation success
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            org.opensearch.ml.common.MLModel llmModel = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(llmId)
                .algorithm(FunctionName.REMOTE)
                .name("test-llm")
                .build();
            modelListener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq(llmId), any());

        // Mock embedding validation failure
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            modelListener.onFailure(new RuntimeException("Embedding model not found"));
            return null;
        }).when(mlModelManager).getModel(eq(embeddingModelId), any());

        action.doExecute(task, request, listener);

        // Verify failure - embedding validation should fail
        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    public void testUpdateContainer_TransitionToStrategies_SharedIndexIncompatible() {
        // Test shared index compatibility failure
        String containerId = "test-container-id";
        String llmId = "llm-999";
        String embeddingModelId = "embedding-999";

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("incompatible")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock model validations success
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            String modelId = invocation.getArgument(0);
            org.opensearch.ml.common.MLModel model = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(modelId)
                .algorithm(modelId.equals(llmId) ? FunctionName.REMOTE : FunctionName.TEXT_EMBEDDING)
                .name("test-model")
                .build();
            modelListener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(any(String.class), any());

        // Mock admin clients
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        // Mock getMappings - index exists with incompatible dimension
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            org.opensearch.cluster.metadata.MappingMetadata metadata = mock(org.opensearch.cluster.metadata.MappingMetadata.class);
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> embeddingField = new HashMap<>();
            embeddingField.put("type", "knn_vector");
            embeddingField.put("dimension", 768); // Different dimension!
            properties.put("memory_embedding", embeddingField);
            when(metadata.getSourceAsMap()).thenReturn(Map.of("properties", properties));

            org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse response = mock(
                org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse.class
            );
            when(response.getMappings()).thenReturn(Map.of(".plugins-ml-am-incompatible-memory-long-term", metadata));
            mappingListener.onResponse(response);
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        // Mock getPipeline - different model ID
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);
            org.opensearch.ingest.PipelineConfiguration pipelineConfig = mock(org.opensearch.ingest.PipelineConfiguration.class);
            Map<String, Object> pipelineSource = Map
                .of("processors", Arrays.asList(Map.of("text_embedding", Map.of("model_id", "different-model-id", "field_map", Map.of()))));
            when(pipelineConfig.getConfigAsMap()).thenReturn(pipelineSource);

            org.opensearch.action.ingest.GetPipelineResponse response = mock(org.opensearch.action.ingest.GetPipelineResponse.class);
            when(response.pipelines()).thenReturn(Arrays.asList(pipelineConfig));
            pipelineListener.onResponse(response);
            return null;
        }).when(clusterAdmin).getPipeline(any(), any());

        action.doExecute(task, request, listener);

        // Verify failure due to incompatible configuration
        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    public void testUpdateContainer_TransitionToStrategies_HistoryIndexCreationFails() {
        // Test history index creation failure
        String containerId = "test-container-id";
        String llmId = "llm-555";
        String embeddingModelId = "embedding-555";

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("history-fail")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(256)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(256)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock model validations success
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            String modelId = invocation.getArgument(0);
            org.opensearch.ml.common.MLModel model = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(modelId)
                .algorithm(modelId.equals(llmId) ? FunctionName.REMOTE : FunctionName.TEXT_EMBEDDING)
                .name("test-model")
                .build();
            modelListener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(any(String.class), any());

        // Mock admin clients and shared index validation - index doesn't exist
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            mappingListener.onFailure(new org.opensearch.index.IndexNotFoundException(".plugins-ml-am-history-fail-memory-long-term"));
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse response = mock(org.opensearch.action.ingest.GetPipelineResponse.class);
            when(response.pipelines()).thenReturn(Collections.emptyList());
            pipelineListener.onResponse(response);
            return null;
        }).when(clusterAdmin).getPipeline(any(org.opensearch.action.ingest.GetPipelineRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.support.clustermanager.AcknowledgedResponse> putListener = invocation.getArgument(1);
            org.opensearch.action.support.clustermanager.AcknowledgedResponse response = mock(
                org.opensearch.action.support.clustermanager.AcknowledgedResponse.class
            );
            when(response.isAcknowledged()).thenReturn(true);
            putListener.onResponse(response);
            return null;
        }).when(clusterAdmin).putPipeline(any(org.opensearch.action.ingest.PutPipelineRequest.class), any());

        // Mock long-term index creation success
        doAnswer(invocation -> {
            ActionListener<Boolean> longTermListener = invocation.getArgument(3);
            longTermListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());

        // Mock history index creation FAILURE
        doAnswer(invocation -> {
            ActionListener<Boolean> historyListener = invocation.getArgument(2);
            historyListener.onFailure(new RuntimeException("Failed to create history index"));
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryHistoryIndex(any(), any(), any());

        action.doExecute(task, request, listener);

        // Verify failure - history index creation should fail
        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    public void testUpdateContainer_TransitionToStrategies_LongTermIndexCreationFails() {
        // Test long-term index creation failure
        String containerId = "test-container-id";
        String llmId = "llm-777";
        String embeddingModelId = "embedding-777";

        MemoryConfiguration currentConfig = MemoryConfiguration
            .builder()
            .indexPrefix("longterm-fail")
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(128)
            .strategies(new ArrayList<>())
            .build();

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(currentConfig)
            .owner(new User("test-user", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()))
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryConfiguration updateConfig = MemoryConfiguration
            .builder()
            .llmId(llmId)
            .embeddingModelId(embeddingModelId)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(128)
            .strategies(Arrays.asList(newStrategy))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().configuration(updateConfig).build();

        MLUpdateMemoryContainerRequest request = MLUpdateMemoryContainerRequest
            .builder()
            .memoryContainerId(containerId)
            .mlUpdateMemoryContainerInput(input)
            .build();

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(container))).thenReturn(true);

        // Mock model validations success
        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.MLModel> modelListener = invocation.getArgument(1);
            String modelId = invocation.getArgument(0);
            org.opensearch.ml.common.MLModel model = org.opensearch.ml.common.MLModel
                .builder()
                .modelId(modelId)
                .algorithm(modelId.equals(llmId) ? FunctionName.REMOTE : FunctionName.TEXT_EMBEDDING)
                .name("test-model")
                .build();
            modelListener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(any(String.class), any());

        // Mock admin clients
        org.opensearch.transport.client.IndicesAdminClient indicesAdmin = mock(org.opensearch.transport.client.IndicesAdminClient.class);
        org.opensearch.transport.client.ClusterAdminClient clusterAdmin = mock(org.opensearch.transport.client.ClusterAdminClient.class);
        org.opensearch.transport.client.AdminClient adminClient = mock(org.opensearch.transport.client.AdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdmin);
        when(adminClient.cluster()).thenReturn(clusterAdmin);

        // Index doesn't exist
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse> mappingListener = invocation.getArgument(1);
            mappingListener.onFailure(new org.opensearch.index.IndexNotFoundException(".plugins-ml-am-longterm-fail-memory-long-term"));
            return null;
        }).when(indicesAdmin).getMappings(any(), any());

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.ingest.GetPipelineResponse> pipelineListener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse response = mock(org.opensearch.action.ingest.GetPipelineResponse.class);
            when(response.pipelines()).thenReturn(Collections.emptyList());
            pipelineListener.onResponse(response);
            return null;
        }).when(clusterAdmin).getPipeline(any(org.opensearch.action.ingest.GetPipelineRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.support.clustermanager.AcknowledgedResponse> putListener = invocation.getArgument(1);
            org.opensearch.action.support.clustermanager.AcknowledgedResponse response = mock(
                org.opensearch.action.support.clustermanager.AcknowledgedResponse.class
            );
            when(response.isAcknowledged()).thenReturn(true);
            putListener.onResponse(response);
            return null;
        }).when(clusterAdmin).putPipeline(any(org.opensearch.action.ingest.PutPipelineRequest.class), any());

        // Mock long-term index creation FAILURE
        doAnswer(invocation -> {
            ActionListener<Boolean> longTermListener = invocation.getArgument(3);
            longTermListener.onFailure(new RuntimeException("Failed to create long-term index"));
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());

        action.doExecute(task, request, listener);

        // Verify failure - long-term index creation should fail
        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

}
