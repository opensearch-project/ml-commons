/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportSearchMemoryContainerActionTests extends OpenSearchTestCase {

    private TransportSearchMemoryContainerAction action;

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

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
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.builder().build()));

        action = new TransportSearchMemoryContainerAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    @Test
    public void testConstructor() {
        assertNotNull(action);
    }

    @Test
    public void testDoExecuteWhenFeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MEMORY_CONTAINER_INDEX });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(null).build();
        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testDoExecuteSuccessWithAdminUser() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Mock admin user
        org.opensearch.commons.authuser.User adminUser = new org.opensearch.commons.authuser.User(
            "admin",
            java.util.Arrays.asList("admin", "all_access"),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyMap()
        );
        when(memoryContainerHelper.isAdminUser(any())).thenReturn(true);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MEMORY_CONTAINER_INDEX });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(null).build();

        // Mock search response - just return a completing future, the SdkClientUtils.wrapSearchCompletion handles the response
        doAnswer(invocation -> {
            // Return a CompletableFuture that completes immediately
            return CompletableFuture.completedFuture(null);
        }).when(sdkClient).searchDataObjectAsync(any());

        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        // Act
        action.doExecute(task, request, listener);

        // Assert - admin user should NOT have backend roles filter applied
        verify(memoryContainerHelper, never()).addUserBackendRolesFilter(any(), any());
        verify(sdkClient).searchDataObjectAsync(any());
    }

    @Test
    public void testDoExecuteWithTenantId() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(memoryContainerHelper.isAdminUser(any())).thenReturn(true);

        String tenantId = "tenant-123";
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MEMORY_CONTAINER_INDEX });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(tenantId).build();

        // Mock search response - just return a completing future, the SdkClientUtils.wrapSearchCompletion handles the response
        doAnswer(invocation -> {
            // Return a CompletableFuture that completes immediately
            return CompletableFuture.completedFuture(null);
        }).when(sdkClient).searchDataObjectAsync(any());

        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        // Act
        action.doExecute(task, request, listener);

        // Assert - verify tenant ID is passed correctly
        org.mockito.ArgumentCaptor<org.opensearch.remote.metadata.client.SearchDataObjectRequest> requestCaptor = org.mockito.ArgumentCaptor
            .forClass(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class);
        verify(sdkClient).searchDataObjectAsync(requestCaptor.capture());
        assertEquals(tenantId, requestCaptor.getValue().tenantId());
    }

    @Test
    public void testDoExecuteWithMultiTenancyEnabledAndNullTenantId() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MEMORY_CONTAINER_INDEX });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(null).build();

        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        // Act
        action.doExecute(task, request, listener);

        // Assert
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("You don't have permission to access this resource", exception.getMessage());
    }

    @Test
    public void testDoExecuteSearchFailure() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(memoryContainerHelper.isAdminUser(any())).thenReturn(true);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { ML_MEMORY_CONTAINER_INDEX });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(null).build();

        // Mock search failure
        java.util.concurrent.CompletableFuture<org.opensearch.remote.metadata.client.SearchDataObjectResponse> future = java.util.concurrent.CompletableFuture
            .failedFuture(new RuntimeException("Search failed"));
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(future);

        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        // Act
        action.doExecute(task, request, listener);

        // Assert - just verify the search was attempted
        verify(sdkClient).searchDataObjectAsync(any());
    }

    @Test
    public void testDoExecuteWithMultipleIndices() {
        // Arrange
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(memoryContainerHelper.isAdminUser(any())).thenReturn(true);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchRequest searchRequest = new SearchRequest(new String[] { "index1", "index2", "index3" });
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId(null).build();

        // Mock search response - just return a completing future, the SdkClientUtils.wrapSearchCompletion handles the response
        doAnswer(invocation -> {
            // Return a CompletableFuture that completes immediately
            return CompletableFuture.completedFuture(null);
        }).when(sdkClient).searchDataObjectAsync(any());

        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        // Act
        action.doExecute(task, request, listener);

        // Assert - verify multiple indices are passed correctly
        org.mockito.ArgumentCaptor<org.opensearch.remote.metadata.client.SearchDataObjectRequest> requestCaptor = org.mockito.ArgumentCaptor
            .forClass(org.opensearch.remote.metadata.client.SearchDataObjectRequest.class);
        verify(sdkClient).searchDataObjectAsync(requestCaptor.capture());
        assertArrayEquals(new String[] { "index1", "index2", "index3" }, requestCaptor.getValue().indices());
    }

}
