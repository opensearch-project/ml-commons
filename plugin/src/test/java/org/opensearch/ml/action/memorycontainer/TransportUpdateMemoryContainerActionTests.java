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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
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
            memoryContainerHelper
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
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Container not found", exception.getMessage());
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
            .strategies(existingStrategies)
            .build();

        // Create update strategy (disable existing)
        MemoryStrategy updateStrategy = MemoryStrategy.builder().id("semantic_123").enabled(false).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().strategies(Arrays.asList(updateStrategy)).build();

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
            .strategies(existingStrategies)
            .build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.USER_PREFERENCE)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().strategies(Arrays.asList(newStrategy)).build();

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
            .strategies(existingStrategies)
            .build();

        // Try to update non-existent strategy
        MemoryStrategy updateStrategy = MemoryStrategy.builder().id("nonexistent_999").enabled(false).build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().strategies(Arrays.asList(updateStrategy)).build();

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
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exception).status());
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
            .strategies(existingStrategies)
            .build();

        // Try to change strategy type
        MemoryStrategy updateStrategy = MemoryStrategy
            .builder()
            .id("semantic_123")
            .type(MemoryStrategyType.USER_PREFERENCE)  // Try to change type
            .enabled(false)
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().strategies(Arrays.asList(updateStrategy)).build();

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
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("Cannot change strategy type"));
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
            .strategies(existingStrategies)
            .build();

        // Update only namespace, not enabled
        MemoryStrategy updateStrategy = MemoryStrategy
            .builder()
            .id("semantic_123")
            .namespace(Arrays.asList("user_id", "session_id"))
            .build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().strategies(Arrays.asList(updateStrategy)).build();

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
}
