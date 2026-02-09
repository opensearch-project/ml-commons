/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class GeminiMessageTest {

    @Test
    public void testGetResponse_AllFieldsNull() {
        // Test with all fields null - should use defaults
        GeminiMessage message = new GeminiMessage();
        message.setRole(null);
        message.setContent(null);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"user\""));
        assertTrue(response.contains("\"parts\":[]"));
    }

    @Test
    public void testGetResponse_AllFieldsSet() {
        // Test with all fields set - should use provided values
        List<Object> content = Arrays.asList("text1", "text2");
        GeminiMessage message = new GeminiMessage();
        message.setRole("model");
        message.setContent(content);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"model\""));
        assertTrue(response.contains("\"parts\""));
    }
}
