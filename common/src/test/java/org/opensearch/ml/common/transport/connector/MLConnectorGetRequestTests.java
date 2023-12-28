/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.opensearch.ml.common.transport.connector;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLConnectorGetRequestTests {
    private String connectorId;

    @Before
    public void setUp() {
        connectorId = "testConnectorId";
    }

    @Test
    public void writeToSuccess() throws IOException {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(connectorId).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlConnectorGetRequest.writeTo(bytesStreamOutput);
        MLConnectorGetRequest parsedRequest = new MLConnectorGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(connectorId, parsedRequest.getConnectorId());
    }

    @Test
    public void fromActionRequestSuccess() {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(connectorId).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlConnectorGetRequest.writeTo(out);
            }
        };
        MLConnectorGetRequest mlConnectorGetRequestFromActionRequest = MLConnectorGetRequest.fromActionRequest(actionRequest);
        assertNotSame(mlConnectorGetRequest, mlConnectorGetRequestFromActionRequest);
        assertEquals(mlConnectorGetRequest.getConnectorId(), mlConnectorGetRequestFromActionRequest.getConnectorId());
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
        MLConnectorGetRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void fromActionRequestWithMLConnectorGetRequestSuccess() {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(connectorId).build();
        MLConnectorGetRequest mlConnectorGetRequestFromActionRequest = MLConnectorGetRequest.fromActionRequest(mlConnectorGetRequest);
        assertSame(mlConnectorGetRequest, mlConnectorGetRequestFromActionRequest);
        assertEquals(mlConnectorGetRequest.getConnectorId(), mlConnectorGetRequestFromActionRequest.getConnectorId());
    }

    @Test
    public void validateWithNullConnectorIdException() {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.builder().build();
        ActionRequestValidationException actionRequestValidationException = mlConnectorGetRequest.validate();
        assertEquals("Validation Failed: 1: ML connector id can't be null;", actionRequestValidationException.getMessage());
    }

    @Test
    public void validateSuccess() {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(connectorId).build();
        ActionRequestValidationException actionRequestValidationException = mlConnectorGetRequest.validate();
        assertNull(actionRequestValidationException);
    }
}

