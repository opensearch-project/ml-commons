package org.opensearch.ml.common.transport.model_group;

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

public class MLModelGroupDeleteRequestTest {

    private String modelGroupId;

    @Before
    public void setUp() {
        modelGroupId = "test_group_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelGroupDeleteRequest mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelGroupDeleteRequest.writeTo(bytesStreamOutput);
        MLModelGroupDeleteRequest parsedModel = new MLModelGroupDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getModelGroupId(), modelGroupId);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLModelGroupDeleteRequest mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.builder().build();

        ActionRequestValidationException exception = mlModelGroupDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML model group id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelGroupDeleteRequest mlModelDeleteRequest = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).build();
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
        MLModelGroupDeleteRequest result = MLModelGroupDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelDeleteRequest);
        assertEquals(result.getModelGroupId(), mlModelDeleteRequest.getModelGroupId());
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
        MLModelGroupDeleteRequest.fromActionRequest(actionRequest);
    }

}
