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

public class MLModelGroupDeleteRequestTest {

    private String modelGroupId;

    private MLModelGroupDeleteRequest request;

    @Before
    public void setUp() {
        modelGroupId = "testGroupId";

        request = MLModelGroupDeleteRequest.builder()
                .modelGroupId(modelGroupId).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLModelGroupDeleteRequest parsedRequest = new MLModelGroupDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedRequest.getModelGroupId(), modelGroupId);
    }

    @Test
    public void validateSuccess() {
        assertNull(request.validate());
    }

    @Test
    public void validateWithNullModelIdException() {
        MLModelGroupDeleteRequest request = MLModelGroupDeleteRequest.builder().build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML model group id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithMLUpdateModelControllerRequestSuccess() {
        assertSame(MLModelGroupDeleteRequest.fromActionRequest(request), request);
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
        MLModelGroupDeleteRequest result = MLModelGroupDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(result.getModelGroupId(), request.getModelGroupId());
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
        MLModelGroupDeleteRequest.fromActionRequest(actionRequest);
    }

}
