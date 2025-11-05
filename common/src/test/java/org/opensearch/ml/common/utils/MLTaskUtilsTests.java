/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

@RunWith(MockitoJUnitRunner.class)
public class MLTaskUtilsTests {
    private Client client;
    private ThreadPool threadPool;
    private ThreadContext threadContext;

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

//    @Test
//    public void testUpdateMLTaskDirectly_NullFields() {
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", null, client, listener);
//        verify(listener).onFailure(any(IllegalArgumentException.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_EmptyFields() {
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", new HashMap<>(), client, listener);
//        verify(listener).onFailure(any(IllegalArgumentException.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_NullTaskId() {
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly(null, new HashMap<>(), client, listener);
//        verify(listener).onFailure(any(IllegalArgumentException.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_EmptyTaskId() {
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("", new HashMap<>(), client, listener);
//        verify(listener).onFailure(any(IllegalArgumentException.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_Success() {
//        Map<String, Object> updatedFields = new HashMap<>();
//        updatedFields.put("field1", "value1");
//
//        doAnswer(invocation -> {
//            ActionListener<UpdateResponse> actionListener = invocation.getArgument(1);
//            ShardId shardId = new ShardId(new Index(ML_TASK_INDEX, "_na_"), 0);
//            UpdateResponse response = new UpdateResponse(shardId, "task_id", 1, 1, 1, DocWriteResponse.Result.CREATED);
//            actionListener.onResponse(response);
//            return null;
//        }).when(client).update(any(UpdateRequest.class), any());
//
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", updatedFields, client, listener);
//        verify(listener).onResponse(any(UpdateResponse.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_InvalidStateType() {
//        Map<String, Object> updatedFields = new HashMap<>();
//        updatedFields.put("state", "INVALID_STATE");
//
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", updatedFields, client, listener);
//        verify(listener).onFailure(any(IllegalArgumentException.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_TaskDoneState() {
//        Map<String, Object> updatedFields = new HashMap<>();
//        updatedFields.put("state", MLTaskState.COMPLETED);
//
//        doAnswer(invocation -> {
//            ActionListener<UpdateResponse> actionListener = invocation.getArgument(1);
//            UpdateRequest request = invocation.getArgument(0);
//            // Verify retry policy is set for task done state
//            assert request.retryOnConflict() == 3;
//
//            ShardId shardId = new ShardId(new Index(ML_TASK_INDEX, "_na_"), 0);
//            UpdateResponse response = new UpdateResponse(shardId, "task_id", 1, 1, 1, DocWriteResponse.Result.CREATED);
//            actionListener.onResponse(response);
//            return null;
//        }).when(client).update(any(UpdateRequest.class), any());
//
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", updatedFields, client, listener);
//        verify(listener).onResponse(any(UpdateResponse.class));
//    }
//
//    @Test
//    public void testUpdateMLTaskDirectly_ClientException() {
//        Map<String, Object> updatedFields = new HashMap<>();
//        updatedFields.put("field1", "value1");
//
//        doAnswer(invocation -> {
//            ActionListener<UpdateResponse> actionListener = invocation.getArgument(1);
//            actionListener.onFailure(new RuntimeException("Test exception"));
//            return null;
//        }).when(client).update(any(UpdateRequest.class), any());
//
//        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
//        MLTaskUtils.updateMLTaskDirectly("task_id", updatedFields, client, listener);
//        verify(listener).onFailure(any(RuntimeException.class));
//    }
}
