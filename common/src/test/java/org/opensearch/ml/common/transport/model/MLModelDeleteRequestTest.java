/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLModelDeleteRequestTest {
    private String modelId;

    @Before
    public void setUp() {
        modelId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelDeleteRequest.writeTo(bytesStreamOutput);
        MLModelDeleteRequest parsedModel = new MLModelDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getModelId(), modelId);
    }

    @Test
    public void validate_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        ActionRequestValidationException actionRequestValidationException = mlModelDeleteRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().build();

        ActionRequestValidationException exception = mlModelDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
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
        MLModelDeleteRequest result = MLModelDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelDeleteRequest);
        assertEquals(result.getModelId(), mlModelDeleteRequest.getModelId());
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
        MLModelDeleteRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithModelDeleteRequest_Success() {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId(modelId).build();
        MLModelDeleteRequest mlModelDeleteRequestFromActionRequest = MLModelDeleteRequest.fromActionRequest(mlModelDeleteRequest);
        assertSame(mlModelDeleteRequest, mlModelDeleteRequestFromActionRequest);
        assertEquals(mlModelDeleteRequest.getModelId(), mlModelDeleteRequestFromActionRequest.getModelId());
    }

    @Test
    public void writeTo_withTenantId_Success() throws IOException {
        String tenantId = "tenant-1";
        MLModelDeleteRequest request = MLModelDeleteRequest.builder().modelId(modelId).tenantId(tenantId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLModelDeleteRequest parsedRequest = new MLModelDeleteRequest(out.bytes().streamInput());

        assertEquals(modelId, parsedRequest.getModelId());
        assertEquals(tenantId, parsedRequest.getTenantId());
    }

    @Test
    public void writeTo_withoutTenantId_Success() throws IOException {
        MLModelDeleteRequest request = MLModelDeleteRequest.builder().modelId(modelId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLModelDeleteRequest parsedRequest = new MLModelDeleteRequest(out.bytes().streamInput());

        assertEquals(modelId, parsedRequest.getModelId());
        assertNull(parsedRequest.getTenantId());
    }

    @Test
    public void serialization_withOlderVersion_Success() throws IOException {
        MLModelDeleteRequest request = MLModelDeleteRequest.builder().modelId(modelId).tenantId("tenant-1").build();

        // Serialize with an older version
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(Version.V_2_18_0); // Older version without tenantId support
        request.writeTo(out);

        // Deserialize with the same older version
        StreamInput in = out.bytes().streamInput();
        in.setVersion(Version.V_2_18_0); // Ensure the version matches
        MLModelDeleteRequest parsedRequest = new MLModelDeleteRequest(in);

        // Validate
        assertEquals(modelId, parsedRequest.getModelId());
        assertNull(parsedRequest.getTenantId()); // tenantId should not be read
    }

    @Test
    public void serialization_withNewVersion_Success() throws IOException {
        String tenantId = "tenant-1";
        MLModelDeleteRequest request = MLModelDeleteRequest.builder().modelId(modelId).tenantId(tenantId).build();

        // Serialize with a newer version
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(Version.V_2_19_0);
        request.writeTo(out);

        // Deserialize with the same newer version
        StreamInput in = out.bytes().streamInput();
        in.setVersion(Version.V_2_19_0);
        MLModelDeleteRequest parsedRequest = new MLModelDeleteRequest(in);

        // Validate
        assertEquals(modelId, parsedRequest.getModelId());
        assertEquals(tenantId, parsedRequest.getTenantId()); // tenantId should be preserved
    }

    @Test
    public void fromActionRequest_withTenantId_Success() {
        MLModelDeleteRequest originalRequest = MLModelDeleteRequest.builder().modelId(modelId).tenantId("tenant-1").build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                originalRequest.writeTo(out);
            }
        };

        MLModelDeleteRequest parsedRequest = MLModelDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(originalRequest, parsedRequest);
        assertEquals(originalRequest.getModelId(), parsedRequest.getModelId());
        assertEquals(originalRequest.getTenantId(), parsedRequest.getTenantId());
    }

    @Test
    public void writeTo_withOlderVersion_withoutTenantId_Success() throws IOException {
        MLModelDeleteRequest request = MLModelDeleteRequest.builder().modelId(modelId).tenantId("xyz").build();

        // Serialize with an older version
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(Version.V_2_19_0);
        request.writeTo(out);

        // Deserialize
        StreamInput in = out.bytes().streamInput();
        in.setVersion(Version.V_2_18_0);
        MLModelDeleteRequest parsedRequest = new MLModelDeleteRequest(in);

        assertEquals(modelId, parsedRequest.getModelId());
        assertNull(parsedRequest.getTenantId());
    }

}
