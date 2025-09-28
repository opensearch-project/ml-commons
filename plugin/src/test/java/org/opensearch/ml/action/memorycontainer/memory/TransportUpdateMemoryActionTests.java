/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportUpdateMemoryActionTests extends OpenSearchTestCase {

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
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private Task task;

    @Mock
    private ActionListener<UpdateResponse> actionListener;

    private TransportUpdateMemoryAction transportUpdateMemoryAction;

    private MLMemoryContainer mockContainer;
    private Settings settings;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        settings = Settings.builder().build();

        // Setup thread context with real instance
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock ML feature settings
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Setup mock container with semantic storage
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory-index")
                    .disableHistory(true)
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .embeddingModelId("embedding-model-123")
                    .dimension(768)
                    .build()
            )
            .build();

        // Initialize transport action
        transportUpdateMemoryAction = spy(
            new TransportUpdateMemoryAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                mlFeatureEnabledSetting,
                memoryContainerHelper
            )
        );
    }

    @Test
    public void testDoExecute_Success() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        GetResponse mockGetResponse = mock(GetResponse.class);
        GetResult mockGetResult = mock(GetResult.class);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "original memory");

        when(mockGetResponse.isExists()).thenReturn(true);
        when(mockGetResponse.getSourceAsMap()).thenReturn(sourceMap);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            GetRequest request = invocation.getArgument(0);
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            assertEquals("test-memory-index", request.index());
            assertEquals(memoryId, request.id());
            listener.onResponse(mockGetResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock update operation
        doAnswer(invocation -> {
            UpdateRequest request = invocation.getArgument(0);
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            assertEquals("test-memory-index", request.index());
            assertEquals(memoryId, request.id());
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any());
        verify(client, times(1)).get(any(GetRequest.class), any());
        verify(client, times(1)).update(any(UpdateRequest.class), any());
        verify(actionListener, times(1)).onResponse(mockUpdateResponse);
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_GetContainerFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType("long-term")
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        Exception expectedError = new RuntimeException("Container not found");

        // Mock getMemoryContainer to fail
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onFailure(expectedError);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, never()).checkMemoryContainerAccess(any(), any());
        verify(client, never()).get(any(), any());
        verify(client, never()).update(any(), any());
        verify(actionListener, times(1)).onFailure(expectedError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType("long-term")
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess to return false
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(false);

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, never()).validateMemoryIndexExists(any(), any(), any(), any());
        verify(client, never()).get(any(), any());
        verify(client, never()).update(any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("doesn't have permissions"));
    }

    @Test
    public void testDoExecute_MemoryNotFound() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        when(mockGetResponse.isExists()).thenReturn(false);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, "long-term")).thenReturn("test-memory-index");

        // Mock get operation to return not found
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(MemoryConfiguration.class), any(GetRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any());
        verify(client, times(1)).get(any(GetRequest.class), any());
        verify(client, never()).update(any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Memory not found"));
    }

    @Test
    public void testDoExecute_WithoutSemanticStorage() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        // Setup container without semantic storage
        MLMemoryContainer containerWithoutSemantic = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory-index").disableHistory(false).build())
            .build();

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        GetResponse mockGetResponse = mock(GetResponse.class);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "original memory");

        when(mockGetResponse.isExists()).thenReturn(true);
        when(mockGetResponse.getSourceAsMap()).thenReturn(sourceMap);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(containerWithoutSemantic);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(containerWithoutSemantic))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(containerWithoutSemantic), eq(memoryType), eq("update"), any()))
            .thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(containerWithoutSemantic, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock update operation
        doAnswer(invocation -> {
            UpdateRequest request = invocation.getArgument(0);
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);

            // Verify no embedding field is added
            Map<String, Object> updateFields = (Map<String, Object>) request.doc().sourceAsMap();
            assertFalse(updateFields.containsKey("memory_embedding"));
            assertTrue(updateFields.containsKey("memory"));
            assertEquals(newText, updateFields.get("memory"));

            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(containerWithoutSemantic));
        verify(memoryContainerHelper, times(1)).getMemoryIndexName(eq(containerWithoutSemantic), eq(memoryType));
        verify(client, times(1)).get(any(GetRequest.class), any());
        verify(client, times(1)).update(any(UpdateRequest.class), any());
        verify(actionListener, times(1)).onResponse(mockUpdateResponse);
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_EmbeddingGenerationFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "original memory");

        when(mockGetResponse.isExists()).thenReturn(true);
        when(mockGetResponse.getSourceAsMap()).thenReturn(sourceMap);

        Exception embeddingError = new RuntimeException("Embedding generation failed");

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock update operation (should still happen without embedding)
        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            UpdateRequest request = invocation.getArgument(0);
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);

            // Verify no embedding field is added due to failure
            Map<String, Object> updateFields = (Map<String, Object>) request.doc().sourceAsMap();
            assertFalse(updateFields.containsKey("memory_embedding"));
            assertTrue(updateFields.containsKey("memory"));
            assertEquals(newText, updateFields.get("memory"));

            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("update"), any());
        verify(client, times(1)).get(any(GetRequest.class), any());
        verify(client, times(1)).update(any(UpdateRequest.class), any()); // Update should still happen without embedding
        verify(actionListener, times(1)).onResponse(mockUpdateResponse); // Should succeed even without embedding
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_UpdateFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("memory", "original memory");

        when(mockGetResponse.isExists()).thenReturn(true);
        when(mockGetResponse.getSourceAsMap()).thenReturn(sourceMap);

        Exception updateError = new RuntimeException("Update failed");

        // Setup container without semantic storage for simpler test
        MLMemoryContainer containerWithoutSemantic = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory-index").disableHistory(false).build())
            .build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(containerWithoutSemantic);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(containerWithoutSemantic))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(containerWithoutSemantic), eq(memoryType), eq("update"), any()))
            .thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(containerWithoutSemantic, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(MemoryConfiguration.class), any(GetRequest.class), any());

        // Mock update operation to fail
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onFailure(updateError);
            return null;
        }).when(memoryContainerHelper).updateData(any(MemoryConfiguration.class), any(UpdateRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(containerWithoutSemantic));
        verify(memoryContainerHelper, times(1)).getData(any(MemoryConfiguration.class), any(GetRequest.class), any());
        verify(memoryContainerHelper, times(1)).updateData(any(MemoryConfiguration.class), any(UpdateRequest.class), any());
        verify(actionListener, times(1)).onFailure(updateError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testFromActionRequest() {
        // Test that MLUpdateMemoryRequest.fromActionRequest works correctly
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "updated text")).build();
        MLUpdateMemoryRequest originalRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(input)
            .build();

        MLUpdateMemoryRequest convertedRequest = MLUpdateMemoryRequest.fromActionRequest(originalRequest);

        assertEquals(input, convertedRequest.getMlUpdateMemoryInput());
    }

}
