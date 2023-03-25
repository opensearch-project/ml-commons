/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class MLModelChunkUploaderTests extends OpenSearchTestCase {

    public static final String USER_STRING = "myuser|role1,role2|myTenant";
    private String indexName = "testIndex";

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private ActionListener<MLUploadModelChunkResponse> actionListener;

    private ThreadContext threadContext;

    @Mock
    private ExecutorService executorService;

    @Mock
    private IndexResponse indexResponse;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Before
    public void setup() throws IOException {
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

        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .version("111")
            .name("Test Model")
            .modelId("someModelId")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .totalChunks(2)
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(indexName, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        final GetResponse getResponse = new GetResponse(getResult);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
    }

    public void testConstructor() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        assertNotNull(mlModelChunkUploader);
    }

    public void testUploadModelChunk() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<MLUploadModelChunkResponse> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelChunkResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    private MLUploadModelChunkInput prepareRequest() {
        final byte[] content = new byte[] { 1, 2, 3, 4 };
        MLUploadModelChunkInput input = MLUploadModelChunkInput.builder().chunkNumber(0).modelId("someModelId").content(content).build();
        return input;
    }

    public void testUploadModelChunkNumberEqualsChunkCount() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        uploadModelChunkInput.setChunkNumber(1);
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<MLUploadModelChunkResponse> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelChunkResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testUploadModelChunkWithNullContent() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        final byte[] content = new byte[] {};
        MLUploadModelChunkInput uploadModelChunkInput = MLUploadModelChunkInput
            .builder()
            .chunkNumber(0)
            .modelId("someModelId")
            .content(content)
            .build();
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Chunk size either 0 or null", argumentCaptor.getValue().getMessage());
    }

    public void testUploadModelChunkNumberGreaterThanTotalCount() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        uploadModelChunkInput.setChunkNumber(5);
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Chunk number exceeds total chunks", argumentCaptor.getValue().getMessage());
    }

    public void testUploadModelChunkSizeMorethan10MB() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        byte[] content = new byte[] { 1, 2, 3, 4 };
        MLModelChunkUploader spy = Mockito.spy(mlModelChunkUploader);
        when(spy.validateChunkSize(content.length)).thenReturn(true);
        MLUploadModelChunkInput input = MLUploadModelChunkInput.builder().chunkNumber(0).modelId("someModelId").content(content).build();
        spy.uploadModelChunk(input, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Chunk size exceeds 10MB", argumentCaptor.getValue().getMessage());
    }

    public void testUploadModelChunkModelNotFound() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        uploadModelChunkInput.setChunkNumber(5);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<MLResourceNotFoundException> argumentCaptor = ArgumentCaptor.forClass(MLResourceNotFoundException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

    public void testUploadModelChunkModelIndexNotFound() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        uploadModelChunkInput.setChunkNumber(5);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("Index Not Found"));
            return null;
        }).when(client).get(any(), any());
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<MLResourceNotFoundException> argumentCaptor = ArgumentCaptor.forClass(MLResourceNotFoundException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model", argumentCaptor.getValue().getMessage());
    }

    public void testUploadModelChunkIndexNotFound() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        MLUploadModelChunkInput uploadModelChunkInput = prepareRequest();
        uploadModelChunkInput.setChunkNumber(5);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(client).get(any(), any());
        mlModelChunkUploader.uploadModelChunk(uploadModelChunkInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    public void testExceeds10MB() {
        MLModelChunkUploader mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        final boolean exceeds = mlModelChunkUploader.validateChunkSize(999999999);
        assertTrue(exceeds);
    }
}
