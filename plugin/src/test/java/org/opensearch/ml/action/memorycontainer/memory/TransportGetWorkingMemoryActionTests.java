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

import java.io.IOException;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportGetWorkingMemoryActionTests extends OpenSearchTestCase {

    private TransportGetWorkingMemoryAction action;

    @Mock
    private Client client;

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
        when(threadPool.getThreadContext())
            .thenReturn(new org.opensearch.common.util.concurrent.ThreadContext(org.opensearch.common.settings.Settings.builder().build()));

        action = new TransportGetWorkingMemoryAction(
            transportService,
            actionFilters,
            client,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testDoExecuteWhenFeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        MLGetWorkingMemoryRequest request = new MLGetWorkingMemoryRequest("container-id", "memory-id");
        ActionListener<MLGetWorkingMemoryResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    public void testDoExecuteSuccess() throws IOException {
        String containerId = "test-container-id";
        String memoryId = "test-memory-id";
        String indexName = "test-working-memory-index";

        MLGetWorkingMemoryRequest request = new MLGetWorkingMemoryRequest(containerId, memoryId);
        ActionListener<MLGetWorkingMemoryResponse> listener = mock(ActionListener.class);

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

        // Mock get operation with valid source
        BytesReference source = BytesReference
            .bytes(
                XContentFactory
                    .jsonBuilder()
                    .startObject()
                    .field("memory_container_id", containerId)
                    .field("memory_type", "CONVERSATIONAL")
                    .field("messages", java.util.Collections.emptyList())
                    .endObject()
            );

        GetResult getResult = new GetResult(indexName, memoryId, 1L, 1L, 1L, true, source, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        doAnswer(invocation -> {
            ActionListener<GetResponse> getListener = invocation.getArgument(2);
            getListener.onResponse(getResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(), any());

        action.doExecute(task, request, listener);

        verify(listener).onResponse(any(MLGetWorkingMemoryResponse.class));
    }

    public void testDoExecuteWhenGetContainerFails() {
        MLGetWorkingMemoryRequest request = new MLGetWorkingMemoryRequest("container-id", "memory-id");
        ActionListener<MLGetWorkingMemoryResponse> listener = mock(ActionListener.class);

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

    public void testDoExecuteWhenGetFails() {
        String containerId = "test-container-id";
        String memoryId = "test-memory-id";
        String indexName = "test-working-memory-index";

        MLGetWorkingMemoryRequest request = new MLGetWorkingMemoryRequest(containerId, memoryId);
        ActionListener<MLGetWorkingMemoryResponse> listener = mock(ActionListener.class);

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

        // Mock get operation failure
        doAnswer(invocation -> {
            ActionListener<GetResponse> getListener = invocation.getArgument(2);
            getListener.onFailure(new RuntimeException("Get failed"));
            return null;
        }).when(memoryContainerHelper).getData(any(), any(), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Get failed", exception.getMessage());
    }

    public void testDoExecuteWhenMemoryNotFound() {
        String containerId = "test-container-id";
        String memoryId = "test-memory-id";
        String indexName = "test-working-memory-index";

        MLGetWorkingMemoryRequest request = new MLGetWorkingMemoryRequest(containerId, memoryId);
        ActionListener<MLGetWorkingMemoryResponse> listener = mock(ActionListener.class);

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

        // Mock get operation returning not found
        GetResult getResult = new GetResult(indexName, memoryId, -2L, 0L, 1L, false, null, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        doAnswer(invocation -> {
            ActionListener<GetResponse> getListener = invocation.getArgument(2);
            getListener.onResponse(getResponse);
            return null;
        }).when(memoryContainerHelper).getData(any(), any(), any());

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());

        Exception exception = captor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exception).status());
    }
}
