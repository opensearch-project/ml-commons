/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MLAgentTypeTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(" is not a valid Agent Type");
        MLAgentType.from("INVALID_TYPE");
    }

    @Test
    public void testFromWithEmptyString() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(" is not a valid Agent Type");
        // This should also throw an IllegalArgumentException
        MLAgentType.from("");
    }

    @Test
    public void testFromWithNull() {
        // This should also throw an IllegalArgumentException
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Agent type can't be null");
        MLAgentType.from(null);
    }
}
