/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportDeleteMemoryContainerActionTests extends OpenSearchTestCase {

    private static final String MEMORY_CONTAINER_ID = "memory_container_id";
    private static final String TENANT_ID = "tenant_id";
    DeleteResponse deleteResponse = new DeleteResponse(
        new ShardId(ML_MEMORY_CONTAINER_INDEX, "_na_", 0),
        MEMORY_CONTAINER_ID,
        1,
        0,
        2,
        true
    );

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    ClusterService clusterService;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    TransportDeleteMemoryContainerAction transportDeleteMemoryContainerAction;
    MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest;
    ThreadContext threadContext;
    private GetDataObjectResponse getDataObjectResponse;
    private DeleteDataObjectResponse deleteDataObjectResponse;
    private GetResponse getResponse;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        // Setup GetResponse
        getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"name\":\"test_container\"}");

        // Setup GetDataObjectResponse
        getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(MEMORY_CONTAINER_ID);

        // Setup DeleteDataObjectResponse
        deleteDataObjectResponse = mock(DeleteDataObjectResponse.class);
        when(deleteDataObjectResponse.deleteResponse()).thenReturn(deleteResponse);
        when(deleteDataObjectResponse.id()).thenReturn(MEMORY_CONTAINER_ID);

        Settings settings = Settings.builder().build();
        mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId("test_id")
            .deleteAllMemories(false)
            .deleteMemories(null)
            .tenantId(null)
            .build();
        transportDeleteMemoryContainerAction = spy(
            new TransportDeleteMemoryContainerAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                connectorAccessControlHelper,
                memoryContainerHelper,
                mlFeatureEnabledSetting
            )
        );

        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Setup ML feature settings
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests
    }

    @Test
    public void testDeleteMemoryContainer_Success() throws InterruptedException {
        CompletableFuture<GetDataObjectResponse> getFuture = CompletableFuture.completedFuture(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(getFuture);
        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        transportDeleteMemoryContainerAction.doExecute(null, mlMemoryContainerDeleteRequest, actionListener);

        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    @Test
    public void testUserHasNoAccessException() throws IOException {
        CompletableFuture<GetDataObjectResponse> getFuture = CompletableFuture.completedFuture(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(getFuture);

        // Mock access control to return false
        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(false);

        transportDeleteMemoryContainerAction.doExecute(null, mlMemoryContainerDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Only container owner can delete container", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteMemoryContainer_Failure() {
        CompletableFuture<GetDataObjectResponse> getFuture = CompletableFuture.completedFuture(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(getFuture);
        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        CompletableFuture<DeleteDataObjectResponse> deleteFuture = new CompletableFuture<>();
        deleteFuture.completeExceptionally(new Exception("errorMessage"));
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        transportDeleteMemoryContainerAction.doExecute(null, mlMemoryContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find memory container", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteMemoryContainer_IndexNotFoundException() {
        // Setup getMemoryContainer to throw IndexNotFoundException
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Memory container index not found"));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        transportDeleteMemoryContainerAction.doExecute(null, mlMemoryContainerDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());

        Exception exception = argumentCaptor.getValue();
        assertTrue(exception instanceof IndexNotFoundException);
        assertTrue(exception.getMessage().contains("Memory container index not found"));
    }

    public void testDeleteMemoryContainer_MultiTenancyEnabled_ValidTenantId() {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create request with tenant ID
        MLMemoryContainerDeleteRequest requestWithTenant = MLMemoryContainerDeleteRequest.builder()
                .memoryContainerId(MEMORY_CONTAINER_ID)
                .deleteAllMemories(false)
                .deleteMemories(null)
                .tenantId(TENANT_ID)
                .build();

        CompletableFuture<GetDataObjectResponse> getFuture = CompletableFuture.completedFuture(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(getFuture);
        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        transportDeleteMemoryContainerAction.doExecute(null, requestWithTenant, actionListener);

        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
    }

    public void testDeleteMemoryContainer_MultiTenancyEnabled_NoTenantId() throws InterruptedException {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create a request without a tenant ID
        MLMemoryContainerDeleteRequest requestWithoutTenant = MLMemoryContainerDeleteRequest.builder()
                .memoryContainerId(MEMORY_CONTAINER_ID)
                .deleteAllMemories(false)
                .deleteMemories(null)
                .tenantId(null)
                .build();

        transportDeleteMemoryContainerAction.doExecute(null, requestWithoutTenant, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteMemoryContainer_UnauthorizedUser() {
        // Create request
        MLMemoryContainerDeleteRequest request = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup unauthorized user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "unauthorized-user||");

        // Disable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, request, actionListener);

        // Verify failure response with FORBIDDEN status (unauthorized user should be denied access)
        verify(actionListener, timeout(1000))
            .onFailure(
                argThat(
                    exception -> exception instanceof OpenSearchStatusException
                        && ((OpenSearchStatusException) exception).status() == RestStatus.FORBIDDEN
                        && exception.getMessage().contains("Only container owner can delete container")
                )
            );
    }

    public void testDoExecuteWithAgenticMemoryDisabled() throws InterruptedException {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        // Create request
        MLMemoryContainerDeleteRequest request = MLMemoryContainerDeleteRequest.builder()
                .memoryContainerId(MEMORY_CONTAINER_ID)
                .deleteAllMemories(false)
                .deleteMemories(null)
                .tenantId(null)
                .build();

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, request, actionListener);

        // Verify failure response due to feature being disabled
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) exception;
        assertEquals(RestStatus.FORBIDDEN, statusException.status());
        assertEquals("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled", exception.getMessage());
    }

    @Test
    public void testHandleDeleteResponse_WithDeleteAllMemories_Success() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("test-memory-prefix");
        when(mockConfig.getSessionIndexName()).thenReturn("test-memory-prefix-sessions");
        when(mockConfig.getWorkingMemoryIndexName()).thenReturn("test-memory-prefix-working");
        when(mockConfig.getLongMemoryIndexName()).thenReturn("test-memory-prefix-long-term");
        when(mockConfig.getLongMemoryHistoryIndexName()).thenReturn("test-memory-prefix-history");

        // Setup delete request with deleteAllMemories = true
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(true)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to return 1 (sole owner - safe to delete indices)
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onResponse(1L);
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Mock successful deleteIndex operation
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(2);
            AcknowledgedResponse acknowledgedResponse = mock(AcknowledgedResponse.class);
            listener.onResponse(acknowledgedResponse);
            return null;
        }).when(memoryContainerHelper).deleteIndex(any(), any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was called with correct index names
        ArgumentCaptor<org.opensearch.action.admin.indices.delete.DeleteIndexRequest> deleteIndexCaptor = ArgumentCaptor
            .forClass(org.opensearch.action.admin.indices.delete.DeleteIndexRequest.class);
        verify(memoryContainerHelper).deleteIndex(any(), deleteIndexCaptor.capture(), any());

        org.opensearch.action.admin.indices.delete.DeleteIndexRequest capturedRequest = deleteIndexCaptor.getValue();
        String[] indices = capturedRequest.indices();
        assertEquals(4, indices.length);
        assertEquals("test-memory-prefix-sessions", indices[0]);
        assertEquals("test-memory-prefix-working", indices[1]);
        assertEquals("test-memory-prefix-long-term", indices[2]);
        assertEquals("test-memory-prefix-history", indices[3]);

        // Verify final success response
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());
        assertEquals(deleteResponse.getId(), responseCaptor.getValue().getId());
    }

    @Test
    public void testHandleDeleteResponse_WithDeleteAllMemories_DeleteIndexFailure() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("test-memory-prefix");
        when(mockConfig.getSessionIndexName()).thenReturn("test-memory-prefix-sessions");
        when(mockConfig.getWorkingMemoryIndexName()).thenReturn("test-memory-prefix-working");
        when(mockConfig.getLongMemoryIndexName()).thenReturn("test-memory-prefix-long-term");
        when(mockConfig.getLongMemoryHistoryIndexName()).thenReturn("test-memory-prefix-history");

        // Setup delete request with deleteAllMemories = true
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(true)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to return 1 (sole owner - safe to delete indices)
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onResponse(1L);
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Mock failed deleteIndex operation
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Failed to delete memory indices"));
            return null;
        }).when(memoryContainerHelper).deleteIndex(any(), any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was called
        verify(memoryContainerHelper).deleteIndex(any(), any(), any());

        // Verify failure response due to deleteIndex failure
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertEquals("Failed to delete memory indices", exception.getMessage());
    }

    @Test
    public void testHandleDeleteResponse_WithoutDeleteAllMemories_Success() {
        // Setup mock container
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);

        // Setup delete request with deleteAllMemories = false (default)
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was NOT called since deleteAllMemories is false
        verify(memoryContainerHelper, org.mockito.Mockito.never()).deleteIndex(any(), any(), any());

        // Verify success response is returned directly
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());
        assertEquals(deleteResponse.getId(), responseCaptor.getValue().getId());
    }

    @Test
    public void testHandleDeleteResponse_WithDeleteAllMemories_NullConfiguration() {
        // Setup mock container with null configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        when(mockContainer.getConfiguration()).thenReturn(null);

        // Setup delete request with deleteAllMemories = true
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(true)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify that an exception is thrown due to null configuration
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof NullPointerException || exception.getMessage().contains("configuration"));
    }

    @Test
    public void testDeleteMemoryContainer_WithSelectiveMemories_Success() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("test-memory-prefix");
        when(mockConfig.getSessionIndexName()).thenReturn("test-memory-prefix-sessions");
        when(mockConfig.getWorkingMemoryIndexName()).thenReturn("test-memory-prefix-working");
        when(mockConfig.getLongMemoryIndexName()).thenReturn("test-memory-prefix-long-term");
        when(mockConfig.getLongMemoryHistoryIndexName()).thenReturn("test-memory-prefix-history");

        // Setup delete request with selective memories
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(new LinkedHashSet<>(Arrays.asList(MemoryType.SESSIONS, MemoryType.WORKING)))
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to return 1 (sole owner - safe to delete indices)
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onResponse(1L);
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Mock successful deleteIndex operation
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(2);
            AcknowledgedResponse acknowledgedResponse = mock(AcknowledgedResponse.class);
            listener.onResponse(acknowledgedResponse);
            return null;
        }).when(memoryContainerHelper).deleteIndex(any(), any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was called with correct index names (only session and working)
        ArgumentCaptor<org.opensearch.action.admin.indices.delete.DeleteIndexRequest> deleteIndexCaptor = ArgumentCaptor
            .forClass(org.opensearch.action.admin.indices.delete.DeleteIndexRequest.class);
        verify(memoryContainerHelper).deleteIndex(any(), deleteIndexCaptor.capture(), any());

        org.opensearch.action.admin.indices.delete.DeleteIndexRequest capturedRequest = deleteIndexCaptor.getValue();
        String[] indices = capturedRequest.indices();
        assertEquals(2, indices.length);
        assertEquals("test-memory-prefix-sessions", indices[0]);
        assertEquals("test-memory-prefix-working", indices[1]);

        // Verify final success response
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());
        assertEquals(deleteResponse.getId(), responseCaptor.getValue().getId());
    }

    @Test
    public void testDeleteMemoryContainer_WithSelectiveMemories_UnknownType() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("test-memory-prefix");
        when(mockConfig.getSessionIndexName()).thenReturn("test-memory-prefix-sessions");

        // Setup delete request with selective memories (cannot test unknown type with enum)
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(new LinkedHashSet<>(Arrays.asList(MemoryType.SESSIONS)))
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to return 1 (sole owner - safe to delete indices)
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onResponse(1L);
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Mock successful deleteIndex operation
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(2);
            AcknowledgedResponse acknowledgedResponse = mock(AcknowledgedResponse.class);
            listener.onResponse(acknowledgedResponse);
            return null;
        }).when(memoryContainerHelper).deleteIndex(any(), any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was called with only valid index (session)
        ArgumentCaptor<org.opensearch.action.admin.indices.delete.DeleteIndexRequest> deleteIndexCaptor = ArgumentCaptor
            .forClass(org.opensearch.action.admin.indices.delete.DeleteIndexRequest.class);
        verify(memoryContainerHelper).deleteIndex(any(), deleteIndexCaptor.capture(), any());

        org.opensearch.action.admin.indices.delete.DeleteIndexRequest capturedRequest = deleteIndexCaptor.getValue();
        String[] indices = capturedRequest.indices();
        assertEquals(1, indices.length);
        assertEquals("test-memory-prefix-sessions", indices[0]);
    }

    @Test
    public void testDeleteMemoryContainer_WithEmptySelectiveMemories() {
        // Setup mock container
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        // Setup delete request with empty selective memories list
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(Collections.emptySet())
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was NOT called since the list is empty
        verify(memoryContainerHelper, timeout(1000).times(0)).deleteIndex(any(), any(), any());

        // Verify final success response (container deleted, no indices)
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());
        assertEquals(deleteResponse.getId(), responseCaptor.getValue().getId());
    }

    @Test
    public void testDeleteMemoryContainer_WithSharedPrefix_PreventsDeletion() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("shared-prefix");

        // Setup delete request with deleteAllMemories = true
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(true)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to return 2 (shared prefix - unsafe to delete indices)
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onResponse(2L);
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex was NEVER called (prevented due to shared prefix)
        verify(memoryContainerHelper, timeout(1000).times(0)).deleteIndex(any(), any(), any());

        // Verify failure response with CONFLICT status
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) exception;
        assertEquals(RestStatus.CONFLICT, statusException.status());
        assertTrue(exception.getMessage().contains("multiple containers share"));
        assertTrue(exception.getMessage().contains("shared-prefix"));
    }

    @Test
    public void testDeleteMemoryContainer_CountCheckFailure_AbortsOperation() {
        // Setup mock container with configuration
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);
        when(mockConfig.getIndexPrefix()).thenReturn("test-prefix");

        // Setup delete request
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(true)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock countContainersWithPrefix to fail
        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Failed to count containers"));
            return null;
        }).when(memoryContainerHelper).countContainersWithPrefix(any(), any(), any());

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify deleteIndex and delete container were NEVER called (operation aborted)
        verify(memoryContainerHelper, timeout(1000).times(0)).deleteIndex(any(), any(), any());
        verify(sdkClient, timeout(1000).times(0)).deleteDataObjectAsync(any());

        // Verify failure response
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Failed to count containers"));
    }

    @Test
    public void testDeleteMemoryContainer_WithoutIndexDeletion_SkipsCountCheck() {
        // Setup mock container
        MLMemoryContainer mockContainer = mock(MLMemoryContainer.class);
        User owner = new User("test_user", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(mockContainer.getOwner()).thenReturn(owner);

        org.opensearch.ml.common.memorycontainer.MemoryConfiguration mockConfig = mock(
            org.opensearch.ml.common.memorycontainer.MemoryConfiguration.class
        );
        when(mockContainer.getConfiguration()).thenReturn(mockConfig);

        // Setup delete request WITHOUT index deletion (deleteAllMemories=false, deleteMemories=null)
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(MEMORY_CONTAINER_ID)
            .deleteAllMemories(false)
            .deleteMemories(null)
            .tenantId(null)
            .build();

        when(memoryContainerHelper.checkMemoryAccess(any(), any())).thenReturn(true);

        // Mock successful memory container retrieval
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Setup successful delete data object response
        CompletableFuture<DeleteDataObjectResponse> deleteFuture = CompletableFuture.completedFuture(deleteDataObjectResponse);
        when(sdkClient.deleteDataObjectAsync(any())).thenReturn(deleteFuture);

        // Execute
        transportDeleteMemoryContainerAction.doExecute(null, deleteRequest, actionListener);

        // Verify countContainersWithPrefix was NEVER called (no index deletion requested)
        verify(memoryContainerHelper, timeout(1000).times(0)).countContainersWithPrefix(any(), any(), any());

        // Verify deleteIndex was NEVER called
        verify(memoryContainerHelper, timeout(1000).times(0)).deleteIndex(any(), any(), any());

        // Verify success response
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());
        assertEquals(deleteResponse.getId(), responseCaptor.getValue().getId());
    }

}
