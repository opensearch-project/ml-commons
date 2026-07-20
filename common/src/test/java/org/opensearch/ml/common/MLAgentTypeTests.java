/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.*;

import org.junit.Test;

public class MLAgentTypeTests {
    @Test
    public void testFromWithValidTypes() {
        // Test all enum values to ensure they return correctly
        assertEquals(MLAgentType.FLOW, MLAgentType.from("FLOW"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("CONVERSATIONAL"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("CONVERSATIONAL_FLOW"));
    }

    @Test
    public void testFromWithLowerCase() {
        // Test with lowercase input
        assertEquals(MLAgentType.FLOW, MLAgentType.from("flow"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("conversational"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("conversational_flow"));
    }

    @Test
    public void testFromWithMixedCase() {
        // Test with mixed case input
        assertEquals(MLAgentType.FLOW, MLAgentType.from("Flow"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("Conversational"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("Conversational_Flow"));
    }

    @Test
    public void testFromWithInvalidType() {
        // This should throw an IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from("INVALID_TYPE"));
        assertEquals("Wrong Agent type", exception.getMessage());
    }

    @Test
    public void testFromWithEmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            // This should also throw an IllegalArgumentException
            MLAgentType.from("");
        });
        assertEquals("Wrong Agent type", exception.getMessage());
    }

    @Test
    public void testFromWithNull() {
        // This should also throw an IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from(null));
        assertEquals("Agent type can't be null", exception.getMessage());
    }
}
