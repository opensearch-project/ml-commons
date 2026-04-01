/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

@RunWith(MockitoJUnitRunner.class)
public class MLTaskUtilsTests {
    private Client client;
    private SdkClient sdkClient;
    private ThreadPool threadPool;
    private ThreadContext threadContext;

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.sdkClient = mock(SdkClient.class);
        this.threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testUpdateMLTaskDirectly_NullFields() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", null, null, client, sdkClient, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_EmptyFields() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", null, new HashMap<>(), client, sdkClient, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_NullTaskId() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly(null, null, new HashMap<>(), client, sdkClient, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_EmptyTaskId() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("", null, new HashMap<>(), client, sdkClient, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_Success() {
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("field1", "value1");

        ShardId shardId = new ShardId(new Index(ML_TASK_INDEX, "_na_"), 0);
        UpdateResponse updateResponse = new UpdateResponse(shardId, "task_id", 1, 1, 1, DocWriteResponse.Result.UPDATED);
        UpdateDataObjectResponse sdkResponse = mock(UpdateDataObjectResponse.class);
        when(sdkResponse.updateResponse()).thenReturn(updateResponse);

        CompletableFuture<UpdateDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);
        when(sdkClient.updateDataObjectAsync(any(UpdateDataObjectRequest.class))).thenReturn(future);

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", "tenant1", updatedFields, client, sdkClient, listener);
        verify(listener).onResponse(any(UpdateResponse.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_InvalidStateType() {
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("state", "INVALID_STATE");

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", null, updatedFields, client, sdkClient, listener);
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_TaskDoneState() {
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("state", MLTaskState.COMPLETED);

        ShardId shardId = new ShardId(new Index(ML_TASK_INDEX, "_na_"), 0);
        UpdateResponse updateResponse = new UpdateResponse(shardId, "task_id", 1, 1, 1, DocWriteResponse.Result.UPDATED);
        UpdateDataObjectResponse sdkResponse = mock(UpdateDataObjectResponse.class);
        when(sdkResponse.updateResponse()).thenReturn(updateResponse);

        CompletableFuture<UpdateDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);
        when(sdkClient.updateDataObjectAsync(any(UpdateDataObjectRequest.class))).thenReturn(future);

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", "tenant1", updatedFields, client, sdkClient, listener);
        verify(listener).onResponse(any(UpdateResponse.class));
    }

    @Test
    public void testUpdateMLTaskDirectly_SdkClientException() {
        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("field1", "value1");

        CompletableFuture<UpdateDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Test exception"));
        when(sdkClient.updateDataObjectAsync(any(UpdateDataObjectRequest.class))).thenReturn(future);

        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        MLTaskUtils.updateMLTaskDirectly("task_id", "tenant1", updatedFields, client, sdkClient, listener);
        verify(listener).onFailure(any(Exception.class));
    }
}
