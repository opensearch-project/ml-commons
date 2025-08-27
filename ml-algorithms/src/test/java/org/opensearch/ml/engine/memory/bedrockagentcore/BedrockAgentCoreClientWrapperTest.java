/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

/**
 * Unit test for BedrockAgentCoreClientWrapper.
 */
public class BedrockAgentCoreClientWrapperTest {

    @Test
    public void testClientCreationWithValidCredentials() {
        Map<String, String> credentials = Map.of("access_key", "AKIATEST123", "secret_key", "test-secret-key");

        try {
            BedrockAgentCoreClientWrapper client = new BedrockAgentCoreClientWrapper("us-east-1", credentials);
            assertNotNull(client);
        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage());
        }
    }

    @Test
    public void testAutoCloseableImplementation() {
        Map<String, String> credentials = Map.of("access_key", "AKIATEST123", "secret_key", "test-secret-key");

        try (BedrockAgentCoreClientWrapper client = new BedrockAgentCoreClientWrapper("us-east-1", credentials)) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage());
        }
    }
}
