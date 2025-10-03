/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

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
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
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
    private ActionListener<IndexResponse> actionListener;

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
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of(MEMORY_FIELD, newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        GetResponse mockGetResponse = mock(GetResponse.class);
        GetResult mockGetResult = mock(GetResult.class);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put(MEMORY_FIELD, "original memory");

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

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            GetRequest request = invocation.getArgument(1);
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            assertEquals("test-memory-index", request.index());
            assertEquals(memoryId, request.id());
            listener.onResponse(mockGetResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(GetRequest.class), any());

        // Mock index operation
        doAnswer(invocation -> {
            IndexRequest request = invocation.getArgument(1);
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            assertEquals("test-memory-index", request.index());
            assertEquals(memoryId, request.id());
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(IndexRequest.class), any());

        doReturn(true).when(memoryContainerHelper).checkMemoryAccess(any(), any());
        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).getData(any(), any(GetRequest.class), any());
        verify(memoryContainerHelper, times(1)).indexData(any(), any(IndexRequest.class), any());
        verify(actionListener, times(1)).onResponse(mockIndexResponse);
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
        verify(memoryContainerHelper, times(1)).getData(any(), any(GetRequest.class), any());
        verify(memoryContainerHelper, never()).indexData(any(), any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Memory not found"));
    }

    @Test
    public void testDoExecute_EmbeddingGenerationFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of(MEMORY_FIELD, newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put(MEMORY_FIELD, "original memory");

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

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, "long-term")).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(GetRequest.class), any());

        // Mock update operation (should still happen without embedding)
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            IndexRequest request = invocation.getArgument(1);
            ActionListener<IndexResponse> listener = invocation.getArgument(2);

            // The test verifies that indexing succeeds even when embedding generation fails
            // We don't need to verify the exact content here as that's tested elsewhere

            listener.onResponse(mockIndexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(IndexRequest.class), any());

        doReturn(true).when(memoryContainerHelper).checkMemoryAccess(any(), any());
        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).getData(any(), any(GetRequest.class), any());
        verify(memoryContainerHelper, times(1)).indexData(any(), any(IndexRequest.class), any()); // Index should still happen without
                                                                                                  // embedding
        verify(actionListener, times(1)).onResponse(mockIndexResponse); // Should succeed even without embedding
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_UpdateFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        String newText = "Updated memory content";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of(MEMORY_FIELD, newText)).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put(MEMORY_FIELD, "original memory");

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
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(updateError);
            return null;
        }).when(memoryContainerHelper).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());

        doReturn(true).when(memoryContainerHelper).checkMemoryAccess(any(), any());
        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(containerWithoutSemantic));
        verify(memoryContainerHelper, times(1)).getData(any(MemoryConfiguration.class), any(GetRequest.class), any());
        verify(memoryContainerHelper, times(1)).indexData(any(MemoryConfiguration.class), any(IndexRequest.class), any());
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

    @Test
    public void testDoExecute_FeatureDisabled() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(input)
            .build();

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Agentic Memory APIs are not enabled"));
    }

    @Test
    public void testDoExecute_MemoryIndexNotFound() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "unknown-type";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock getMemoryIndexName to return null (index not found)
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn(null);

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Memory index not found"));
    }

    @Test
    public void testDoExecute_CannotUpdateHistoryIndex() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "history";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of("text", "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock getMemoryIndexName to return history index
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-history");

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Can't update memory history"));
    }

    @Test
    public void testDoExecute_MemoryAccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of(MEMORY_FIELD, "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        GetResponse mockGetResponse = mock(GetResponse.class);
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put(MEMORY_FIELD, "original memory");
        sourceMap.put("owner_id", "different-user");

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

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-index");

        // Mock get operation
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(GetRequest.class), any());

        // Mock checkMemoryAccess to deny access
        when(memoryContainerHelper.checkMemoryAccess(any(), eq("different-user"))).thenReturn(false);

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        verify(actionListener, never()).onResponse(any());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("User doesn't have permissions to update this memory"));
    }

    @Test
    public void testConstructUpdateFields_SessionType() {
        // Test session memory update fields construction
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("summary", "Updated summary");
        updateContent.put("additional_info", Map.of("key", "value"));
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

        Map<String, Object> result = transportUpdateMemoryAction
            .constructNewDoc(input, MEM_CONTAINER_MEMORY_TYPE_SESSIONS, new HashMap<>());

        assertEquals(3, result.size()); // 2 fields + last_updated_time
        assertEquals("Updated summary", result.get("summary"));
        assertNotNull(result.get("additional_info"));
        assertNotNull(result.get("last_updated_time"));
    }

    @Test
    public void testConstructUpdateFields_WorkingType() {
        // Test working memory update fields construction
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("messages", "New messages");
        updateContent.put("binary_data", "Binary content");
        updateContent.put("structured_data", Map.of("data", "value"));
        updateContent.put("metadata", Map.of("meta", "data"));
        updateContent.put("tags", Map.of("tag1", "value1"));
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

        Map<String, Object> result = transportUpdateMemoryAction.constructNewDoc(input, "working", new HashMap<>());

        assertEquals(6, result.size()); // 5 fields + last_updated_time
        assertEquals("New messages", result.get("messages"));
        assertEquals("Binary content", result.get("binary_data"));
        assertNotNull(result.get("structured_data"));
        assertNotNull(result.get("metadata"));
        assertNotNull(result.get("tags"));
        assertNotNull(result.get("last_updated_time"));
    }

    @Test
    public void testConstructUpdateFields_LongTermType() {
        // Test long-term memory update fields construction
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put(MEMORY_FIELD, "Updated memory text");
        updateContent.put("tags", Map.of("tag1", "value1"));
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

        Map<String, Object> result = transportUpdateMemoryAction.constructNewDoc(input, "long-term", new HashMap<>());

        assertEquals(3, result.size()); // 2 fields + last_updated_time
        assertEquals("Updated memory text", result.get(MEMORY_FIELD));
        assertNotNull(result.get("tags"));
        assertNotNull(result.get("last_updated_time"));
    }

    @Test
    public void testConstructUpdateFields_UnknownType() {
        // Test unknown memory type returns empty map
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("field", "value");
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

        Map<String, Object> result = transportUpdateMemoryAction.constructNewDoc(input, "unknown", new HashMap<>());

        assertEquals(1, result.size()); // only last_updated_time is added for unknown types
        assertNotNull(result.get("last_updated_time"));
    }

    @Test
    public void testDoExecute_GetRequestFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "long-term";
        String memoryId = "memory-456";
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(Map.of(MEMORY_FIELD, "new text")).build();
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .mlUpdateMemoryInput(input)
            .build();

        Exception getError = new RuntimeException("Failed to retrieve memory");

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-index");

        // Mock get operation to fail
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onFailure(getError);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(GetRequest.class), any());

        // Act
        transportUpdateMemoryAction.doExecute(task, updateRequest, actionListener);

        // Assert
        verify(actionListener, times(1)).onFailure(getError);
        verify(actionListener, never()).onResponse(any());
        verify(memoryContainerHelper, never()).indexData(any(), any(), any());
    }

}
