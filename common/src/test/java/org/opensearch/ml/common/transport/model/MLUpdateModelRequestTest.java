/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

public class MLUpdateModelRequestTest {

    private MLUpdateModelRequest updateModelRequest;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        MLModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();

        MLUpdateModelInput updateModelInput = MLUpdateModelInput
            .builder()
            .modelId("test-model_id")
            .modelGroupId("modelGroupId")
            .name("name")
            .description("description")
            .modelConfig(config)
            .build();

        updateModelRequest = MLUpdateModelRequest.builder().updateModelInput(updateModelInput).build();

    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        updateModelRequest.writeTo(bytesStreamOutput);
        MLUpdateModelRequest parsedUpdateRequest = new MLUpdateModelRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("test-model_id", parsedUpdateRequest.getUpdateModelInput().getModelId());
        assertEquals("name", parsedUpdateRequest.getUpdateModelInput().getName());
    }

    @Test
    public void validate_Success() {
        assertNull(updateModelRequest.validate());
    }

    @Test
    public void validate_Exception_NullModelInput() {
        MLUpdateModelRequest updateModelRequest = MLUpdateModelRequest.builder().build();
        Exception exception = updateModelRequest.validate();

        assertEquals("Validation Failed: 1: Update Model Input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        assertSame(MLUpdateModelRequest.fromActionRequest(updateModelRequest), updateModelRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                updateModelRequest.writeTo(out);
            }
        };
        MLUpdateModelRequest request = MLUpdateModelRequest.fromActionRequest(actionRequest);
        assertNotSame(request, updateModelRequest);
        assertEquals(updateModelRequest.getUpdateModelInput().getName(), request.getUpdateModelInput().getName());
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
                throw new IOException();
            }
        };
        MLUpdateModelRequest.fromActionRequest(actionRequest);
    }

}
