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
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class TransportCreateModelMetaActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLModelMetaCreate mlModelMetaCreate;

    @Mock
    private ActionListener<MLCreateModelMetaResponse> actionListener;

    @Mock
    private Task task;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("customModelId");
            return null;
        }).when(mlModelMetaCreate).createModelMeta(any(), any());
    }

    public void testTransportUCreateModelMetaActionConstructor() {
        TransportCreateModelMetaAction action = new TransportCreateModelMetaAction(transportService, actionFilters, mlModelMetaCreate);
        assertNotNull(action);
    }

    public void testTransportCreateModelMetaActionDoExecute() {
        TransportCreateModelMetaAction action = new TransportCreateModelMetaAction(transportService, actionFilters, mlModelMetaCreate);
        MLCreateModelMetaRequest actionRequest = prepareRequest();
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLCreateModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLCreateModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    private MLCreateModelMetaRequest prepareRequest() {
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
                    TextEmbeddingModelConfig.PoolingMode.MEAN,
                    true,
                    512
                )
            )
            .totalChunks(2)
            .build();
        return new MLCreateModelMetaRequest(input);
    }

}
