/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoryRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportDeleteMemoryActionTests extends OpenSearchTestCase {

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
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private Task task;

    @Mock
    private ActionListener<DeleteResponse> actionListener;

    private TransportDeleteMemoryAction transportDeleteMemoryAction;

    private MLMemoryContainer mockContainer;
    private User mockUser;
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

        // Setup mock container
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test container")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory-index").build())
            .build();

        // Setup mock user
        mockUser = User.parse("test-user|test-backend-role");

        // Initialize transport action
        transportDeleteMemoryAction = spy(
            new TransportDeleteMemoryAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                connectorAccessControlHelper,
                mlFeatureEnabledSetting,
                memoryContainerHelper
            )
        );
    }

    @Test
    public void testDoExecute_Success() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), any(), eq("delete"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-index");

        // Mock delete operation
        doAnswer(invocation -> {
            DeleteRequest request = invocation.getArgument(0);
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            assertEquals("test-memory-index", request.index());
            assertEquals(memoryId, request.id());
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("delete"), any());
        verify(client, times(1)).delete(any(DeleteRequest.class), any());
        verify(actionListener, times(1)).onResponse(mockDeleteResponse);
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_GetContainerFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        Exception expectedError = new RuntimeException("Container not found");

        // Mock getMemoryContainer to fail
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onFailure(expectedError);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, never()).checkMemoryContainerAccess(any(), any());
        verify(client, never()).delete(any(), any());
        verify(actionListener, times(1)).onFailure(expectedError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess to return false (user is null)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(false);

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, never()).validateMemoryIndexExists(any(), any(), any(), any());
        verify(client, never()).delete(any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("doesn't have permissions"));
    }

    @Test
    public void testDoExecute_MemoryIndexNotExists() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists to return false
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), any(), eq("delete"), any())).thenReturn(false);

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("delete"), any());
        verify(memoryContainerHelper, never()).getMemoryIndexName(any(), any());
        verify(client, never()).delete(any(), any());
        // validateMemoryIndexExists handles the error response
    }

    @Test
    public void testDoExecute_DeleteFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        Exception deleteError = new RuntimeException("Delete failed");

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), any(), eq("delete"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-index");

        // Mock delete operation to fail
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(deleteError);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("delete"), any());
        verify(client, times(1)).delete(any(DeleteRequest.class), any());
        verify(actionListener, times(1)).onFailure(deleteError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_ExceptionDuringDelete() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        RuntimeException unexpectedError = new RuntimeException("Unexpected error");

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess (user is null from RestActionUtils.getUserContext)
        when(memoryContainerHelper.checkMemoryContainerAccess(isNull(), eq(mockContainer))).thenReturn(true);

        // Mock validateMemoryIndexExists
        when(memoryContainerHelper.validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("delete"), any())).thenReturn(true);

        // Mock getMemoryIndexName
        when(memoryContainerHelper.getMemoryIndexName(mockContainer, memoryType)).thenReturn("test-memory-index");

        // Mock client.delete to throw exception
        doThrow(unexpectedError).when(client).delete(any(DeleteRequest.class), any());

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(isNull(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).validateMemoryIndexExists(eq(mockContainer), eq(memoryType), eq("delete"), any());
        verify(client, times(1)).delete(any(), any());
        verify(actionListener, times(1)).onFailure(unexpectedError);
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testDoExecute_NullContainer() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest deleteRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        // Mock getMemoryContainer to return null container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess to handle null
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(null))).thenReturn(false);

        // Act
        transportDeleteMemoryAction.doExecute(task, deleteRequest, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(null));
        verify(client, never()).delete(any(), any());

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue() instanceof OpenSearchStatusException);
    }

    @Test
    public void testFromActionRequest() {
        // Test that MLDeleteMemoryRequest.fromActionRequest works correctly
        String memoryContainerId = "container-123";
        String memoryType = "conversation";
        String memoryId = "memory-456";
        MLDeleteMemoryRequest originalRequest = new MLDeleteMemoryRequest(memoryContainerId, memoryType, memoryId);

        MLDeleteMemoryRequest convertedRequest = MLDeleteMemoryRequest.fromActionRequest(originalRequest);

        assertEquals(memoryContainerId, convertedRequest.getMemoryContainerId());
        assertEquals(memoryType, convertedRequest.getMemoryType());
        assertEquals(memoryId, convertedRequest.getMemoryId());
    }
}
