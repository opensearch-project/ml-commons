/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLModelGroupGetRequestTest {
    private String modelGroupId;

    @Before
    public void setUp() {
        modelGroupId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.builder()
                .modelGroupId(modelGroupId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelGroupGetRequest.writeTo(bytesStreamOutput);
        MLModelGroupGetRequest parsedRequest = new MLModelGroupGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedRequest.getModelGroupId(), modelGroupId);
    }

    @Test
    public void validate_Exception_NullmodelGroupId() {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.builder().build();

        ActionRequestValidationException exception = mlModelGroupGetRequest.validate();
        assertEquals("Validation Failed: 1: Model group id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.builder()
                .modelGroupId(modelGroupId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
              return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
              mlModelGroupGetRequest.writeTo(out);
            }
        };
        MLModelGroupGetRequest result = MLModelGroupGetRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelGroupGetRequest);
        assertEquals(result.getModelGroupId(), mlModelGroupGetRequest.getModelGroupId());
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
        MLModelGroupGetRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void validate_Success() {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.builder().modelGroupId(modelGroupId).build();
        ActionRequestValidationException actionRequestValidationException = mlModelGroupGetRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void fromActionRequestWithMLModelGroupGetRequest_Success() {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.builder().modelGroupId(modelGroupId).build();
        MLModelGroupGetRequest mlModelGroupGetRequestFromActionRequest = MLModelGroupGetRequest.fromActionRequest(mlModelGroupGetRequest);
        assertSame(mlModelGroupGetRequest, mlModelGroupGetRequestFromActionRequest);
        assertEquals(mlModelGroupGetRequest.getModelGroupId(), mlModelGroupGetRequestFromActionRequest.getModelGroupId());
    }
}
