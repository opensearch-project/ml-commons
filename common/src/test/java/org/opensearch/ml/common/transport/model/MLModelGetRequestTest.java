/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class MLModelGetRequestTest {
    private String modelId;

    @Before
    public void setUp() {
        modelId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModelGetRequest.writeTo(bytesStreamOutput);
        MLModelGetRequest parsedModel = new MLModelGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedModel.getModelId(), modelId);
    }

    @Test
    public void validate_Exception_NullModelId() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().build();

        ActionRequestValidationException exception = mlModelGetRequest.validate();
        assertEquals("Validation Failed: 1: ML model id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlModelGetRequest.writeTo(out);
            }
        };
        MLModelGetRequest result = MLModelGetRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlModelGetRequest);
        assertEquals(result.getModelId(), mlModelGetRequest.getModelId());
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
        MLModelGetRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void validate_Success() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).build();
        ActionRequestValidationException actionRequestValidationException = mlModelGetRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void fromActionRequestWithMLModelGetRequest_Success() {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).build();
        MLModelGetRequest mlModelGetRequestFromActionRequest = MLModelGetRequest.fromActionRequest(mlModelGetRequest);
        assertSame(mlModelGetRequest, mlModelGetRequestFromActionRequest);
        assertEquals(mlModelGetRequest.getModelId(), mlModelGetRequestFromActionRequest.getModelId());
    }

    @Test
    public void writeTo_withTenantId_Success() throws IOException {
        String tenantId = "tenant-1";
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).tenantId(tenantId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Newer version supporting tenantId
        mlModelGetRequest.writeTo(out);

        MLModelGetRequest parsedRequest = new MLModelGetRequest(out.bytes().streamInput());
        assertEquals(modelId, parsedRequest.getModelId());
        assertEquals(tenantId, parsedRequest.getTenantId());
    }

    @Test
    public void writeTo_withoutTenantId_Success() throws IOException {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder().modelId(modelId).build(); // No tenantId set

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Newer version supporting tenantId
        mlModelGetRequest.writeTo(out);

        MLModelGetRequest parsedRequest = new MLModelGetRequest(out.bytes().streamInput());
        assertEquals(modelId, parsedRequest.getModelId());
        assertNull(parsedRequest.getTenantId()); // TenantId should be null
    }

    @Test
    public void writeTo_withOlderVersion_Success() throws IOException {
        String tenantId = "tenant-1";
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest
            .builder()
            .modelId(modelId)
            .tenantId(tenantId) // Tenant ID is set, but won't be written for older versions
            .build();

        // Serialize with an older version
        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_18_0); // Older version without tenantId support
        mlModelGetRequest.writeTo(out);

        // Deserialize with the same older version
        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_18_0); // Set version explicitly
        MLModelGetRequest parsedRequest = new MLModelGetRequest(in);

        // Validate deserialization
        assertEquals(modelId, parsedRequest.getModelId());
        assertNull(parsedRequest.getTenantId()); // TenantId should not be present for older versions
        assertFalse(parsedRequest.isReturnContent()); // Default value for boolean fields
        assertFalse(parsedRequest.isUserInitiatedGetRequest()); // Default value for boolean fields
    }

    @Test
    public void fromActionRequest_withTenantId_Success() {
        String tenantId = "tenant-1";
        MLModelGetRequest originalRequest = MLModelGetRequest.builder().modelId(modelId).tenantId(tenantId).build();

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

        MLModelGetRequest parsedRequest = MLModelGetRequest.fromActionRequest(actionRequest);
        assertEquals(originalRequest.getModelId(), parsedRequest.getModelId());
        assertEquals(originalRequest.getTenantId(), parsedRequest.getTenantId());
    }
}
