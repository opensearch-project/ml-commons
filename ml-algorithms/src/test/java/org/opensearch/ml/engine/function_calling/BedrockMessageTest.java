/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class BedrockMessageTest {

    @Test
    public void testGetResponse_AllFieldsNull() {
        // Test with all fields null - should use defaults
        BedrockMessage message = new BedrockMessage();
        message.setRole(null);
        message.setContent(null);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"user\""));
        assertTrue(response.contains("\"content\":[]"));
    }

    @Test
    public void testGetResponse_AllFieldsSet() {
        // Test with all fields set - should use provided values
        List<Object> content = Arrays.asList("text1", "text2");
        BedrockMessage message = new BedrockMessage();
        message.setRole("assistant");
        message.setContent(content);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"assistant\""));
        assertTrue(response.contains("\"content\""));
    }
}
