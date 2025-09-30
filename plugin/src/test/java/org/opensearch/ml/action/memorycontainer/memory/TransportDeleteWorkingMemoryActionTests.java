/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteWorkingMemoryRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportDeleteWorkingMemoryActionTests extends OpenSearchTestCase {

    private TransportDeleteWorkingMemoryAction action;

    @Mock
    private Client client;

    private Settings settings;

    @Mock
    private ClusterService clusterService;

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
        settings = Settings.builder().build();
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Setup thread context
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new org.opensearch.common.util.concurrent.ThreadContext(settings));

        action = new TransportDeleteWorkingMemoryAction(
            transportService,
            actionFilters,
            client,
            settings,
            clusterService,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testDoExecuteWhenFeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        MLDeleteWorkingMemoryRequest request = new MLDeleteWorkingMemoryRequest("container-id", "memory-id");
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    public void testDoExecuteSuccess() {
        String containerId = "test-container-id";
        String memoryId = "test-memory-id";
        String indexName = "test-working-memory-index";

        MLDeleteWorkingMemoryRequest request = new MLDeleteWorkingMemoryRequest(containerId, memoryId);
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        MLMemoryContainer container = mock(MLMemoryContainer.class);
        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(container.getConfiguration()).thenReturn(config);
        when(config.getWorkingMemoryIndexName()).thenReturn(indexName);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock delete operation
        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(new Index("test", "uuid"), 0), "id", 1L, 1L, 1L, true);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(deleteResponse);
    }

    public void testDoExecuteWhenGetContainerFails() {
        MLDeleteWorkingMemoryRequest request = new MLDeleteWorkingMemoryRequest("container-id", "memory-id");
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        // Mock container retrieval failure
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onFailure(new RuntimeException("Container not found"));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Container not found", exception.getMessage());
    }

    public void testDoExecuteWhenDeleteFails() {
        String containerId = "test-container-id";
        String memoryId = "test-memory-id";
        String indexName = "test-working-memory-index";

        MLDeleteWorkingMemoryRequest request = new MLDeleteWorkingMemoryRequest(containerId, memoryId);
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        // Mock container retrieval
        MLMemoryContainer container = mock(MLMemoryContainer.class);
        MemoryConfiguration config = mock(MemoryConfiguration.class);
        when(container.getConfiguration()).thenReturn(config);
        when(config.getWorkingMemoryIndexName()).thenReturn(indexName);

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> containerListener = invocation.getArgument(1);
            containerListener.onResponse(container);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(), any());

        // Mock delete operation failure
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> deleteListener = invocation.getArgument(1);
            deleteListener.onFailure(new RuntimeException("Delete failed"));
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Delete failed", exception.getMessage());
    }
}
