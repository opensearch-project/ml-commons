/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;

import org.junit.Test;

public class OpenaiMessageTest {

    @Test
    public void testGetResponse_AllFieldsNull() {
        // Test with all fields null - should use defaults
        OpenaiMessage message = new OpenaiMessage();
        message.setRole(null);
        message.setContent(null);
        message.setToolCallId(null);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"tool\""));
        assertTrue(response.contains("\"tool_call_id\":\"\""));
        assertTrue(response.contains("\"content\":\"\""));
    }

    @Test
    public void testGetResponse_AllFieldsSet() {
        // Test with all fields set - should use provided values
        OpenaiMessage message = new OpenaiMessage();
        message.setRole("assistant");
        message.setContent("test content");
        message.setToolCallId("call_123");

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"assistant\""));
        assertTrue(response.contains("\"tool_call_id\":\"call_123\""));
        assertTrue(response.contains("\"content\":\"test content\""));
    }
}
