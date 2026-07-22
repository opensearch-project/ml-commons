/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorProtocols.supportedProtocols;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConnectorProtocolsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
}
