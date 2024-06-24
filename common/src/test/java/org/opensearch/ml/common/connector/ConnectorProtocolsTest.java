/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConnectorProtocolsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validateProtocol_Null() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Connector protocol is null. Please use one of [aws_sigv4, http, oci_sigv1]");
        ConnectorProtocols.validateProtocol(null);
    }

    @Test
    public void validateProtocol_WrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported connector protocol. Please use one of [aws_sigv4, http, oci_sigv1]");
        ConnectorProtocols.validateProtocol("abc");
    }

    @Test
    public void validateProtocol_http() {
        ConnectorProtocols.validateProtocol(ConnectorProtocols.HTTP);
    }

    @Test
    public void validateProtocol_aws_sigv4() {
        ConnectorProtocols.validateProtocol(ConnectorProtocols.AWS_SIGV4);
    }

    @Test
    public void validateProtocol_oci_sigv1() {
        ConnectorProtocols.validateProtocol(ConnectorProtocols.OCI_SIGV1);
    }
}
