/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

import io.modelcontextprotocol.spec.McpSchema;

public class MLMcpServerRequestTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String validRequestWithStringId;
    private String validRequestWithIntegerId;
    private String validNotification;

    @Before
    public void setUp() {
        validRequestWithStringId = """
            {
              "jsonrpc": "2.0",
              "id": "test-123",
              "method": "tools/list",
              "params": {}
            }
            """;

        validRequestWithIntegerId = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {}
            }
            """;

        validNotification = """
            {
              "jsonrpc": "2.0",
              "method": "ping",
              "params": {}
            }
            """;
    }

    @Test
    public void testConstructor_ValidRequestWithStringId() {
        MLMcpServerRequest request = new MLMcpServerRequest(validRequestWithStringId);

        assertNotNull(request);
        assertNotNull(request.getMessage());
        assertTrue(request.getMessage() instanceof McpSchema.JSONRPCRequest);
        assertEquals("test-123", ((McpSchema.JSONRPCRequest) request.getMessage()).id());
    }

    @Test
    public void testConstructor_ValidRequestWithIntegerId() {
        MLMcpServerRequest request = new MLMcpServerRequest(validRequestWithIntegerId);

        assertNotNull(request);
        assertEquals(1, ((McpSchema.JSONRPCRequest) request.getMessage()).id());
    }

    @Test
    public void testConstructor_ValidNotification() {
        MLMcpServerRequest request = new MLMcpServerRequest(validNotification);

        assertNotNull(request);
        assertTrue(request.getMessage() instanceof McpSchema.JSONRPCNotification);
    }

    @Test
    public void testConstructor_InvalidJsonRpcVersion() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Invalid jsonrpc version");

        String invalidRequest = """
            {
              "jsonrpc": "1.0",
              "id": 1,
              "method": "ping"
            }
            """;
        new MLMcpServerRequest(invalidRequest);
    }

    @Test
    public void testConstructor_InvalidMethod() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Invalid MCP method");

        String invalidRequest = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "invalid_method"
            }
            """;
        new MLMcpServerRequest(invalidRequest);
    }

    @Test
    public void testConstructor_InvalidIdWithSpecialCharacters() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("can only contain");

        String invalidRequest = """
            {
              "jsonrpc": "2.0",
              "id": "<script>alert()</script>",
              "method": "ping"
            }
            """;
        new MLMcpServerRequest(invalidRequest);
    }

    @Test
    public void testConstructor_ResponseRejected() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("JSON-RPC responses are not accepted");

        String response = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {}
            }
            """;
        new MLMcpServerRequest(response);
    }

    @Test
    public void testConstructor_EmptyBody() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("cannot be null or empty");

        new MLMcpServerRequest("");
    }

    @Test
    public void testConstructor_NullBody() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("cannot be null or empty");

        String nullBody = null;
        new MLMcpServerRequest(nullBody);
    }

    @Test
    public void testConstructor_InvalidJson() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Failed to parse JSON-RPC message");

        new MLMcpServerRequest("invalid json");
    }

    @Test
    public void testWriteTo_Success() throws IOException {
        MLMcpServerRequest original = new MLMcpServerRequest(validRequestWithIntegerId);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpServerRequest deserialized = new MLMcpServerRequest(input);

        assertNotNull(deserialized);
        assertEquals(original.getMessage().jsonrpc(), deserialized.getMessage().jsonrpc());
    }

    @Test
    public void testFromActionRequest_SameInstance() {
        MLMcpServerRequest original = new MLMcpServerRequest(validRequestWithIntegerId);

        MLMcpServerRequest result = MLMcpServerRequest.fromActionRequest(original);

        assertSame(original, result);
    }

    @Test
    public void testValidate_Success() {
        MLMcpServerRequest request = new MLMcpServerRequest(validRequestWithIntegerId);

        ActionRequestValidationException validation = request.validate();

        assertNull(validation);
    }
}
