/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectorAuthTypeTests {

    @Test
    public void from_acceptsSupportedTypes() {
        assertEquals(ConnectorAuthType.OAUTH2, ConnectorAuthType.from("oauth2"));
        assertEquals(ConnectorAuthType.NONE, ConnectorAuthType.from("none"));
        // Built in code and read back from the transport; a request never gets this far with it.
        assertEquals(ConnectorAuthType.GOOGLE_ADC, ConnectorAuthType.from("google_adc"));
    }

    @Test
    public void from_rejectsUnsupportedTypesAndListsSupportedOnes() {
        for (String value : new String[] { "OAuth2", "custom", "", null }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConnectorAuthType.from(value));
            assertEquals("Unsupported auth_config type [" + value + "]; supported: oauth2, none", e.getMessage());
        }
    }

    @Test
    public void isActive_isFalseOnlyForNone() {
        assertTrue(ConnectorAuthType.OAUTH2.isActive());
        assertTrue(ConnectorAuthType.GOOGLE_ADC.isActive());
        assertFalse(ConnectorAuthType.NONE.isActive());
    }

    @Test
    public void isUserWritable_isFalseOnlyForGoogleAdc() {
        assertTrue(ConnectorAuthType.OAUTH2.isUserWritable());
        assertTrue(ConnectorAuthType.NONE.isUserWritable());
        assertFalse(ConnectorAuthType.GOOGLE_ADC.isUserWritable());
    }
}
