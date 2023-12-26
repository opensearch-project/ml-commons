package org.opensearch.ml.common.transport.model_group;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.AccessMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLRegisterModelGroupRequestTest {

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    private MLRegisterModelGroupRequest request;

    @Before
    public void setUp(){

        mlRegisterModelGroupInput = MLRegisterModelGroupInput.builder()
                .name("name")
                .description("description")
                .backendRoles(List.of("IT"))
                .modelAccessMode(AccessMode.RESTRICTED)
                .isAddAllBackendRoles(true)
                .build();

        request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLRegisterModelGroupRequest parsedRequest = new MLRegisterModelGroupRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(request.getRegisterModelGroupInput().getName(), parsedRequest.getRegisterModelGroupInput().getName());
        assertEquals(request.getRegisterModelGroupInput().getDescription(), parsedRequest.getRegisterModelGroupInput().getDescription());
        assertEquals(request.getRegisterModelGroupInput().getBackendRoles().get(0), parsedRequest.getRegisterModelGroupInput().getBackendRoles().get(0));
        assertEquals(request.getRegisterModelGroupInput().getModelAccessMode(), parsedRequest.getRegisterModelGroupInput().getModelAccessMode());
        assertEquals(request.getRegisterModelGroupInput().getIsAddAllBackendRoles() ,parsedRequest.getRegisterModelGroupInput().getIsAddAllBackendRoles());
    }

    @Test
    public void validateSuccess() {
        assertNull(request.validate());
    }

    @Test
    public void validateNullMLRegisterModelGroupInputException() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder().build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model meta input can't be null;", exception.getMessage());
    }

    @Test
    // MLRegisterModelGroupInput check its parameters when created, so exception is not thrown here
    public void validateNullMLModelNameException() {
        mlRegisterModelGroupInput.setName(null);
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder()
                .registerModelGroupInput(mlRegisterModelGroupInput)
                .build();

        assertNull(request.validate());
        assertNull(request.getRegisterModelGroupInput().getName());
    }

    @Test
    public void fromActionRequestWithMLRegisterModelGroupRequestSuccess() {
        assertSame(MLRegisterModelGroupRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequestWithNonMLRegisterModelGroupRequestSuccess() {
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
        MLRegisterModelGroupRequest.fromActionRequest(actionRequest);
    }
}
