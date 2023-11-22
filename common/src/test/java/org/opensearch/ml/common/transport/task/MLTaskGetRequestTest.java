package org.opensearch.ml.common.transport.task;

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

public class MLTaskGetRequestTest {
    private String taskId;

    @Before
    public void setUp() {
        taskId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder().taskId(taskId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlTaskGetRequest.writeTo(bytesStreamOutput);
        MLTaskGetRequest parsedModel = new MLTaskGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getTaskId(), taskId);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder().build();

        ActionRequestValidationException exception = mlTaskGetRequest.validate();
        assertEquals("Validation Failed: 1: ML task id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder().taskId(taskId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlTaskGetRequest.writeTo(out);
            }
        };
        MLTaskGetRequest result = MLTaskGetRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlTaskGetRequest);
        assertEquals(result.getTaskId(), mlTaskGetRequest.getTaskId());
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
        MLTaskGetRequest.fromActionRequest(actionRequest);
    }
}
