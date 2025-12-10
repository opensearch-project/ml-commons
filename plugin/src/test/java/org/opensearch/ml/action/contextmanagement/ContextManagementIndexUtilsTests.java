/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class ContextManagementIndexUtilsTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private AdminClient adminClient;

    @Mock
    private IndicesAdminClient indicesAdminClient;

    private ContextManagementIndexUtils contextManagementIndexUtils;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Create a real ThreadContext instead of mocking it
        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        contextManagementIndexUtils = new ContextManagementIndexUtils(client, clusterService);
    }

    @Test
    public void testGetIndexName() {
        String indexName = ContextManagementIndexUtils.getIndexName();
        assertEquals("ml_context_management_templates", indexName);
    }

    @Test
    public void testDoesIndexExist_True() {
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(true);

        boolean exists = contextManagementIndexUtils.doesIndexExist();
        assertTrue(exists);
    }

    @Test
    public void testDoesIndexExist_False() {
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        boolean exists = contextManagementIndexUtils.doesIndexExist();
        assertFalse(exists);
    }

    @Test
    public void testCreateIndexIfNotExists_IndexAlreadyExists() {
        // Mock index already exists
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(true);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementIndexUtils.createIndexIfNotExists(listener);

        verify(listener).onResponse(true);
        verify(indicesAdminClient, never()).create(any(), any());
    }

    @Test
    public void testCreateIndexIfNotExists_Success() {
        // Mock index doesn't exist
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Mock successful index creation
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<CreateIndexResponse> createListener = invocation.getArgument(1);
            CreateIndexResponse response = mock(CreateIndexResponse.class);
            createListener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any());

        contextManagementIndexUtils.createIndexIfNotExists(listener);

        verify(listener).onResponse(true);

        // Verify the create request was made with correct settings
        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), any());

        CreateIndexRequest request = requestCaptor.getValue();
        assertEquals("ml_context_management_templates", request.index());

        Settings indexSettings = request.settings();
        assertEquals("1", indexSettings.get("index.number_of_shards"));
        assertEquals("1", indexSettings.get("index.number_of_replicas"));
        assertEquals("0-1", indexSettings.get("index.auto_expand_replicas"));
    }

    @Test
    public void testCreateIndexIfNotExists_ResourceAlreadyExistsException() {
        // Mock index doesn't exist initially
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Mock ResourceAlreadyExistsException (race condition)
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<CreateIndexResponse> createListener = invocation.getArgument(1);
            createListener.onFailure(new ResourceAlreadyExistsException("Index already exists"));
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any());

        contextManagementIndexUtils.createIndexIfNotExists(listener);

        verify(listener).onResponse(true);
    }

    @Test
    public void testCreateIndexIfNotExists_OtherException() {
        // Mock index doesn't exist
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        RuntimeException testException = new RuntimeException("Test exception");

        // Mock other exception
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<CreateIndexResponse> createListener = invocation.getArgument(1);
            createListener.onFailure(testException);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any());

        contextManagementIndexUtils.createIndexIfNotExists(listener);

        verify(listener).onFailure(testException);
    }

    @Test
    public void testCreateIndexIfNotExists_UnexpectedException() {
        // Mock index doesn't exist
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        RuntimeException testException = new RuntimeException("Unexpected error");

        // Mock unexpected exception during setup
        when(client.admin()).thenThrow(testException);

        contextManagementIndexUtils.createIndexIfNotExists(listener);

        verify(listener).onFailure(testException);
    }
}
