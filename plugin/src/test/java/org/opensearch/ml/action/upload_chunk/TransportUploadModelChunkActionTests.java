/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class TransportUploadModelChunkActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

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
        }).when(mlModelUploader).uploadModelChunk(any(), any());
    }

    public void testTransportUploadModelChunkActionConstructor() {
        TransportUploadModelChunkAction action = new TransportUploadModelChunkAction(transportService, actionFilters, mlModelUploader);
        assertNotNull(action);
    }

    public void testTransportUploadModelChunkActionDoExecute() {
        TransportUploadModelChunkAction action = new TransportUploadModelChunkAction(transportService, actionFilters, mlModelUploader);
        assertNotNull(action);
        MLUploadModelChunkRequest request = prepareRequest();
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<MLUploadModelChunkResponse> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelChunkResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    private MLUploadModelChunkRequest prepareRequest() {
        MLUploadModelChunkInput input = MLUploadModelChunkInput.builder().chunkNumber(1).content(new byte[] { 13, 4, 4 }).build();
        return new MLUploadModelChunkRequest(input);
    }

}
