/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLMcpServerResponseTest {

    private MLMcpServerResponse mlMcpServerResponse;
    private Boolean testAcknowledgedResponse;
    private String testMcpResponse;
    private Map<String, Object> testError;

    @Before
    public void setUp() {
        testAcknowledgedResponse = true;
        testMcpResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}";
        testError = new HashMap<>();
        testError.put("code", -32601);
        testError.put("message", "Method not found");

        mlMcpServerResponse = new MLMcpServerResponse(testAcknowledgedResponse, testMcpResponse, testError);
    }

    @Test
    public void testConstructor_withAllFields() {
        assertNotNull(mlMcpServerResponse);
        assertEquals(testAcknowledgedResponse, mlMcpServerResponse.getAcknowledgedResponse());
        assertEquals(testMcpResponse, mlMcpServerResponse.getMcpResponse());
        assertEquals(testError, mlMcpServerResponse.getError());
    }

    @Test
    public void testConstructor_withNullFields() {
        MLMcpServerResponse nullResponse = new MLMcpServerResponse(null, null, null);

        assertNotNull(nullResponse);
        assertNull(nullResponse.getAcknowledgedResponse());
        assertNull(nullResponse.getMcpResponse());
        assertNull(nullResponse.getError());
    }

    @Test
    public void testConstructor_withStreamInput() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlMcpServerResponse.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpServerResponse parsedResponse = new MLMcpServerResponse(input);

        assertNotNull(parsedResponse);
        assertEquals(testAcknowledgedResponse, parsedResponse.getAcknowledgedResponse());
        assertEquals(testMcpResponse, parsedResponse.getMcpResponse());
        assertEquals(testError, parsedResponse.getError());
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlMcpServerResponse.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpServerResponse parsedResponse = new MLMcpServerResponse(input);

        assertEquals(mlMcpServerResponse.getAcknowledgedResponse(), parsedResponse.getAcknowledgedResponse());
        assertEquals(mlMcpServerResponse.getMcpResponse(), parsedResponse.getMcpResponse());
        assertEquals(mlMcpServerResponse.getError(), parsedResponse.getError());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlMcpServerResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertNotNull(builder);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"acknowledged\":true"));
        assertTrue(jsonStr.contains("\"mcpResponse\":"));
        assertTrue(jsonStr.contains("\"error\":"));
        assertTrue(jsonStr.contains("\"code\":-32601"));
        assertTrue(jsonStr.contains("\"message\":\"Method not found\""));
    }

    @Test
    public void testToXContent_withNullFields() throws IOException {
        MLMcpServerResponse nullResponse = new MLMcpServerResponse(null, null, null);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        nullResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{}", jsonStr);
    }

    @Test
    public void testGetAcknowledgedResponse() {
        assertEquals(testAcknowledgedResponse, mlMcpServerResponse.getAcknowledgedResponse());
    }

    @Test
    public void testGetMcpResponse() {
        assertEquals(testMcpResponse, mlMcpServerResponse.getMcpResponse());
    }

    @Test
    public void testGetError() {
        assertEquals(testError, mlMcpServerResponse.getError());
    }
}
