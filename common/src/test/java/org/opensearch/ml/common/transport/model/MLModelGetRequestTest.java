/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class MLModelGetRequestTest {
    private String modelId;

    @Before
    public void setUp() {
        modelId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder()
                .modelId(modelId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelGetRequest.writeTo(bytesStreamOutput);
        MLModelGetRequest parsedModel = new MLModelGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getModelId(), modelId);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().build();

        ActionRequestValidationException exception = mlModelGetRequest.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder()
                .modelId(modelId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
              return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
              mlModelGetRequest.writeTo(out);
            }
        };
        MLModelGetRequest result = MLModelGetRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelGetRequest);
        assertEquals(result.getModelId(), mlModelGetRequest.getModelId());
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
        MLModelGetRequest.fromActionRequest(actionRequest);
    }
}
