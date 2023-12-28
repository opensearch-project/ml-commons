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
        connectorId = "testConnectorId";
    }

    @Test
    public void writeToSuccess() throws IOException {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder()
                .connectorId(connectorId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlConnectorDeleteRequest.writeTo(bytesStreamOutput);
        MLConnectorDeleteRequest parsedRequest = new MLConnectorDeleteRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedRequest.getConnectorId(), connectorId);
    }

    @Test
    public void validWithNullConnectorIdException() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder().build();
        ActionRequestValidationException exception = mlConnectorDeleteRequest.validate();
        assertEquals("Validation Failed: 1: ML connector id can't be null;", exception.getMessage());
    }

    @Test
    public void validateSuccess() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder()
                .connectorId(connectorId).build();
        ActionRequestValidationException actionRequestValidationException = mlConnectorDeleteRequest.validate();
        assertNull(actionRequestValidationException);
    }

    @Test
    public void fromActionRequestSuccess() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder()
                .connectorId(connectorId).build();
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
        MLConnectorDeleteRequest parsedRequest = MLConnectorDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(parsedRequest, mlConnectorDeleteRequest);
        assertEquals(parsedRequest.getConnectorId(), connectorId);
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
                throw new IOException();
            }
        };
        MLConnectorDeleteRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithConnectorDeleteRequestSuccess() {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.builder()
                .connectorId(connectorId).build();
        MLConnectorDeleteRequest mlConnectorDeleteRequestFromActionRequest = MLConnectorDeleteRequest.fromActionRequest(mlConnectorDeleteRequest);
        assertSame(mlConnectorDeleteRequest, mlConnectorDeleteRequestFromActionRequest);
        assertEquals(mlConnectorDeleteRequest.getConnectorId(), mlConnectorDeleteRequestFromActionRequest.getConnectorId());
    }
}
