/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.threadpool.ThreadPool;

public class MockHelper {

    public static void mock_client_get_NotExist(Client client) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
    }

    public static void mock_client_get_NullResponse(Client client) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
    }

    public static void mock_client_get_failure(Client client) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("get doc failure"));
            return null;
        }).when(client).get(any(), any());
    }

    public static void mock_client_get_model(Client client, MLModel model) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = createGetResponse_Model(model);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
    }

    public static GetResponse createGetResponse_Model(MLModel mlModel) throws IOException {
        return new GetResponse(
            new GetResult(
                ML_MODEL_INDEX,
                mlModel.getModelId(),
                UNASSIGNED_SEQ_NO,
                0,
                -1,
                true,
                BytesReference.bytes(mlModel.toXContent(TestHelper.builder(), ToXContent.EMPTY_PARAMS)),
                Collections.emptyMap(),
                Collections.emptyMap()
            )
        );
    }

    public static void mock_client_index_failure(Client client) {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("index failure"));
            return null;
        }).when(client).index(any(), any());
    }

    public static void mock_client_index(Client client, String modelId) {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(indexResponse.getId()).thenReturn(modelId);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());
    }

    public static void mock_client_ThreadContext(Client client, ThreadPool threadPool, ThreadContext threadContext) {
        when(client.threadPool()).thenReturn(threadPool);
        when(client.threadPool().getThreadContext()).thenReturn(threadContext);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public static void mock_threadpool(ThreadPool threadPool, ExecutorService executorService) {
        when(threadPool.executor(anyString())).thenReturn(executorService);
    }

    public static void mock_MLIndicesHandler_initModelIndex_failure(MLIndicesHandler mlIndicesHandler) {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());
    }

    public static void mock_MLIndicesHandler_initModelIndex(MLIndicesHandler mlIndicesHandler, boolean result) {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(result);
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());
    }
}
