/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class MLModelMetaCreateTests extends OpenSearchTestCase {

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private ActionListener<String> actionListener;

    private ThreadContext threadContext;

    @Mock
    private ExecutorService executorService;

    @Mock
    private IndexResponse indexResponse;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());

    }

    public void testConstructor() {
        MLModelMetaCreate mlModelChunkCreate = new MLModelMetaCreate(mlIndicesHandler, threadPool, client);
        assertNotNull(mlModelChunkCreate);
    }

    public void testUploadModel() {
        MLModelMetaCreate mlModelMetaCreate = new MLModelMetaCreate(mlIndicesHandler, threadPool, client);
        MLCreateModelMetaInput mlUploadInput = prepareRequest();
        mlModelMetaCreate.createModelMeta(mlUploadInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testUploadModelFiledIndex() {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Init Index Failed"));
            return null;
        }).when(client).index(any(), any());
        MLModelMetaCreate mlModelMetaCreate = new MLModelMetaCreate(mlIndicesHandler, threadPool, client);
        MLCreateModelMetaInput mlUploadInput = prepareRequest();
        mlModelMetaCreate.createModelMeta(mlUploadInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void testUploadModelFiledInitIndexIfPresent() {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onFailure(new Exception("initModelIndexIfAbsent Failed"));
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());
        MLModelMetaCreate mlModelMetaCreate = new MLModelMetaCreate(mlIndicesHandler, threadPool, client);
        MLCreateModelMetaInput mlUploadInput = prepareRequest();
        mlModelMetaCreate.createModelMeta(mlUploadInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    private MLCreateModelMetaInput prepareRequest() {
        MLCreateModelMetaInput input = MLCreateModelMetaInput
            .builder()
            .name("Model Name")
            .version("1")
            .description("Custom Model Test")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .functionName(FunctionName.BATCH_RCF)
            .modelContentHashValue("14555")
            .modelContentSizeInBytes(1000L)
            .modelConfig(
                new TextEmbeddingModelConfig(
                    "CUSTOM",
                    123,
                    FrameworkType.SENTENCE_TRANSFORMERS,
                    "all config",
                    TextEmbeddingModelConfig.PoolingMethod.MEAN,
                    true,
                    512
                )
            )
            .totalChunks(2)
            .build();
        return input;
    }
}
