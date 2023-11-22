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

public class MLUploadModelChunkRequestTest {

    MLUploadModelChunkInput mlUploadModelChunkInput;

    @Before
    public void setUp() {
        mlUploadModelChunkInput = new MLUploadModelChunkInput("modelId", 1, new byte[] { 12, 3 });
    }

    @Test
    public void writeTo_Succeess() throws IOException {
        MLUploadModelChunkRequest request = new MLUploadModelChunkRequest(mlUploadModelChunkInput);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLUploadModelChunkRequest newRequest = new MLUploadModelChunkRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(request.getUploadModelChunkInput(), newRequest.getUploadModelChunkInput());
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLUploadModelChunkRequest mlUploadModelChunkRequest = MLUploadModelChunkRequest.builder().build();
        ActionRequestValidationException exception = mlUploadModelChunkRequest.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLUploadModelChunkRequest request = new MLUploadModelChunkRequest(mlUploadModelChunkInput);
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
        MLUploadModelChunkRequest result = MLUploadModelChunkRequest.fromActionRequest(actionRequest);
        assertNotSame(request, result);
        assertEquals(request.getUploadModelChunkInput(), result.getUploadModelChunkInput());
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
        MLUploadModelChunkRequest.fromActionRequest(actionRequest);
    }

}
