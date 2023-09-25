package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
import org.opensearch.ml.common.model.ModelGroupTag;

public class MLUpdateModelGroupRequestTest {

    private MLUpdateModelGroupInput mlUpdateModelGroupInput;

    @Before
    public void setUp(){
        mlUpdateModelGroupInput = MLUpdateModelGroupInput.builder()
                .modelGroupID("modelGroupId")
                .name("name")
                .description("description")
                .backendRoles(List.of("IT"))
                .modelAccessMode(AccessMode.RESTRICTED)
                .isAddAllBackendRoles(true)
                .tags(List.of(new ModelGroupTag("tag1", "String"),
                    new ModelGroupTag("tag2", "Number")))
                .build();
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .updateModelGroupInput(mlUpdateModelGroupInput)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLUpdateModelGroupRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("modelGroupId", request.getUpdateModelGroupInput().getModelGroupID());
        assertEquals("name", request.getUpdateModelGroupInput().getName());
        assertEquals("description", request.getUpdateModelGroupInput().getDescription());
        assertEquals("IT", request.getUpdateModelGroupInput().getBackendRoles().get(0));
        assertEquals(AccessMode.RESTRICTED, request.getUpdateModelGroupInput().getModelAccessMode());
        assertEquals(true, request.getUpdateModelGroupInput().getIsAddAllBackendRoles());
        assertEquals("tag1", request.getUpdateModelGroupInput().getTags().get(0).getKey());
        assertEquals("String", request.getUpdateModelGroupInput().getTags().get(0).getType());
        assertEquals("tag2", request.getUpdateModelGroupInput().getTags().get(1).getKey());
        assertEquals("Number", request.getUpdateModelGroupInput().getTags().get(1).getType());
    }

    @Test
    public void validate_Success() {
        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .updateModelGroupInput(mlUpdateModelGroupInput)
                .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLRegisterModelGroupInput() {
        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Update Model group input can't be null;", exception.getMessage());
    }

    @Test
    // MLRegisterModelGroupInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        mlUpdateModelGroupInput.setName(null);
        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .updateModelGroupInput(mlUpdateModelGroupInput)
                .build();

        assertNull(request.validate());
        assertNull(request.getUpdateModelGroupInput().getName());
    }


    @Test
    public void fromActionRequest_Success_WithMLUpdateModelRequest() {
        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .updateModelGroupInput(mlUpdateModelGroupInput)
                .build();
        assertSame(MLUpdateModelGroupRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLUpdateModelRequest() {
        MLUpdateModelGroupRequest request = MLUpdateModelGroupRequest.builder()
                .updateModelGroupInput(mlUpdateModelGroupInput)
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
        MLUpdateModelGroupRequest result = MLUpdateModelGroupRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getUpdateModelGroupInput().getName(), result.getUpdateModelGroupInput().getName());
        assertEquals(request.getUpdateModelGroupInput().getTags().get(0).getKey(), result.getUpdateModelGroupInput().getTags().get(0).getKey());
        assertEquals(request.getUpdateModelGroupInput().getTags().get(0).getType(), result.getUpdateModelGroupInput().getTags().get(0).getType());
        assertEquals(request.getUpdateModelGroupInput().getTags().get(1).getKey(), result.getUpdateModelGroupInput().getTags().get(1).getKey());
        assertEquals(request.getUpdateModelGroupInput().getTags().get(1).getType(), result.getUpdateModelGroupInput().getTags().get(1).getType());
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
        MLUpdateModelGroupRequest.fromActionRequest(actionRequest);
    }
}
