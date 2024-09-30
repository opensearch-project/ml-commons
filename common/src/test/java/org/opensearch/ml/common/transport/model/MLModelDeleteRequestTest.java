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
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLModelDeleteRequestTest {
    private String modelId;

    @Before
    public void setUp() {
        modelId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelDeleteRequest.writeTo(bytesStreamOutput);
        MLModelDeleteRequest parsedModel = new MLModelDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getModelId(), modelId);
    }

    @Test
    public void validate_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        ActionRequestValidationException actionRequestValidationException = mlModelDeleteRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().build();

        ActionRequestValidationException exception = mlModelDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlModelDeleteRequest.writeTo(out);
            }
        };
        MLModelDeleteRequest result = MLModelDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelDeleteRequest);
        assertEquals(result.getModelId(), mlModelDeleteRequest.getModelId());
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
        MLModelDeleteRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithModelDeleteRequest_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        MLModelDeleteRequest mlModelDeleteRequestFromActionRequest = MLModelDeleteRequest.fromActionRequest(mlModelDeleteRequest);
        assertSame(mlModelDeleteRequest, mlModelDeleteRequestFromActionRequest);
        assertEquals(mlModelDeleteRequest.getModelId(), mlModelDeleteRequestFromActionRequest.getModelId());
    }
}
