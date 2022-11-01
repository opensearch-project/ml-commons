package org.opensearch.ml.common.transport.load;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.xcontent.*;
import org.opensearch.tasks.Task;
import org.opensearch.tasks.TaskId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.Map;

import static org.junit.Assert.*;

public class MLLoadModelRequestTest {

    @Mock
    private MLLoadModelRequest mlLoadModelRequest;

    @Before
    public void setUp() throws Exception {
        mlLoadModelRequest = MLLoadModelRequest.builder().
                modelId("modelId").
                modelNodeIds(new String[]{"modelNodeIds"}).
                async(true).
                dispatchTask(true).
                build();
    }

    @Test
    public void testValidate() {
         MLLoadModelRequest request = MLLoadModelRequest.builder().
                 modelId("modelId").
                 build();
        assertNull(request.validate());
    }

    @Test
    public void fromActionRequest_Success_WithMLUploadModelRequest() {
        MLLoadModelRequest request = MLLoadModelRequest.builder().
                modelId("modelId").
                build();
        assertSame(MLLoadModelRequest.fromActionRequest(request), request);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLLoadModelRequest request = MLLoadModelRequest.builder().
                modelId(null).
                modelNodeIds(new String[]{"modelNodeIds"}).
                async(true).
                dispatchTask(true).
                build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLLoadModelRequest request = mlLoadModelRequest;
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLLoadModelRequest(bytesStreamOutput.bytes().streamInput());

        assertEquals("modelId", request.getModelId());
        assertArrayEquals(new String[]{"modelNodeIds"}, request.getModelNodeIds());
        assertTrue(request.isAsync());
        assertTrue(request.isDispatchTask());
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
        MLLoadModelRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLUploadModelRequest_ConfigInput() {
        MLLoadModelRequest request = mlLoadModelRequest;
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
        MLLoadModelRequest result = MLLoadModelRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.isAsync(), result.isAsync());
        assertEquals(request.isDispatchTask(), result.isDispatchTask());
    }


/*
    // No toXcontent Method?
    @Test
    public void testParse() throws Exception {
    }
 */
}
