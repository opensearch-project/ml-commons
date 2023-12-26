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

public class MLModelControllerGetRequestTest {

    private String modelId;

    private MLModelControllerGetRequest request;

    @Before
    public void setUp() {

        modelId = "testModelId";

        request = MLModelControllerGetRequest.builder().modelId(modelId).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLModelControllerGetRequest parsedRequest = new MLModelControllerGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(modelId, parsedRequest.getModelId());
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
        MLModelControllerGetRequest requestFromActionRequest = MLModelControllerGetRequest.fromActionRequest(actionRequest);
        assertNotSame(request, requestFromActionRequest);
        assertEquals(request.getModelId(), requestFromActionRequest.getModelId());
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
                throw new IOException();
            }
        };
        MLModelControllerGetRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithMLModelControllerGetRequestSuccess() {
        MLModelControllerGetRequest requestFromActionRequest = MLModelControllerGetRequest.fromActionRequest(request);
        assertSame(request, requestFromActionRequest);
        assertEquals(request.getModelId(), requestFromActionRequest.getModelId());
    }

    @Test
    public void validateNullModelIdException() {
        MLModelControllerGetRequest request = MLModelControllerGetRequest.builder().build();
        ActionRequestValidationException actionRequestValidationException = request.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", actionRequestValidationException.getMessage());
    }

    @Test
    public void validateSuccess() {
        ActionRequestValidationException actionRequestValidationException = request.validate();
        assertNull(actionRequestValidationException);
    }
}
