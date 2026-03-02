/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ContextManagerUtilsTest {

    @Test
    public void testFindSafePoint_EmptyList() {
        List<String> interactions = Collections.emptyList();
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, false));
    }

    @Test
    public void testFindSafePoint_BoundaryConditions() {
        List<String> interactions = Arrays.asList("message1", "message2", "message3");

        // Start point boundary conditions
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, -1, true));
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(3, ContextManagerUtils.findSafePoint(interactions, 5, true));

        // Cut point boundary conditions
        assertEquals(5, ContextManagerUtils.findSafePoint(interactions, 5, false));
    }

    @Test
    public void testFindSafePoint_RegularMessages() {
        List<String> interactions = Arrays.asList("user: hello", "assistant: hi", "user: how are you?");

        // Regular messages should be safe - should return the target point
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(1, ContextManagerUtils.findSafePoint(interactions, 1, true));
        assertEquals(2, ContextManagerUtils.findSafePoint(interactions, 2, true));
    }

    @Test
    public void testFindSafePoint_ReturnsValidIndex() {
        List<String> interactions = Arrays
            .asList("toolResult: result1", "toolUse: call1", "user: hello", "toolUse: call2", "toolResult: result2");

        // Should return a valid index within bounds
        int result = ContextManagerUtils.findSafePoint(interactions, 0, true);
        assertTrue("Result should be >= 0", result >= 0);
        assertTrue("Result should be <= size", result <= interactions.size());

        // Test both start point and cut point modes
        int resultCut = ContextManagerUtils.findSafePoint(interactions, 0, false);
        assertTrue("Cut point result should be >= 0", resultCut >= 0);
        assertTrue("Cut point result should be <= size", resultCut <= interactions.size());
    }

    @Test
    public void testFindSafePoint_MethodExists() {
        // Basic test to ensure the method exists and can be called
        List<String> interactions = Arrays.asList("test message");

        // Should not throw exception
        int result1 = ContextManagerUtils.findSafePoint(interactions, 0, true);
        int result2 = ContextManagerUtils.findSafePoint(interactions, 0, false);

        // Results should be reasonable
        assertTrue("Start point result should be valid", result1 >= 0 && result1 <= interactions.size());
        assertTrue("Cut point result should be valid", result2 >= 0 && result2 <= interactions.size());
    }
}
