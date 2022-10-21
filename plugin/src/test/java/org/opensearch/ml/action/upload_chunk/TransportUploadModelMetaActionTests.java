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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelMetaResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

public class TransportUploadModelMetaActionTests extends OpenSearchTestCase {

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
    private Client client;
    @Mock
    private MLTaskDispatcher mlTaskDispatcher;
    @Mock
    private MLModelMetaUploader mlModelMetaUploader;

    @Mock
    private ActionListener<MLUploadModelMetaResponse> actionListener;

    @Mock
    private Task task;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("customModelId");
            return null;
        }).when(mlModelMetaUploader).uploadModelMeta(any(), any());
    }

    public void testTransportUploadModelMetaActionConstructor() {
        TransportUploadModelMetaAction action = new TransportUploadModelMetaAction(transportService, actionFilters,
                modelHelper, mlIndicesHandler, mlTaskManager, clusterService, client, mlTaskDispatcher,
                mlModelMetaUploader);
        assertNotNull(action);
    }

    public void testTransportUploadModelMetaActionDoExecute() {
        TransportUploadModelMetaAction action = new TransportUploadModelMetaAction(transportService, actionFilters,
                modelHelper, mlIndicesHandler, mlTaskManager, clusterService, client, mlTaskDispatcher,
                mlModelMetaUploader);
        MLUploadModelMetaRequest actionRequest = prepareRequest();
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUploadModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    private MLUploadModelMetaRequest prepareRequest() {
        MLUploadModelMetaInput input = MLUploadModelMetaInput.builder()
                .name("Model Name")
                .version("1")
                .description("Custom Model Test")
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .functionName(FunctionName.BATCH_RCF)
                .modelContentHash("14555")
                .modelContentSizeInBytes(1000L)
                .modelConfig(new TextEmbeddingModelConfig("CUSTOM", 123, FrameworkType.SENTENCE_TRANSFORMERS, "all config"))
                .totalChunks(2)
                .build();
        return new MLUploadModelMetaRequest(input);
    }

}
