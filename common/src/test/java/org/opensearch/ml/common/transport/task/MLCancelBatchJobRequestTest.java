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

public class MLCancelBatchJobRequestTest {
    private String taskId;

    @Before
    public void setUp() {
        taskId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLCancelBatchJobRequest mlCancelBatchJobRequest = MLCancelBatchJobRequest.builder().taskId(taskId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlCancelBatchJobRequest.writeTo(bytesStreamOutput);
        MLCancelBatchJobRequest parsedTask = new MLCancelBatchJobRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedTask.getTaskId(), taskId);
    }

    @Test
    public void validate_Exception_NullTaskId() {
        MLCancelBatchJobRequest mlCancelBatchJobRequest = MLCancelBatchJobRequest.builder().build();

        ActionRequestValidationException exception = mlCancelBatchJobRequest.validate();
        assertEquals("Validation Failed: 1: ML task id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLCancelBatchJobRequest mlCancelBatchJobRequest = MLCancelBatchJobRequest.builder().taskId(taskId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlCancelBatchJobRequest.writeTo(out);
            }
        };
        MLCancelBatchJobRequest result = MLCancelBatchJobRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlCancelBatchJobRequest);
        assertEquals(result.getTaskId(), mlCancelBatchJobRequest.getTaskId());
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
        MLCancelBatchJobRequest.fromActionRequest(actionRequest);
    }
}
