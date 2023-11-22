/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

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

public class MLConnectorDeleteRequestTests {
    private String connectorId;

    @Before
    public void setUp() {
        connectorId = "test-connector-id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(connectorId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlConnectorDeleteRequest.writeTo(bytesStreamOutput);
        MLConnectorDeleteRequest parsedConnector = new MLConnectorDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedConnector.getConnectorId(), connectorId);
    }

    @Test
    public void valid_Exception_NullConnectorId() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().build();
        ActionRequestValidationException exception = mlConnectorDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML connector id can't be null;", exception.getMessage());
    }

    @Test
    public void validate_Success() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(connectorId).build();
        ActionRequestValidationException actionRequestValidationException = mlConnectorDeleteRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void fromActionRequest_Success() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(connectorId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlConnectorDeleteRequest.writeTo(out);
            }
        };
        MLConnectorDeleteRequest parsedConnector = MLConnectorDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(parsedConnector, mlConnectorDeleteRequest);
        assertEquals(parsedConnector.getConnectorId(), connectorId);
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
                throw new IOException();
            }
        };
        MLConnectorDeleteRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithConnectorDeleteRequest_Success() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().connectorId(connectorId).build();
        MLConnectorDeleteRequest mlConnectorDeleteRequestFromActionRequest = MLConnectorDeleteRequest
            .fromActionRequest(mlConnectorDeleteRequest);
        assertSame(mlConnectorDeleteRequest, mlConnectorDeleteRequestFromActionRequest);
        assertEquals(mlConnectorDeleteRequest.getConnectorId(), mlConnectorDeleteRequestFromActionRequest.getConnectorId());
    }
}
