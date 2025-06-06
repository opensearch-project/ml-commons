package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.opensearch.ml.common.utils.StringUtils.SAFE_INPUT_DESCRIPTION;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.AccessMode;

public class MLRegisterModelGroupRequestTest {

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    private MLRegisterModelGroupRequest request;

    @Before
    public void setUp() {

        mlRegisterModelGroupInput = MLRegisterModelGroupInput
            .builder()
            .name("name")
            .description("description")
            .backendRoles(List.of("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .build();

        request = MLRegisterModelGroupRequest.builder().registerModelGroupInput(mlRegisterModelGroupInput).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        MLRegisterModelGroupRequest parsedRequest = new MLRegisterModelGroupRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(request.getRegisterModelGroupInput().getName(), parsedRequest.getRegisterModelGroupInput().getName());
        assertEquals(request.getRegisterModelGroupInput().getDescription(), parsedRequest.getRegisterModelGroupInput().getDescription());
        assertEquals(
            request.getRegisterModelGroupInput().getBackendRoles().get(0),
            parsedRequest.getRegisterModelGroupInput().getBackendRoles().get(0)
        );
        assertEquals(
            request.getRegisterModelGroupInput().getModelAccessMode(),
            parsedRequest.getRegisterModelGroupInput().getModelAccessMode()
        );
        assertEquals(
            request.getRegisterModelGroupInput().getIsAddAllBackendRoles(),
            parsedRequest.getRegisterModelGroupInput().getIsAddAllBackendRoles()
        );
    }

    @Test
    public void validateSuccess() {
        assertNull(request.validate());
    }

    @Test
    public void validateNullMLRegisterModelGroupInputException() {
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder().build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model group input can't be null;", exception.getMessage());
    }

    @Test
    public void validateNullMLModelNameException() {
        mlRegisterModelGroupInput.setName(null);
        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest
            .builder()
            .registerModelGroupInput(mlRegisterModelGroupInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals("Validation Failed: 1: Model group name is required and cannot be null or blank;", exception.getMessage());
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

    @Test
    public void validate_Exception_UnsafeModelGroupName() {
        MLRegisterModelGroupInput unsafeInput = MLRegisterModelGroupInput
            .builder()
            .name("<script>bad</script>")  // unsafe input
            .description("safe description")
            .backendRoles(List.of("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .build();

        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder().registerModelGroupInput(unsafeInput).build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model group name " + SAFE_INPUT_DESCRIPTION + ";", exception.getMessage());
    }

    @Test
    public void validate_Exception_UnsafeModelGroupDescription() {
        MLRegisterModelGroupInput unsafeInput = MLRegisterModelGroupInput
            .builder()
            .name("safeName")
            .description("<script>bad</script>")  // unsafe input
            .backendRoles(List.of("IT"))
            .modelAccessMode(AccessMode.RESTRICTED)
            .isAddAllBackendRoles(true)
            .build();

        MLRegisterModelGroupRequest request = MLRegisterModelGroupRequest.builder().registerModelGroupInput(unsafeInput).build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model group description " + SAFE_INPUT_DESCRIPTION + ";", exception.getMessage());
    }

}
