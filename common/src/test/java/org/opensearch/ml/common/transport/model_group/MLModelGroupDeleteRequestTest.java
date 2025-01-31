package org.opensearch.ml.common.transport.model_group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.opensearch.ml.common.CommonValue.VERSION_2_18_0;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLModelGroupDeleteRequestTest {

    private String modelGroupId;
    private String tenantId;
    private MLModelGroupDeleteRequest request;

    @Before
    public void setUp() {
        modelGroupId = "testGroupId";

        request = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).build();
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
    public void fromActionRequestWithMLUpdateControllerRequestSuccess() {
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

    @Test
    public void writeToAndReadFrom_withTenantId_Success() throws IOException {
        tenantId = "tenant-1";
        request = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).tenantId(tenantId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Newer version supporting tenantId
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0); // Ensure version alignment
        MLModelGroupDeleteRequest parsedRequest = new MLModelGroupDeleteRequest(in);

        assertEquals(modelGroupId, parsedRequest.getModelGroupId());
        assertEquals(tenantId, parsedRequest.getTenantId());
    }

    @Test
    public void fromActionRequest_withTenantId_Success() {
        tenantId = "tenant-1";
        request = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).tenantId(tenantId).build();

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
        assertEquals(result.getTenantId(), request.getTenantId());
    }

    @Test
    public void writeToAndReadFrom_withOlderVersion_TenantIdIgnored() throws IOException {
        tenantId = "tenant-1";
        request = MLModelGroupDeleteRequest.builder().modelGroupId(modelGroupId).tenantId(tenantId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Serialize with newer version
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_18_0); // Older version without tenantId support
        MLModelGroupDeleteRequest parsedRequest = new MLModelGroupDeleteRequest(in);

        assertEquals(modelGroupId, parsedRequest.getModelGroupId());
        assertNull(parsedRequest.getTenantId()); // tenantId should not be deserialized
    }

}
