/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

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

public class MLModelControllerDeleteRequestTest {

    private String modelId;

    private MLModelControllerDeleteRequest request;

    @Before
    public void setUp() {

        modelId = "testModelId";

        request = MLModelControllerDeleteRequest.builder()
                .modelId(modelId).build();
    }


    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLModelControllerDeleteRequest parsedRequest = new MLModelControllerDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedRequest.getModelId(), modelId);
    }

    @Test
    public void validateSuccess() {
        assertNull(request.validate());
    }

    @Test
    public void validateWithNullModelIdException() {
        MLModelControllerDeleteRequest request = MLModelControllerDeleteRequest.builder().build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithMLUpdateModelControllerRequestSuccess() {
        assertSame(MLModelControllerDeleteRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequestSuccess() {
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
        MLModelControllerDeleteRequest result = MLModelControllerDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(result.getModelId(), request.getModelId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequestIOException() {
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
        MLModelControllerDeleteRequest.fromActionRequest(actionRequest);
    }

}
