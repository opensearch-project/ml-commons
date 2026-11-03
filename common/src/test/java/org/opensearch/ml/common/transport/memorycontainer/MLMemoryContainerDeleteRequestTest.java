/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLMemoryContainerDeleteRequestTest {
    private String memoryContainerId;

    @Before
    public void setUp() {
        memoryContainerId = "test_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlMemoryContainerDeleteRequest.writeTo(bytesStreamOutput);
        MLMemoryContainerDeleteRequest parsedMemoryContainer = new MLMemoryContainerDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedMemoryContainer.getMemoryContainerId(), memoryContainerId);
    }

    @Test
    public void validate_Success() {
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .build();
        ActionRequestValidationException actionRequestValidationException = mlMemoryContainerDeleteRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void validate_Exception_NullMemoryContainerId() {
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest.builder().build();

        ActionRequestValidationException exception = mlMemoryContainerDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML memory container id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success() {
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlMemoryContainerDeleteRequest.writeTo(out);
            }
        };
        MLMemoryContainerDeleteRequest result = MLMemoryContainerDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlMemoryContainerDeleteRequest);
        assertEquals(result.getMemoryContainerId(), mlMemoryContainerDeleteRequest.getMemoryContainerId());
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
        MLMemoryContainerDeleteRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithMemoryContainerDeleteRequest_Success() {
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .build();
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequestFromActionRequest = MLMemoryContainerDeleteRequest
            .fromActionRequest(mlMemoryContainerDeleteRequest);
        assertSame(mlMemoryContainerDeleteRequest, mlMemoryContainerDeleteRequestFromActionRequest);
        assertEquals(
            mlMemoryContainerDeleteRequest.getMemoryContainerId(),
            mlMemoryContainerDeleteRequestFromActionRequest.getMemoryContainerId()
        );
    }

    @Test
    public void writeTo_withTenantId_Success() throws IOException {
        String tenantId = "tenant-1";
        MLMemoryContainerDeleteRequest request = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .tenantId(tenantId)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLMemoryContainerDeleteRequest parsedRequest = new MLMemoryContainerDeleteRequest(out.bytes().streamInput());

        assertEquals(memoryContainerId, parsedRequest.getMemoryContainerId());
        assertEquals(tenantId, parsedRequest.getTenantId());
    }

    @Test
    public void writeTo_withoutTenantId_Success() throws IOException {
        MLMemoryContainerDeleteRequest request = MLMemoryContainerDeleteRequest.builder().memoryContainerId(memoryContainerId).build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLMemoryContainerDeleteRequest parsedRequest = new MLMemoryContainerDeleteRequest(out.bytes().streamInput());

        assertEquals(memoryContainerId, parsedRequest.getMemoryContainerId());
        assertNull(parsedRequest.getTenantId());
    }

    @Test
    public void fromActionRequest_withTenantId_Success() {
        MLMemoryContainerDeleteRequest originalRequest = MLMemoryContainerDeleteRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .tenantId("tenant-1")
            .build();
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

        MLMemoryContainerDeleteRequest parsedRequest = MLMemoryContainerDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(originalRequest, parsedRequest);
        assertEquals(originalRequest.getMemoryContainerId(), parsedRequest.getMemoryContainerId());
        assertEquals(originalRequest.getTenantId(), parsedRequest.getTenantId());
    }
}
