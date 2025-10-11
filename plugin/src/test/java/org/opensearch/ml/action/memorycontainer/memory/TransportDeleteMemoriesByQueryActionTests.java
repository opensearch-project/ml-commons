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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;

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
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
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
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory").useSystemIndex(false).disableSession(false).build())
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
        MemoryType memoryType = MemoryType.SESSIONS;
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

        // Mock deleteDataByQuery in memoryContainerHelper
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        verify(memoryContainerHelper, times(1)).getMemoryContainer(eq(memoryContainerId), any());
        verify(memoryContainerHelper, times(1)).checkMemoryContainerAccess(any(), eq(mockContainer));
        verify(memoryContainerHelper, times(1)).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());
        verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testDoExecute_FeatureDisabled() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", MemoryType.SESSIONS, null);

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
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(memoryContainerId, MemoryType.SESSIONS, null);

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
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(memoryContainerId, MemoryType.SESSIONS, null);

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
        MemoryType memoryType = MemoryType.WORKING;
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

        // Verify that deleteDataByQuery was never called
        verify(memoryContainerHelper, never()).deleteDataByQuery(any(), any(), any());
    }

    @Test
    public void testDoExecute_InvalidMemoryType() {
        // Arrange - Create a container with disabled sessions
        MLMemoryContainer containerWithDisabledSessions = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test container with disabled sessions")
            .configuration(MemoryConfiguration.builder().indexPrefix("test-memory").useSystemIndex(false).disableSession(true).build())
            .build();

        String memoryContainerId = "container-123";
        MemoryType memoryType = MemoryType.SESSIONS;
        // Provide explicit query
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            memoryContainerId,
            memoryType,
            QueryBuilders.matchAllQuery()
        );

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(containerWithDisabledSessions);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq(memoryContainerId), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), eq(containerWithDisabledSessions))).thenReturn(true);

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
        MemoryType memoryType = MemoryType.LONG_TERM;

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
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        // For system indices, the helper method handles ThreadContext.stashContext()
        verify(memoryContainerHelper, times(1)).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());
        verify(actionListener, times(1)).onResponse(any(MLDeleteMemoriesByQueryResponse.class));
    }

    @Test
    public void testDoExecute_DeleteByQueryFailure() {
        // Arrange
        String memoryContainerId = "container-123";
        MemoryType memoryType = MemoryType.HISTORY;
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

        // Mock deleteDataByQuery failure in helper
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Delete failed"));
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

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
        MemoryType memoryType = MemoryType.SESSIONS;

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

        // Mock addContainerIdFilter to add container ID filter
        when(memoryContainerHelper.addContainerIdFilter(anyString(), any(QueryBuilder.class))).thenAnswer(invocation -> {
            String containerId = invocation.getArgument(0);
            QueryBuilder query = invocation.getArgument(1);
            BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
            filteredQuery.must(query);
            filteredQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));
            return filteredQuery;
        });

        // Mock addOwnerIdFilter to return the original query for admin users
        when(memoryContainerHelper.addOwnerIdFilter(any(User.class), any(QueryBuilder.class))).thenAnswer(invocation -> {
            // For admin users with all_access, return the original query
            return invocation.getArgument(1);
        });

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), deleteRequestCaptor.capture(), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        DeleteByQueryRequest capturedRequest = deleteRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getSearchRequest().source().query());
        // Query should have container ID filter applied
        String queryString = capturedRequest.getSearchRequest().source().query().toString();
        assertTrue("Query should contain memory_container_id filter: " + queryString, queryString.contains("memory_container_id"));
        assertTrue("Query should contain bool filter: " + queryString, queryString.contains("bool"));
    }

    @Test
    public void testDoExecute_AllMemoryTypes() throws Exception {
        for (MemoryType memoryType : MemoryType.values()) {
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
            }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

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
        MemoryType memoryType = MemoryType.WORKING;

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

        // Mock addContainerIdFilter to add container ID filter
        when(memoryContainerHelper.addContainerIdFilter(anyString(), any(QueryBuilder.class))).thenAnswer(invocation -> {
            String containerId = invocation.getArgument(0);
            QueryBuilder query = invocation.getArgument(1);
            BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
            filteredQuery.must(query);
            filteredQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));
            return filteredQuery;
        });

        // Mock addOwnerIdFilter to simulate adding owner filter for non-admin users
        when(memoryContainerHelper.addOwnerIdFilter(any(User.class), any(QueryBuilder.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            QueryBuilder query = invocation.getArgument(1);
            // Simulate adding owner filter for non-admin users
            BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
            filteredQuery.must(query);
            filteredQuery.filter(QueryBuilders.termQuery(OWNER_ID_FIELD, user.getName()));
            return filteredQuery;
        });

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), deleteRequestCaptor.capture(), any());

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
        MemoryType memoryType = MemoryType.SESSIONS;

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

        // Mock addContainerIdFilter to add container ID filter
        when(memoryContainerHelper.addContainerIdFilter(anyString(), any(QueryBuilder.class))).thenAnswer(invocation -> {
            String containerId = invocation.getArgument(0);
            QueryBuilder query = invocation.getArgument(1);
            BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
            filteredQuery.must(query);
            filteredQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));
            return filteredQuery;
        });

        // Mock addOwnerIdFilter to return original query for null user (security disabled)
        when(memoryContainerHelper.addOwnerIdFilter(any(), any(QueryBuilder.class))).thenAnswer(invocation -> {
            // For null user (security disabled), return the original query
            return invocation.getArgument(1);
        });

        ArgumentCaptor<DeleteByQueryRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
        BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(bulkResponse);
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), deleteRequestCaptor.capture(), any());

        // Act
        transportAction.doExecute(task, request, actionListener);

        // Assert
        DeleteByQueryRequest capturedRequest = deleteRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        String queryString = capturedRequest.getSearchRequest().source().query().toString();
        // With container ID filter, query should now be wrapped in bool filter
        assertTrue("Query should contain bool filter for container ID: " + queryString, queryString.contains("bool"));
        assertTrue("Query should contain memory_container_id: " + queryString, queryString.contains("memory_container_id"));
    }

    @Test
    public void testDoExecute_DisabledSessionMemory() {
        // Arrange
        String memoryContainerId = "container-123";
        MemoryType memoryType = MemoryType.SESSIONS;

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
        MemoryType memoryType = MemoryType.HISTORY;

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
        MemoryType memoryType = MemoryType.WORKING;
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
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

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
        MemoryType memoryType = MemoryType.LONG_TERM;

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

        // Mock deleteDataByQuery to throw an exception during system index processing
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("System index operation failed"));
            return null;
        }).when(memoryContainerHelper).deleteDataByQuery(any(MemoryConfiguration.class), any(DeleteByQueryRequest.class), any());

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
