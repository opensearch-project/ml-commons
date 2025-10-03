/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryConfiguration.VALID_MEMORY_TYPES;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_LONG_TERM;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for TransportDeleteMemoriesByQueryAction
 */
public class TransportDeleteMemoriesByQueryActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Client client;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLDeleteMemoriesByQueryResponse> actionListener;

    private TransportDeleteMemoriesByQueryAction transportAction;
    private MLMemoryContainer mockContainer;
    private User mockUser;
    private ThreadContext threadContext;
    private Settings settings;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        settings = Settings.builder().build();
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
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory").useSystemIndex(false).build())
            .build();

        // Setup mock user
        mockUser = User.parse("test-user|test-backend-role");

        // Initialize transport action
        transportAction = spy(
            new TransportDeleteMemoriesByQueryAction(
                transportService,
                actionFilters,
                client,
                mlFeatureEnabledSetting,
                memoryContainerHelper
            )
        );
    }

    @Test
    public void testDoExecute_Success() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_SESSIONS;
        QueryBuilder query = new MatchAllQueryBuilder();
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(memoryContainerId, memoryType, query);

        // Mock getMemoryContainer
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock checkMemoryContainerAccess
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock DeleteByQueryAction execution
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(client, times(1)).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());
        verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_FeatureDisabled() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", MEM_CONTAINER_MEMORY_TYPE_SESSIONS, null);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Agentic Memory APIs are not enabled"));
    }

    @Test
    public void testDoExecute_ContainerNotFound() {
        // Arrange
        String memoryContainerId = "non-existent";
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            MEM_CONTAINER_MEMORY_TYPE_SESSIONS,
            null
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onFailure(new OpenSearchStatusException("Container not found", RestStatus.NOT_FOUND));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) capturedError).status());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Arrange
        String memoryContainerId = "container-123";
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            MEM_CONTAINER_MEMORY_TYPE_SESSIONS,
            null
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        // Mock access denial
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(false);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("doesn't have permissions"));
    }

    @Test
    public void testDoExecute_NullQueryThrowsException() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "working";
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(memoryContainerId, memoryType, null);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert - should fail with BAD_REQUEST
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Query parameter is required"));
        assertTrue(capturedError.getMessage().contains("match_all"));

        // Verify that DeleteByQueryAction was never called
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());
    }

    @Test
    public void testDoExecute_InvalidMemoryType() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "invalid_type";
        // Provide explicit query
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Invalid memory type"));
    }

    @Test
    public void testDoExecute_SystemIndexHandling() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_LONG_TERM;

        // Configure container with system index
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory").useSystemIndex(true).build())
            .build();

        // Provide explicit query
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.termQuery("field", "value")
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        // For system indices, ThreadContext.stashContext() should be used (verified by successful execution)
        verify(client, times(1)).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());
        verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
    }

    @Test
    public void testDoExecute_DeleteByQueryFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "history";
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            new TermQueryBuilder("field", "value")
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock DeleteByQueryAction failure
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Delete failed"));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof RuntimeException);
        assertTrue(capturedError.getMessage().contains("Delete failed"));
    }

    @Test
    public void testDoExecute_AdminUserNoOwnerFilter() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

        // Create an admin user with all_access role
        User adminUser = new User("admin-user", List.of("admin-backend"), List.of("all_access"), List.of());
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, adminUser.toString());

        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), deleteRequestCaptor.capture(), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        DeleteByQueryRequest capturedRequest = deleteRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getSearchRequest().source().query());
        // For admin users with all_access, the query should not be wrapped in a bool filter
        String queryString = capturedRequest.getSearchRequest().source().query().toString();
        assertTrue("Admin user query should be match_all, not wrapped in bool: " + queryString, queryString.contains("match_all"));
    }

    @Test
    public void testDoExecute_AllMemoryTypes() throws Exception {
        for (String memoryType : VALID_MEMORY_TYPES) {
            setUp();

            // Arrange
            String memoryContainerId = "container-" + memoryType;
            // Provide explicit match_all query instead of null
            MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
                memoryContainerId,
                memoryType,
                QueryBuilders.matchAllQuery()
            );

            doAnswer(invocation -> {
                ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
                listener.onResponse(mockContainer);
                return null;
            }).when(memoryContainerHelper).getMemoryContainer(anyString(), any());

            when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

            BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

            doAnswer(invocation -> {
                ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
                listener.onResponse(bulkResponse);
                return null;
            }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

            // Act
            transportAction.doExecute(task, request, actionListener);

            // Assert
            verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
            verify(actionListener, never()).onFailure(any());
        }
    }

    @Test
    public void testDoExecute_NonAdminUserWithOwnerFilter() {
        // Arrange - Non-admin user should have owner filter applied
        String memoryContainerId = "container-123";
        String memoryType = "working";

        // Create a regular user without all_access role
        User regularUser = new User("regular-user", List.of("backend-role"), List.of("ml_user"), List.of());
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, regularUser.toString());

        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), deleteRequestCaptor.capture(), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        DeleteByQueryRequest capturedRequest = deleteRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        String queryString = capturedRequest.getSearchRequest().source().query().toString();
        // For non-admin users, the query should be wrapped in a bool filter with owner_id term
        assertTrue("Non-admin user query should contain bool filter: " + queryString, queryString.contains("bool"));
        assertTrue("Non-admin user query should filter by owner_id: " + queryString, queryString.contains("owner_id"));
        assertTrue("Non-admin user query should contain user name: " + queryString, queryString.contains("regular-user"));
    }

    @Test
    public void testDoExecute_NullUserNoOwnerFilter() {
        // Arrange - Null user (security disabled) should not have owner filter
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

        // No user in context (security disabled)
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.termQuery("field", "value")
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), deleteRequestCaptor.capture(), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        DeleteByQueryRequest capturedRequest = deleteRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        String queryString = capturedRequest.getSearchRequest().source().query().toString();
        // With null user, query should not be wrapped in bool filter
        assertTrue("Null user query should be term query: " + queryString, queryString.contains("term"));
        assertFalse("Null user query should not have bool filter: " + queryString, queryString.contains("bool"));
    }

    @Test
    public void testDoExecute_DisabledSessionMemory() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

        // Create container with session disabled
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory")
                    .disableSession(true)  // Disable session memory
                    .build()
            )
            .build();

        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Invalid memory type or memory type is disabled"));
    }

    @Test
    public void testDoExecute_DisabledHistoryMemory() {
        // Arrange
        String memoryContainerId = "container-123";
        String memoryType = "history";

        // Create container with history disabled
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory")
                    .disableHistory(true)  // Disable history memory
                    .build()
            )
            .build();

        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) capturedError).status());
        assertTrue(capturedError.getMessage().contains("Invalid memory type or memory type is disabled"));
    }

    @Test
    public void testDoExecute_WithBulkResponseVariations() {
        // Arrange - Test response handler with deleted count
        String memoryContainerId = "container-123";
        String memoryType = "working";
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Create response with deleted documents
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert - Should succeed
        verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_SystemIndexExceptionHandling() {
        // Arrange - Test system index exception handling branch
        String memoryContainerId = "container-123";
        String memoryType = MEM_CONTAINER_MEMORY_TYPE_LONG_TERM;

        // Create container with system index enabled
        mockContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .configuration(
                MemoryConfiguration
                    .builder()
                    .indexPrefix("test-memory")
                    .useSystemIndex(true)  // Enable system index
                    .build()
            )
            .build();

        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(mockContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(mockContainer))).thenReturn(true);

        // Mock DeleteByQueryAction to throw an exception during system index processing
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("System index operation failed"));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(errorCaptor.capture());

        Exception capturedError = errorCaptor.getValue();
        assertTrue(capturedError instanceof RuntimeException);
        assertTrue(capturedError.getMessage().contains("System index operation failed"));
    }
}
