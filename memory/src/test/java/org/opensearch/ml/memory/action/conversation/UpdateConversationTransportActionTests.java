/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_NAME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_UPDATED_TIME_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class UpdateConversationTransportActionTests extends OpenSearchTestCase {
    private UpdateConversationTransportAction transportUpdateConversationAction;

    @Mock
    private Client client;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Task task;

    @Mock
    private UpdateConversationRequest updateRequest;

    @Mock
    private UpdateResponse updateResponse;

    @Mock
    ActionListener<UpdateResponse> actionListener;

    ThreadContext threadContext;

    private Settings settings;

    private ShardId shardId;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        String conversationId = "test_conversation_id";
        Map<String, Object> updateContent = Map.of(META_NAME_FIELD, "new name", META_UPDATED_TIME_FIELD, Instant.ofEpochMilli(123));
        when(updateRequest.getConversationId()).thenReturn(conversationId);
        when(updateRequest.getUpdateContent()).thenReturn(updateContent);
        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);

        transportUpdateConversationAction = new UpdateConversationTransportAction(transportService, actionFilters, client);
    }

    public void test_execute_Success() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateConversationAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    public void test_execute_UpdateFailure() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Error in Update Request"));
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateConversationAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Error in Update Request", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_UpdateWrongStatus() {
        UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateConversationAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    public void test_execute_ThrowException() {
        doThrow(new RuntimeException("Error in Update Request")).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        transportUpdateConversationAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Error in Update Request", argumentCaptor.getValue().getMessage());
    }
}
