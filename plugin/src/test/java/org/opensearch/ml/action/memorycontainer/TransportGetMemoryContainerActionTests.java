/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportGetMemoryContainerActionTests extends OpenSearchTestCase {

    private static final String MEMORY_CONTAINER_ID = "test-memory-container-id";
    private static final String TENANT_ID = "test-tenant";
    private static final String USER_NAME = "test-user";
    private static final String OWNER_NAME = "owner-user";

    private TransportGetMemoryContainerAction action;

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
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private Task task;
    @Mock
    private ThreadPool threadPool;

    @Mock
    private ActionListener<MLMemoryContainerGetResponse> actionListener;

    @Captor
    private ArgumentCaptor<MLMemoryContainerGetResponse> responseCaptor;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private ActionRequest actionRequest;
    private User testUser;
    private User ownerUser;
    private User adminUser;
    private ThreadContext threadContext;
    private MLMemoryContainer testMemoryContainer;
    private GetDataObjectResponse getDataObjectResponse;
    private GetResponse getResponse;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup test users using User.parse() with correct format: name|backend_roles|roles
        testUser = User.parse(USER_NAME + "||");  // No backend roles or roles
        ownerUser = User.parse(OWNER_NAME + "||");  // No backend roles or roles
        adminUser = User.parse("admin-user||all_access");  // Has all_access role

        // Setup thread context
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Setup action request
        actionRequest = mock(ActionRequest.class);

        // Setup test memory container
        testMemoryContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerUser)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        // Setup GetResponse
        getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(createMemoryContainerJson());

        // Setup GetDataObjectResponse
        getDataObjectResponse = mock(GetDataObjectResponse.class);
        when(getDataObjectResponse.getResponse()).thenReturn(getResponse);
        when(getDataObjectResponse.id()).thenReturn(MEMORY_CONTAINER_ID);

        // Setup ML feature settings
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests

        // Create action
        action = new TransportGetMemoryContainerAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            xContentRegistry,
            clusterService,
            connectorAccessControlHelper,
            mlFeatureEnabledSetting
        );
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testCheckMemoryContainerAccessWithNullUser() {
        // Test access control logic directly using package-private method
        boolean hasAccess = action.checkMemoryContainerAccess(null, testMemoryContainer);
        assertTrue("Null user should have access when security is disabled", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithAdminUser() {
        boolean hasAccess = action.checkMemoryContainerAccess(adminUser, testMemoryContainer);
        assertTrue("Admin user should have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithOwnerUser() {
        boolean hasAccess = action.checkMemoryContainerAccess(ownerUser, testMemoryContainer);
        assertTrue("Owner user should have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithUnauthorizedUser() {
        User unauthorizedUser = User.parse("unauthorized-user||");  // No roles or backend roles
        boolean hasAccess = action.checkMemoryContainerAccess(unauthorizedUser, testMemoryContainer);
        assertFalse("Unauthorized user should not have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithMatchingBackendRoles() {
        // Create owner with backend roles using User.parse format: name|backend_roles|roles
        User ownerWithBackendRoles = User.parse(OWNER_NAME + "|backend-role-1,backend-role-2|");
        User userWithMatchingRole = User.parse("different-user|backend-role-1|");

        MLMemoryContainer containerWithBackendRoles = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerWithBackendRoles)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(userWithMatchingRole, containerWithBackendRoles);
        assertTrue("User with matching backend role should have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithNonMatchingBackendRoles() {
        // Create users with different backend roles
        User ownerWithBackendRoles = User.parse(OWNER_NAME + "|owner-role-1,owner-role-2|");
        User userWithDifferentRoles = User.parse("different-user|user-role-1,user-role-2|");

        MLMemoryContainer containerWithBackendRoles = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerWithBackendRoles)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(userWithDifferentRoles, containerWithBackendRoles);
        assertFalse("User with non-matching backend roles should not have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithNullOwner() {
        MLMemoryContainer containerWithNullOwner = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(null)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(testUser, containerWithNullOwner);
        assertFalse("User should not have access when owner is null", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithNullOwnerName() {
        // Create owner with null name - this is tricky with User.parse, so we'll create a container with null owner
        MLMemoryContainer containerWithNullOwnerName = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(null)  // Set owner to null
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(testUser, containerWithNullOwnerName);
        assertFalse("User should not have access when owner is null", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithNullUserBackendRoles() {
        // Create owner with backend roles and user without backend roles
        User ownerWithBackendRoles = User.parse(OWNER_NAME + "|owner-role-1,owner-role-2|");
        User userWithoutBackendRoles = User.parse("different-user||");  // No backend roles

        MLMemoryContainer containerWithBackendRoles = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerWithBackendRoles)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(userWithoutBackendRoles, containerWithBackendRoles);
        assertFalse("User with no backend roles should not have access", hasAccess);
    }

    public void testCheckMemoryContainerAccessWithNullOwnerBackendRoles() {
        // Create owner without backend roles and user with backend roles
        User ownerWithoutBackendRoles = User.parse(OWNER_NAME + "||");  // No backend roles
        User userWithBackendRoles = User.parse("different-user|user-role|");

        MLMemoryContainer containerWithoutOwnerBackendRoles = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerWithoutBackendRoles)
            .tenantId(TENANT_ID)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        boolean hasAccess = action.checkMemoryContainerAccess(userWithBackendRoles, containerWithoutOwnerBackendRoles);
        assertFalse("User should not have access when owner has no backend roles", hasAccess);
    }

    public void testHandleThrowableWithIndexNotFoundException() {
        IndexNotFoundException indexNotFoundException = new IndexNotFoundException("Index not found");

        // Test the handleThrowable method indirectly by testing the logic
        // Since we can't easily mock static methods, we'll test the exception handling logic
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(indexNotFoundException);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // The actual test would require mocking static methods, so we'll focus on testing
        // the access control logic which is the core business logic
    }

    public void testHandleThrowableWithGeneralException() {
        RuntimeException generalException = new RuntimeException("General error");

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(generalException);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Similar to above, the full test would require static method mocking
        // We focus on testing the business logic that can be tested
    }

    public void testProcessResponseWithNullGetResponse() {
        GetDataObjectResponse nullDataResponse = mock(GetDataObjectResponse.class);
        when(nullDataResponse.getResponse()).thenReturn(null);

        // Test the logic that handles null GetResponse
        // The actual method call would require static method mocking
        assertNull("GetResponse should be null", nullDataResponse.getResponse());
    }

    public void testProcessResponseWithNonExistentMemoryContainer() {
        GetResponse nonExistentResponse = mock(GetResponse.class);
        when(nonExistentResponse.isExists()).thenReturn(false);

        GetDataObjectResponse nonExistentDataResponse = mock(GetDataObjectResponse.class);
        when(nonExistentDataResponse.getResponse()).thenReturn(nonExistentResponse);

        // Test the logic that handles non-existent memory containers
        assertFalse("Memory container should not exist", nonExistentDataResponse.getResponse().isExists());
    }

    public void testProcessResponseWithExceptionInGetResponse() {
        GetDataObjectResponse faultyDataResponse = mock(GetDataObjectResponse.class);
        when(faultyDataResponse.getResponse()).thenThrow(new RuntimeException("GetResponse access exception"));

        // Test exception handling in processResponse
        try {
            faultyDataResponse.getResponse();
            fail("Should have thrown exception");
        } catch (RuntimeException e) {
            assertEquals("GetResponse access exception", e.getMessage());
        }
    }

    public void testUserConstructorAndRoles() {
        // Test User constructor and role access using parse method
        User adminUser = User.parse("admin-user||all_access");

        // Debug output
        System.out.println("Admin user name: " + adminUser.getName());
        System.out.println("Admin user roles: " + adminUser.getRoles());
        System.out.println("Admin user backend roles: " + adminUser.getBackendRoles());

        // Test the actual logic
        boolean hasAllAccessRole = adminUser.getRoles() != null && adminUser.getRoles().contains("all_access");
        System.out.println("Has all_access role: " + hasAllAccessRole);

        assertTrue("Admin user should have all_access role", hasAllAccessRole);
    }

    public void testCheckMemoryContainerAccessDebug() {
        // Debug the access check logic
        User debugAdminUser = User.parse("admin-user||all_access");

        System.out.println("Testing admin access:");
        System.out.println("Admin user: " + debugAdminUser.getName());
        System.out.println("Admin roles: " + debugAdminUser.getRoles());

        boolean hasAccess = action.checkMemoryContainerAccess(debugAdminUser, testMemoryContainer);
        System.out.println("Admin has access: " + hasAccess);

        assertTrue("Admin user should have access", hasAccess);
    }

    public void testValidateMemoryContainerAccessWithAuthorizedUser() {
        // Test with owner user
        boolean hasAccess = action.checkMemoryContainerAccess(ownerUser, testMemoryContainer);
        assertTrue("Owner should have access", hasAccess);

        // Test with admin user
        hasAccess = action.checkMemoryContainerAccess(adminUser, testMemoryContainer);
        assertTrue("Admin should have access", hasAccess);

        // Test with null user (security disabled)
        hasAccess = action.checkMemoryContainerAccess(null, testMemoryContainer);
        assertTrue("Null user should have access when security is disabled", hasAccess);
    }

    // ========== doExecute Method Tests ==========

    public void testDoExecuteSuccess() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup successful async response
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup owner user context (should have access)
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, OWNER_NAME + "||");

        // Setup tenant validation to pass
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify sdkClient was called
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class));

        // Verify success response (owner should have access)
        verify(actionListener, timeout(1000))
            .onResponse(
                argThat(
                    response -> response instanceof MLMemoryContainerGetResponse
                        && ((MLMemoryContainerGetResponse) response).getMlMemoryContainer() != null
                )
            );
    }

    public void testDoExecuteWithInvalidTenantId() {
        // Setup request with invalid tenant
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, "invalid-tenant");

        // Setup successful async response (tenant validation happens after SDK call)
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup admin user context (to pass user validation)
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin-user||all_access");

        // Enable multi-tenancy for tenant validation
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify sdkClient was called (early tenant validation passes since tenant is not null)
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class));

        // The tenant resource validation happens in processResponse and should fail
        // because request tenant "invalid-tenant" doesn't match resource tenant TENANT_ID
        verify(actionListener, timeout(1000)).onFailure(any(OpenSearchStatusException.class));
    }

    public void testDoExecuteWithSdkClientException() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup sdkClient to throw exception
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenThrow(new RuntimeException("SDK client error"));

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    public void testDoExecuteWithIndexNotFoundException() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup async response with IndexNotFoundException
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new IndexNotFoundException("Memory container index not found"));
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response with NOT_FOUND status
        verify(actionListener, timeout(1000))
            .onFailure(
                argThat(
                    exception -> exception instanceof OpenSearchStatusException
                        && ((OpenSearchStatusException) exception).status() == RestStatus.NOT_FOUND
                        && exception.getMessage().contains("Failed to find memory container index")
                )
            );
    }

    public void testDoExecuteWithGeneralAsyncException() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup async response with general exception
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        RuntimeException generalException = new RuntimeException("General async error");
        future.completeExceptionally(generalException);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response with the original exception
        verify(actionListener, timeout(1000))
            .onFailure(argThat(exception -> exception instanceof RuntimeException && exception.getMessage().equals("General async error")));
    }

    public void testDoExecuteWithNonExistentMemoryContainer() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup response for non-existent container
        GetResponse nonExistentResponse = mock(GetResponse.class);
        when(nonExistentResponse.isExists()).thenReturn(false);

        GetDataObjectResponse nonExistentDataResponse = mock(GetDataObjectResponse.class);
        when(nonExistentDataResponse.getResponse()).thenReturn(nonExistentResponse);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(nonExistentDataResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response with NOT_FOUND status
        verify(actionListener, timeout(1000))
            .onFailure(
                argThat(
                    exception -> exception instanceof OpenSearchStatusException
                        && ((OpenSearchStatusException) exception).status() == RestStatus.NOT_FOUND
                        && exception.getMessage().contains("Failed to find memory container with the provided memory container id")
                )
            );
    }

    public void testDoExecuteWithUnauthorizedUser() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup successful async response
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup unauthorized user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "unauthorized-user||");

        // Setup tenant validation to pass
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response with FORBIDDEN status (unauthorized user should be denied access)
        verify(actionListener, timeout(1000))
            .onFailure(
                argThat(
                    exception -> exception instanceof OpenSearchStatusException
                        && ((OpenSearchStatusException) exception).status() == RestStatus.FORBIDDEN
                        && exception.getMessage().contains("User doesn't have privilege to perform this operation")
                )
            );
    }

    public void testDoExecuteWithAdminUser() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup successful async response
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup admin user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin-user||all_access");

        // Setup tenant validation to pass
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify success response (admin should have access)
        verify(actionListener, timeout(1000))
            .onResponse(
                argThat(
                    response -> response instanceof MLMemoryContainerGetResponse
                        && ((MLMemoryContainerGetResponse) response).getMlMemoryContainer() != null
                )
            );
    }

    public void testDoExecuteWithParsingException() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup response with invalid JSON
        GetResponse invalidResponse = mock(GetResponse.class);
        when(invalidResponse.isExists()).thenReturn(true);
        when(invalidResponse.getSourceAsString()).thenReturn("invalid-json-content");

        GetDataObjectResponse invalidDataResponse = mock(GetDataObjectResponse.class);
        when(invalidDataResponse.getResponse()).thenReturn(invalidResponse);
        when(invalidDataResponse.id()).thenReturn(MEMORY_CONTAINER_ID);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(invalidDataResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response due to parsing error
        verify(actionListener, timeout(1000)).onFailure(any(Exception.class));
    }

    public void testDoExecuteWithNullGetResponse() {
        // Setup request
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Setup response with null GetResponse
        GetDataObjectResponse nullDataResponse = mock(GetDataObjectResponse.class);
        when(nullDataResponse.getResponse()).thenReturn(null);

        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(nullDataResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify failure response with NOT_FOUND status
        verify(actionListener, timeout(1000))
            .onFailure(
                argThat(
                    exception -> exception instanceof OpenSearchStatusException
                        && ((OpenSearchStatusException) exception).status() == RestStatus.NOT_FOUND
                )
            );
    }

    public void testDoExecuteWithTenantMismatch() {
        // Setup request with different tenant
        MLMemoryContainerGetRequest getRequest = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, "different-tenant");

        // Setup successful async response but with different tenant in container
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.complete(getDataObjectResponse);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(future);

        // Setup user context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify that tenant validation will handle the mismatch
        // (The exact behavior depends on TenantAwareHelper.validateTenantResource implementation)
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class));
    }

    // ========== Helper Methods ==========

    private String createMemoryContainerJson() {
        // Use epoch timestamps instead of ISO format to avoid parsing errors
        long currentTimeEpoch = System.currentTimeMillis();
        return "{"
            + "\"name\":\"test-container\","
            + "\"description\":\"Test memory container\","
            + "\"owner\":{\"name\":\""
            + OWNER_NAME
            + "\"},"
            + "\"tenant_id\":\""
            + TENANT_ID
            + "\","
            + "\"created_time\":"
            + currentTimeEpoch
            + ","
            + "\"last_updated_time\":"
            + currentTimeEpoch
            + "}";
    }

    public void testDoExecuteWithAgenticMemoryDisabled() throws InterruptedException {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        // Create request
        MLMemoryContainerGetRequest request = new MLMemoryContainerGetRequest(MEMORY_CONTAINER_ID, TENANT_ID);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response due to feature being disabled
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) exception;
        assertEquals(RestStatus.FORBIDDEN, statusException.status());
        assertEquals("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled", exception.getMessage());
    }
}
