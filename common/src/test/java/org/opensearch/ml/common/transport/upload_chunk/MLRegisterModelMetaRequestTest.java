/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;

public class MLRegisterModelMetaRequestTest {

    TextEmbeddingModelConfig config;
    MLRegisterModelMetaInput mlRegisterModelMetaInput;

    @Before
    public void setUp() {
        config = new TextEmbeddingModelConfig(
            "Model Type",
            123,
            FrameworkType.SENTENCE_TRANSFORMERS,
            "All Config",
            TextEmbeddingModelConfig.PoolingMode.MEAN,
            true,
            512
        );
        mlRegisterModelMetaInput = new MLRegisterModelMetaInput(
            "Model Name",
            FunctionName.BATCH_RCF,
            "Model Group Id",
            "1.0",
            "Model Description",
            MLModelFormat.TORCH_SCRIPT,
            MLModelState.DEPLOYING,
            200L,
            "123",
            config,
            2,
            null,
            null,
            null,
            null
        );
    }

    @Test
    public void writeTo_Succeess() throws IOException {
        MLRegisterModelMetaRequest request = new MLRegisterModelMetaRequest(mlRegisterModelMetaInput);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLRegisterModelMetaRequest newRequest = new MLRegisterModelMetaRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(request.getMlRegisterModelMetaInput().getName(), newRequest.getMlRegisterModelMetaInput().getName());
        assertEquals(request.getMlRegisterModelMetaInput().getDescription(), newRequest.getMlRegisterModelMetaInput().getDescription());
        assertEquals(request.getMlRegisterModelMetaInput().getFunctionName(), newRequest.getMlRegisterModelMetaInput().getFunctionName());
        assertEquals(
            request.getMlRegisterModelMetaInput().getModelConfig().getAllConfig(),
            newRequest.getMlRegisterModelMetaInput().getModelConfig().getAllConfig()
        );
        assertEquals(request.getMlRegisterModelMetaInput().getModelGroupId(), newRequest.getMlRegisterModelMetaInput().getModelGroupId());
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLRegisterModelMetaRequest mlRegisterModelMetaRequest = MLRegisterModelMetaRequest.builder().build();
        ActionRequestValidationException exception = mlRegisterModelMetaRequest.validate();
        assertEquals("Validation Failed: 1: Model meta input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLRegisterModelMetaRequest request = new MLRegisterModelMetaRequest(mlRegisterModelMetaInput);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLRegisterModelMetaRequest newRequest = MLRegisterModelMetaRequest.fromActionRequest(actionRequest);
        assertNotSame(request, newRequest);
        assertEquals(request.getMlRegisterModelMetaInput().getName(), newRequest.getMlRegisterModelMetaInput().getName());
        assertEquals(request.getMlRegisterModelMetaInput().getDescription(), newRequest.getMlRegisterModelMetaInput().getDescription());
        assertEquals(request.getMlRegisterModelMetaInput().getFunctionName(), newRequest.getMlRegisterModelMetaInput().getFunctionName());
        assertEquals(
            request.getMlRegisterModelMetaInput().getModelConfig().getAllConfig(),
            newRequest.getMlRegisterModelMetaInput().getModelConfig().getAllConfig()
        );
        assertEquals(request.getMlRegisterModelMetaInput().getModelGroupId(), newRequest.getMlRegisterModelMetaInput().getModelGroupId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLRegisterModelMetaRequest.fromActionRequest(actionRequest);
    }
}
