/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLMcpServerRequestTest {

    private MLMcpServerRequest mlMcpServerRequest;
    private String testRequestBody;

    @Before
    public void setUp() {
        testRequestBody = "{\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}";
        mlMcpServerRequest = new MLMcpServerRequest(testRequestBody);
    }

    @Test
    public void testConstructor_withRequestBody() {
        assertNotNull(mlMcpServerRequest);
        assertEquals(testRequestBody, mlMcpServerRequest.getRequestBody());
    }

    @Test
    public void testConstructor_withStreamInput() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlMcpServerRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpServerRequest parsedRequest = new MLMcpServerRequest(input);

        assertNotNull(parsedRequest);
        assertEquals(testRequestBody, parsedRequest.getRequestBody());
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlMcpServerRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpServerRequest parsedRequest = new MLMcpServerRequest(input);

        assertEquals(mlMcpServerRequest.getRequestBody(), parsedRequest.getRequestBody());
    }

    @Test
    public void testValidate() {
        ActionRequestValidationException validationException = mlMcpServerRequest.validate();
        assertNull(validationException);
    }

    @Test
    public void testFromActionRequest_withMLMcpServerRequest() {
        MLMcpServerRequest result = MLMcpServerRequest.fromActionRequest(mlMcpServerRequest);
        assertSame(mlMcpServerRequest, result);
    }

    @Test
    public void testFromActionRequest_withOtherActionRequest() throws IOException {
        MLMcpServerRequest mlMcpServerRequest = new MLMcpServerRequest(testRequestBody);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlMcpServerRequest.writeTo(out);
            }
        };
        MLMcpServerRequest result = MLMcpServerRequest.fromActionRequest(actionRequest);
        assertNotNull(result);
        assertEquals(testRequestBody, result.getRequestBody());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequest_withIOException() {
        ActionRequest failingRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test IOException");
            }
        };

        MLMcpServerRequest.fromActionRequest(failingRequest);
    }

    @Test
    public void testGetRequestBody() {
        assertEquals(testRequestBody, mlMcpServerRequest.getRequestBody());
    }
}
