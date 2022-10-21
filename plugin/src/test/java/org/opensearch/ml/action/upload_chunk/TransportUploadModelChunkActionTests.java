/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

public class TransportUploadModelChunkActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ModelHelper modelHelper;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private MLTaskManager mlTaskManager;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private Client client;
    @Mock
    private MLTaskDispatcher mlTaskDispatcher;
    @Mock
    private MLModelChunkUploader mlModelUploader;

    @Mock
    private MLUploadModelChunkResponse response;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLUploadModelChunkResponse> actionListener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            ActionListener<MLUploadModelChunkResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(mlModelUploader).uploadModel(any(), any());
    }

    public void testTransportUploadModelChunkActionConstructor() {
        TransportUploadModelChunkAction action = new TransportUploadModelChunkAction(transportService, actionFilters,
                modelHelper, mlIndicesHandler, mlTaskManager, clusterService, threadPool, client, mlTaskDispatcher,
                mlModelUploader);
        assertNotNull(action);
    }

    public void testTransportUploadModelChunkActionDoExecute() {
        TransportUploadModelChunkAction action = new TransportUploadModelChunkAction(transportService, actionFilters,
                modelHelper, mlIndicesHandler, mlTaskManager, clusterService, threadPool, client, mlTaskDispatcher,
                mlModelUploader);
        assertNotNull(action);
        MLUploadModelChunkRequest request = prepareRequest();
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<MLUploadModelChunkResponse> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelChunkResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    private MLUploadModelChunkRequest prepareRequest() {
        MLUploadModelChunkInput input = MLUploadModelChunkInput.builder().chunkNumber(1).content(new byte[]{13, 4, 4}).build();
        return new MLUploadModelChunkRequest(input);
    }

}
