/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.ConnectorProtocols.supportedProtocols;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConnectorProtocolsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    // Authoritative literal guard: asserts the exact supported-protocol list so an accidental
    // add/remove/reorder is caught. Other tests may build messages from supportedProtocols();
    // this one intentionally does not, so it is not tautological.
    @Test
    public void supportedProtocols_exactList() {
        assertEquals("[aws_sigv4, http, google_cloud, mcp_sse, mcp_streamable_http]", supportedProtocols());
    }

    @Test
    public void validateProtocol_Null() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector protocol is null. Please use one of " + supportedProtocols());
        ConnectorProtocols.validateProtocol(null);
    }

    @Test
    public void validateProtocol_WrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of " + supportedProtocols());
        ConnectorProtocols.validateProtocol("abc");
    }

    @Test
    public void validateProtocol_GoogleCloud_NoException() {
        // Should not throw for the new google_cloud protocol
        ConnectorProtocols.validateProtocol(ConnectorProtocols.GOOGLE_CLOUD);
    }

    @Test
    public void isMcpProtocol_bothMcpProtocols() {
        assertTrue(ConnectorProtocols.isMcpProtocol(ConnectorProtocols.MCP_SSE));
        assertTrue(ConnectorProtocols.isMcpProtocol(ConnectorProtocols.MCP_STREAMABLE_HTTP));
    }

    @Test
    public void isMcpProtocol_inferenceProtocolsAndNull() {
        assertFalse(ConnectorProtocols.isMcpProtocol(ConnectorProtocols.HTTP));
        assertFalse(ConnectorProtocols.isMcpProtocol(ConnectorProtocols.AWS_SIGV4));
        assertFalse(ConnectorProtocols.isMcpProtocol(ConnectorProtocols.GOOGLE_CLOUD));
        assertFalse(ConnectorProtocols.isMcpProtocol(null));
        assertFalse(ConnectorProtocols.isMcpProtocol("mcp"));
    }

    /** Callers that gate on MCP must not be sidestepped by casing, so matching ignores it. */
    @Test
    public void isMcpProtocol_ignoresCase() {
        assertTrue(ConnectorProtocols.isMcpProtocol("MCP_SSE"));
        assertTrue(ConnectorProtocols.isMcpProtocol("Mcp_Streamable_Http"));
    }
}
