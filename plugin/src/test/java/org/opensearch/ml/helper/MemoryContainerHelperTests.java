/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

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

    @Mock
    private AdminClient adminClient;

    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create real ThreadContext since it's final and can't be mocked
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        helper = new MemoryContainerHelper(client, sdkClient, xContentRegistry);

        // Mock the admin client chain
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
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
        User adminUser = new User("admin", Arrays.asList("backend-role"), Arrays.asList("all_access"), Map.of());
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").build();

        assertTrue(helper.checkMemoryContainerAccess(adminUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessAsOwner() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), Map.of());
        User accessingUser = new User("owner-user", Arrays.asList("backend-role2"), Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertTrue(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithMatchingBackendRole() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1", "backend-role2"), Arrays.asList("role1"), Map.of());
        User accessingUser = new User("different-user", Arrays.asList("backend-role2", "backend-role3"), Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertTrue(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessDenied() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), Map.of());
        User accessingUser = new User("different-user", Arrays.asList("backend-role2"), Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithNullOwner() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User accessingUser = new User("some-user", Arrays.asList("backend-role1"), Arrays.asList("role1"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(null).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testGetMemoryIndexNameWithConfig() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("custom-memory-index").build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(config).build();

        assertEquals(
            ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX + "-custom-memory-index-memory-" + "sessions",
            helper.getMemoryIndexName(container, MemoryType.SESSIONS)
        );

        config.setUseSystemIndex(false);
        assertEquals("custom-memory-index-memory-" + "sessions", helper.getMemoryIndexName(container, MemoryType.SESSIONS));

    }

    @Test
    public void testGetMemoryIndexNameWithoutConfig() {
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(null).build();

        assertNotNull(helper.getMemoryIndexName(container, MemoryType.SESSIONS));
    }

    @Test
    public void testGetMemoryIndexNameWithEmptyConfig() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix(null).build();

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(config).build();

        assertNull(helper.getMemoryIndexName(container, null));
    }

    @Test
    public void testCheckMemoryContainerAccessWithNullBackendRoles() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", null, Arrays.asList("role1"), Map.of());
        User accessingUser = new User("different-user", Arrays.asList("backend-role1"), Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessBothNullBackendRoles() {
        // User constructor: name, backend_roles, roles, custom_attributes
        User owner = new User("owner-user", null, Arrays.asList("role1"), Map.of());
        User accessingUser = new User("different-user", null, Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithAllowedBackendRoles() {
        // Test container with explicit backend roles (not from owner)
        User accessingUser = new User("user1", Arrays.asList("backend-role-a"), Arrays.asList("role1"), Map.of());

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .backendRoles(Arrays.asList("backend-role-a", "backend-role-b"))
            .build();

        assertTrue(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryContainerAccessWithNonMatchingAllowedBackendRoles() {
        // Test container with explicit backend roles that don't match user's roles
        User accessingUser = new User("user1", Arrays.asList("backend-role-c"), Arrays.asList("role1"), Map.of());

        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name("test-container")
            .backendRoles(Arrays.asList("backend-role-a", "backend-role-b"))
            .build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testCheckMemoryAccessWithNullUser() {
        assertTrue(helper.checkMemoryAccess(null, "some-owner"));
    }

    @Test
    public void testCheckMemoryAccessWithAdminUser() {
        User adminUser = new User("admin", Arrays.asList("backend-role"), Arrays.asList("all_access"), Map.of());
        assertTrue(helper.checkMemoryAccess(adminUser, "different-owner"));
    }

    @Test
    public void testCheckMemoryAccessAsOwner() {
        User user = new User("owner-user", Arrays.asList("backend-role"), Arrays.asList("role1"), Map.of());
        assertTrue(helper.checkMemoryAccess(user, "owner-user"));
    }

    @Test
    public void testCheckMemoryAccessDenied() {
        User user = new User("user1", Arrays.asList("backend-role"), Arrays.asList("role1"), Map.of());
        assertFalse(helper.checkMemoryAccess(user, "different-user"));
    }

    @Test
    public void testIsAdminUserWithNullUser() {
        assertFalse(helper.isAdminUser(null));
    }

    @Test
    public void testIsAdminUserWithAdminRole() {
        User adminUser = new User("admin", Arrays.asList("backend-role"), Arrays.asList("all_access"), Map.of());
        assertTrue(helper.isAdminUser(adminUser));
    }

    @Test
    public void testIsAdminUserWithoutAdminRole() {
        User regularUser = new User("user1", Arrays.asList("backend-role"), Arrays.asList("role1"), Map.of());
        assertFalse(helper.isAdminUser(regularUser));
    }

    @Test
    public void testIsAdminUserWithNullRoles() {
        User user = new User("user1", Arrays.asList("backend-role"), null, Map.of());
        assertFalse(helper.isAdminUser(user));
    }

    @Test
    public void testGetMemoryIndexNameWithSystemIndexEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test-prefix").useSystemIndex(true).build();
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(config).build();

        String indexName = helper.getMemoryIndexName(container, MemoryType.WORKING);
        assertNotNull(indexName);
        assertTrue(indexName.contains("test-prefix"));
        assertTrue(indexName.contains("working"));
    }

    @Test
    public void testGetMemoryIndexNameWithSystemIndexDisabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test-prefix").useSystemIndex(false).build();
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(config).build();

        String indexName = helper.getMemoryIndexName(container, MemoryType.LONG_TERM);
        assertNotNull(indexName);
        assertTrue(indexName.contains("test-prefix"));
        assertTrue(indexName.contains("long-term"));
        assertFalse(indexName.startsWith("."));
    }

    @Test
    public void testGetMemoryIndexNameForAllMemoryTypes() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").configuration(config).build();

        // Test all valid memory types
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.SESSIONS));
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.WORKING));
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.LONG_TERM));
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.HISTORY));
    }

    @Test
    public void testCheckMemoryContainerAccessWithEmptyBackendRoles() {
        User owner = new User("owner-user", Arrays.asList(), Arrays.asList("role1"), Map.of());
        User accessingUser = new User("different-user", Arrays.asList("backend-role"), Arrays.asList("role2"), Map.of());

        MLMemoryContainer container = MLMemoryContainer.builder().name("test-container").owner(owner).build();

        assertFalse(helper.checkMemoryContainerAccess(accessingUser, container));
    }

    @Test
    public void testGetMemoryContainerParseException() {
        String memoryContainerId = "container-123";
        String invalidJsonSource = "{invalid json}";

        // Create mock GetResponse with invalid JSON
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(invalidJsonSource);

        // Create mock GetDataObjectResponse
        GetDataObjectResponse getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(memoryContainerId);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        helper.getMemoryContainer(memoryContainerId, listener);
        future.complete(getDataObjectResponse);

        // Should handle parse exception
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void testGetDataWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.action.get.GetRequest getRequest = new org.opensearch.action.get.GetRequest("test-index", "doc-id");
        ActionListener<GetResponse> listener = mock(ActionListener.class);

        helper.getData(config, getRequest, listener);

        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any());
    }

    @Test
    public void testGetDataWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.action.get.GetRequest getRequest = new org.opensearch.action.get.GetRequest("test-index", "doc-id");
        ActionListener<GetResponse> listener = mock(ActionListener.class);

        helper.getData(config, getRequest, listener);

        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any());
    }

    @Test
    public void testIndexDataWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.action.index.IndexRequest indexRequest = new org.opensearch.action.index.IndexRequest("test-index");
        ActionListener<org.opensearch.action.index.IndexResponse> listener = mock(ActionListener.class);

        helper.indexData(config, indexRequest, listener);

        verify(client).index(any(org.opensearch.action.index.IndexRequest.class), any());
    }

    @Test
    public void testIndexDataWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.action.index.IndexRequest indexRequest = new org.opensearch.action.index.IndexRequest("test-index");
        ActionListener<org.opensearch.action.index.IndexResponse> listener = mock(ActionListener.class);

        helper.indexData(config, indexRequest, listener);

        verify(client).index(any(org.opensearch.action.index.IndexRequest.class), any());
    }

    @Test
    public void testUpdateDataWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.action.update.UpdateRequest updateRequest = new org.opensearch.action.update.UpdateRequest("test-index", "doc-id");
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        helper.updateData(config, updateRequest, listener);

        verify(client).update(any(org.opensearch.action.update.UpdateRequest.class), any());
    }

    @Test
    public void testUpdateDataWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.action.update.UpdateRequest updateRequest = new org.opensearch.action.update.UpdateRequest("test-index", "doc-id");
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        helper.updateData(config, updateRequest, listener);

        verify(client).update(any(org.opensearch.action.update.UpdateRequest.class), any());
    }

    @Test
    public void testDeleteDataWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.action.delete.DeleteRequest deleteRequest = new org.opensearch.action.delete.DeleteRequest("test-index", "doc-id");
        ActionListener<org.opensearch.action.delete.DeleteResponse> listener = mock(ActionListener.class);

        helper.deleteData(config, deleteRequest, listener);

        verify(client).delete(any(org.opensearch.action.delete.DeleteRequest.class), any());
    }

    @Test
    public void testDeleteDataWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.action.delete.DeleteRequest deleteRequest = new org.opensearch.action.delete.DeleteRequest("test-index", "doc-id");
        ActionListener<org.opensearch.action.delete.DeleteResponse> listener = mock(ActionListener.class);

        helper.deleteData(config, deleteRequest, listener);

        verify(client).delete(any(org.opensearch.action.delete.DeleteRequest.class), any());
    }

    @Test
    public void testBulkIngestDataWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.action.bulk.BulkRequest bulkRequest = new org.opensearch.action.bulk.BulkRequest();
        ActionListener<org.opensearch.action.bulk.BulkResponse> listener = mock(ActionListener.class);

        helper.bulkIngestData(config, bulkRequest, listener);

        verify(client).bulk(any(org.opensearch.action.bulk.BulkRequest.class), any());
    }

    @Test
    public void testBulkIngestDataWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.action.bulk.BulkRequest bulkRequest = new org.opensearch.action.bulk.BulkRequest();
        ActionListener<org.opensearch.action.bulk.BulkResponse> listener = mock(ActionListener.class);

        helper.bulkIngestData(config, bulkRequest, listener);

        verify(client).bulk(any(org.opensearch.action.bulk.BulkRequest.class), any());
    }

    @Test
    public void testGetOwnerIdWithUser() {
        User user = new User("testuser", Arrays.asList("role1"), Arrays.asList("role2"), Map.of());
        String ownerId = helper.getOwnerId(user);
        assertEquals("testuser", ownerId);
    }

    @Test
    public void testGetOwnerIdWithNullUser() {
        String ownerId = helper.getOwnerId(null);
        assertNull(ownerId);
    }

    @Test
    public void testAddUserBackendRolesFilter() {
        User user = new User("testuser", Arrays.asList("backend-role1", "backend-role2"), Arrays.asList("role1"), Map.of());
        org.opensearch.search.builder.SearchSourceBuilder searchSourceBuilder = new org.opensearch.search.builder.SearchSourceBuilder();

        org.opensearch.search.builder.SearchSourceBuilder result = helper.addUserBackendRolesFilter(user, searchSourceBuilder);

        assertNotNull(result);
        assertNotNull(result.query());
    }

    @Test
    public void testAddOwnerIdFilter() {
        User user = new User("testuser", Arrays.asList("backend-role1"), Arrays.asList("role1"), Map.of());
        org.opensearch.search.builder.SearchSourceBuilder searchSourceBuilder = new org.opensearch.search.builder.SearchSourceBuilder();

        helper.addOwnerIdFilter(user, searchSourceBuilder);

        assertNotNull(searchSourceBuilder.query());
    }

    @Test
    public void testSearchDataWithSearchDataObjectRequestSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.remote.metadata.client.SearchDataObjectRequest searchRequest = mock(
            org.opensearch.remote.metadata.client.SearchDataObjectRequest.class
        );
        ActionListener<org.opensearch.action.search.SearchResponse> listener = mock(ActionListener.class);

        CompletableFuture<org.opensearch.remote.metadata.client.SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(future);

        helper.searchData(config, searchRequest, listener);

        verify(sdkClient).searchDataObjectAsync(any());
    }

    @Test
    public void testSearchDataWithSearchDataObjectRequestNonSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.remote.metadata.client.SearchDataObjectRequest searchRequest = mock(
            org.opensearch.remote.metadata.client.SearchDataObjectRequest.class
        );
        ActionListener<org.opensearch.action.search.SearchResponse> listener = mock(ActionListener.class);

        CompletableFuture<org.opensearch.remote.metadata.client.SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(future);

        helper.searchData(config, searchRequest, listener);

        verify(sdkClient).searchDataObjectAsync(any());
    }

    @Test
    public void testDeleteIndexWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest("test-index");
        ActionListener<AcknowledgedResponse> listener = mock(ActionListener.class);

        helper.deleteIndex(config, deleteIndexRequest, listener);

        verify(indicesAdminClient).delete(any(DeleteIndexRequest.class), any());
    }

    @Test
    public void testDeleteIndexWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest("test-index");
        ActionListener<AcknowledgedResponse> listener = mock(ActionListener.class);

        helper.deleteIndex(config, deleteIndexRequest, listener);

        verify(indicesAdminClient).delete(any(DeleteIndexRequest.class), any());
    }

    @Test
    public void testDeleteIndexThreadContextHandling() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest("test-index");
        ActionListener<AcknowledgedResponse> listener = mock(ActionListener.class);

        helper.deleteIndex(config, deleteIndexRequest, listener);

        // Verify that the client.admin().indices().delete() was called
        verify(indicesAdminClient).delete(any(DeleteIndexRequest.class), any());

        // Verify that threadPool and threadContext were accessed for system index
        verify(client).threadPool();
        verify(threadPool).getThreadContext();
    }

    @Test
    public void testDeleteDataByQueryWithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();
        org.opensearch.index.reindex.DeleteByQueryRequest deleteByQueryRequest = new org.opensearch.index.reindex.DeleteByQueryRequest(
            "test-index"
        );
        ActionListener<org.opensearch.index.reindex.BulkByScrollResponse> listener = mock(ActionListener.class);

        helper.deleteDataByQuery(config, deleteByQueryRequest, listener);

        // Verify that the client executed the delete by query action
        verify(client).execute(any(), any(org.opensearch.index.reindex.DeleteByQueryRequest.class), any());
        verify(client).threadPool();
        verify(threadPool).getThreadContext();
    }

    @Test
    public void testDeleteDataByQueryWithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();
        org.opensearch.index.reindex.DeleteByQueryRequest deleteByQueryRequest = new org.opensearch.index.reindex.DeleteByQueryRequest(
            "test-index"
        );
        ActionListener<org.opensearch.index.reindex.BulkByScrollResponse> listener = mock(ActionListener.class);

        helper.deleteDataByQuery(config, deleteByQueryRequest, listener);

        // Verify that the client executed the delete by query action without thread context handling
        verify(client).execute(any(), any(org.opensearch.index.reindex.DeleteByQueryRequest.class), any());
    }

    @Test
    public void testCountContainersWithPrefixSuccess() {
        String indexPrefix = "test-prefix";
        String tenantId = "tenant-123";
        ActionListener<Long> listener = mock(ActionListener.class);

        // Mock SDK search response
        CompletableFuture<org.opensearch.remote.metadata.client.SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class))).thenReturn(future);

        helper.countContainersWithPrefix(indexPrefix, tenantId, listener);

        // Verify the SDK client was called with the right request
        verify(sdkClient).searchDataObjectAsync(any(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class));
    }

    @Test
    public void testCountContainersWithPrefixNullPrefix() {
        ActionListener<Long> listener = mock(ActionListener.class);

        helper.countContainersWithPrefix(null, null, listener);

        // Verify that the listener was called with 0
        verify(listener).onResponse(0L);
    }

    @Test
    public void testCountContainersWithPrefixBlankPrefix() {
        ActionListener<Long> listener = mock(ActionListener.class);

        helper.countContainersWithPrefix("   ", null, listener);

        // Verify that the listener was called with 0
        verify(listener).onResponse(0L);
    }

    @Test
    public void testCountContainersWithPrefixWithoutTenant() {
        String indexPrefix = "test-prefix";
        ActionListener<Long> listener = mock(ActionListener.class);

        // Mock SDK search response
        CompletableFuture<org.opensearch.remote.metadata.client.SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class))).thenReturn(future);

        helper.countContainersWithPrefix(indexPrefix, null, listener);

        // Verify the SDK client was called
        verify(sdkClient).searchDataObjectAsync(any(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class));
    }

    @Test
    public void testAddOwnerIdFilterWithNullUser() {
        org.opensearch.index.query.QueryBuilder baseQuery = org.opensearch.index.query.QueryBuilders.matchAllQuery();

        org.opensearch.index.query.QueryBuilder result = helper.addOwnerIdFilter(null, baseQuery);

        // For null user, should return the original query unchanged
        assertEquals(baseQuery, result);
    }

    @Test
    public void testAddOwnerIdFilterWithAdminUser() {
        // Create admin user with all_access role - User.parse format is "username|backend-role1,backend-role2|role1,role2,..."
        User adminUser = new User("admin", List.of("backend-role"), List.of("all_access"), List.of());
        org.opensearch.index.query.QueryBuilder baseQuery = org.opensearch.index.query.QueryBuilders.matchAllQuery();

        org.opensearch.index.query.QueryBuilder result = helper.addOwnerIdFilter(adminUser, baseQuery);

        // For admin user, should return the original query unchanged
        assertEquals(baseQuery, result);
    }

    @Test
    public void testAddOwnerIdFilterWithRegularUser() {
        User regularUser = User.parse("john|backend-role");
        org.opensearch.index.query.QueryBuilder baseQuery = org.opensearch.index.query.QueryBuilders.matchAllQuery();

        org.opensearch.index.query.QueryBuilder result = helper.addOwnerIdFilter(regularUser, baseQuery);

        // For regular user, should wrap in a BoolQuery with owner filter
        assertTrue(result instanceof org.opensearch.index.query.BoolQueryBuilder);
        org.opensearch.index.query.BoolQueryBuilder boolQuery = (org.opensearch.index.query.BoolQueryBuilder) result;
        assertEquals(1, boolQuery.must().size());
        assertEquals(1, boolQuery.filter().size());
    }

}
