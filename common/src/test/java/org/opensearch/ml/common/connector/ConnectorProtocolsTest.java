/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ConnectorProtocolsTest {
    @Test
    public void validateProtocol_Null() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ConnectorProtocols.validateProtocol(null));
        assertEquals(
            "Connector protocol is null. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]",
            exception.getMessage()
        );
    }

    @Test
    public void validateProtocol_WrongValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ConnectorProtocols.validateProtocol("abc"));
        assertEquals(
            "Unsupported connector protocol. Please use one of [aws_sigv4, http, mcp_sse, mcp_streamable_http]",
            exception.getMessage()
        );
    }
}
