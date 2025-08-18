/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MemoryContainerHelperTests {

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private ActionListener<MLMemoryContainer> listener;

    private MemoryContainerHelper helper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create real ThreadContext since it's final and can't be mocked
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        helper = new MemoryContainerHelper(client, sdkClient, xContentRegistry);
    }

    @Test
    public void testGetMemoryContainerSuccess() {
        String memoryContainerId = "container-123";
        String mockJsonSource = "{\"name\":\"test-container\",\"memory_storage_config\":{\"memory_index_name\":\"test-index\"}}";

        // Create mock GetResponse
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(mockJsonSource);

        // Create mock GetDataObjectResponse
        GetDataObjectResponse getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(memoryContainerId);

        // Create CompletableFuture with proper typed response
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, listener);

        // Complete the future with the mock response
        future.complete(getDataObjectResponse);

        // Verify sdkClient was called with GetDataObjectRequest
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class));
    }

    @Test
    public void testGetMemoryContainerWithTenantId() {
        String memoryContainerId = "container-123";
        String tenantId = "tenant-456";
        String mockJsonSource = "{\"name\":\"test-container\"}";

        // Create mock GetResponse
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(mockJsonSource);

        // Create mock GetDataObjectResponse
        GetDataObjectResponse getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(memoryContainerId);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, tenantId, listener);
        future.complete(getDataObjectResponse);

        // Verify sdkClient was called with GetDataObjectRequest
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class));
    }

    @Test
    public void testGetMemoryContainerNotFound() {
        String memoryContainerId = "container-123";

        // Create mock GetResponse for non-existent container
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn(null);

        // Create mock GetDataObjectResponse
        GetDataObjectResponse getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(memoryContainerId);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, listener);
        future.complete(getDataObjectResponse);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("Memory container not found", exception.getMessage());
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testGetMemoryContainerIndexNotFoundException() {
        String memoryContainerId = "container-123";

        IndexNotFoundException indexNotFoundException = new IndexNotFoundException("index not found");
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(indexNotFoundException);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, listener);

        // Need to wait a bit for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("Memory container not found", exception.getMessage());
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testGetMemoryContainerGeneralError() {
        String memoryContainerId = "container-123";

        RuntimeException runtimeException = new RuntimeException("General error");
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(runtimeException);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, listener);

        // Need to wait a bit for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof RuntimeException);
        assertEquals("General error", exception.getMessage());
    }

    @Test
    public void testCheckMemoryContainerAccessWithNullUser() {
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").build();

        assertTrue(helper.checkMemoryContainerAccess(null, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithAdminUser() {
        // User constructor: name, backend_roles, roles, custom_attributes
        // The "all_access" should be in roles (third parameter), not backend_roles
        User adminUser = new User("admin", Arrays.asList("backend-role"), Arrays.asList("all_access"), null);
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").build();

        assertTrue(helper.checkMemoryContainerAccess(adminUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessAsOwner() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), null);
        User accessingUser = new User("owner-user", Arrays.asList("backend-role2"), Arrays.asList("role2"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertTrue(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithMatchingBackendRole() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1", "backend-role2"), Arrays.asList("role1"), null);
        User accessingUser = new User("different-user", Arrays.asList("backend-role2", "backend-role3"), Arrays.asList("role2"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertTrue(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessDenied() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), null);
        User accessingUser = new User("different-user", Arrays.asList("backend-role2"), Arrays.asList("role2"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithNullOwner() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User accessingUser = new User("some-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(null).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testGetMemoryIndexNameWithConfig() {
        MemoryStorageConfig config = MemoryStorageConfig.builder().memoryIndexName("custom-memory-index").build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(config).build();

        assertEquals("custom-memory-index", helper.getMemoryIndexName(container));
    }

    @Test
    public void testGetMemoryIndexNameWithoutConfig() {
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(null).build();

        assertNull(helper.getMemoryIndexName(container));
    }

    @Test
    public void testGetMemoryIndexNameWithEmptyConfig() {
        MemoryStorageConfig config = MemoryStorageConfig.builder().memoryIndexName(null).build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(config).build();

        assertNull(helper.getMemoryIndexName(container));
    }

    @Test
    public void testValidateMemoryIndexExistsSuccess() {
        MemoryStorageConfig config = MemoryStorageConfig.builder().memoryIndexName("valid-index").build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(config).build();

        ActionListener<String> mockListener = mock(ActionListener.class);
        boolean result = helper.validateMemoryIndexExists(container, "test-action", mockListener);

        assertTrue(result);
        verify(mockListener, never()).onFailure(any());
    }

    @Test
    public void testValidateMemoryIndexExistsFailureNoIndex() {
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(null).build();

        ActionListener<String> mockListener = mock(ActionListener.class);
        boolean result = helper.validateMemoryIndexExists(container, "test-action", mockListener);

        assertFalse(result);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(mockListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("Memory container does not have a memory index configured for test-action", exception.getMessage());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testValidateMemoryIndexExistsFailureEmptyIndex() {
        MemoryStorageConfig config = MemoryStorageConfig.builder().memoryIndexName("").build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").memoryStorageConfig(config).build();

        ActionListener<String> mockListener = mock(ActionListener.class);
        boolean result = helper.validateMemoryIndexExists(container, "another-action", mockListener);

        assertFalse(result);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(mockListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertTrue(exception.getMessage().contains("another-action"));
    }

    @Test
    public void testCheckMemoryContainerAccessWithNullBackendRoles() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", null, Arrays.asList("role1"), null);
        User accessingUser = new User("different-user", Arrays.asList("backend-role1"), Arrays.asList("role2"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessBothNullBackendRoles() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", null, Arrays.asList("role1"), null);
        User accessingUser = new User("different-user", null, Arrays.asList("role2"), null);

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }
}
