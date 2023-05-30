package org.opensearch.ml.common.transport.model_group;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.ModelAccessMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLRegisterModelGroupRequestTest {

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    @Before
    public void setUp(){

        mlRegisterModelGroupInput = mlRegisterModelGroupInput.builder()
                .name("name")
                .description("description")
                .backendRoles(Arrays.asList("IT"))
                .modelAccessMode(ModelAccessMode.RESTRICTED)
                .isAddAllBackendRoles(true)
                .build();
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLRegisterModelGroupRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("name", request.getRegisterModelGroupInput().getName());
        assertEquals("description", request.getRegisterModelGroupInput().getDescription());
        assertEquals("IT", request.getRegisterModelGroupInput().getBackendRoles().get(0));
        assertEquals(ModelAccessMode.RESTRICTED, request.getRegisterModelGroupInput().getModelAccessMode());
        assertEquals(true, request.getRegisterModelGroupInput().getIsAddAllBackendRoles());
    }

    @Test
    public void validate_Success() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLRegisterModelGroupInput() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model meta input can't be null;", exception.getMessage());
    }

    @Test
    // MLRegisterModelGroupInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        mlRegisterModelGroupInput.setName(null);
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();

        assertNull(request.validate());
        assertNull(request.getRegisterModelGroupInput().getName());
    }

    @Test
    public void fromActionRequest_Success_WithMLRegisterModelRequest() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();
        assertSame(MLRegisterModelGroupRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLRegisterModelRequest() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();
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
        MLRegisterModelGroupRequest result = MLRegisterModelGroupRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getRegisterModelGroupInput().getName(), result.getRegisterModelGroupInput().getName());
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
        MLRegisterModelGroupRequest.fromActionRequest(actionRequest);
    }
}
