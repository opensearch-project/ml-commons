/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MLAgentTypeTest {

    @Test
    public void testFrom_ValidUpperCaseValues() {
        assertEquals(MLAgentType.FLOW, MLAgentType.from("FLOW"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("CONVERSATIONAL"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("CONVERSATIONAL_FLOW"));
        assertEquals(MLAgentType.PLAN_EXECUTE_AND_REFLECT, MLAgentType.from("PLAN_EXECUTE_AND_REFLECT"));
        assertEquals(MLAgentType.AG_UI, MLAgentType.from("AG_UI"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("CONVERSATIONAL_V2"));
    }

    @Test
    public void testFrom_ValidLowerCaseValues() {
        assertEquals(MLAgentType.FLOW, MLAgentType.from("flow"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("conversational"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("conversational_flow"));
        assertEquals(MLAgentType.PLAN_EXECUTE_AND_REFLECT, MLAgentType.from("plan_execute_and_reflect"));
        assertEquals(MLAgentType.AG_UI, MLAgentType.from("ag_ui"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("conversational_v2"));
    }

    @Test
    public void testFrom_ValidMixedCaseValues() {
        assertEquals(MLAgentType.FLOW, MLAgentType.from("Flow"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.from("Conversational"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.from("Conversational_Flow"));
        assertEquals(MLAgentType.PLAN_EXECUTE_AND_REFLECT, MLAgentType.from("Plan_Execute_And_Reflect"));
        assertEquals(MLAgentType.AG_UI, MLAgentType.from("Ag_Ui"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("Conversational_V2"));
    }

    @Test
    public void testFrom_NullValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from(null));
        assertEquals("Agent type can't be null", exception.getMessage());
    }

    @Test
    public void testFrom_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from("INVALID_TYPE"));
        assertEquals("Wrong Agent type", exception.getMessage());
    }

    @Test
    public void testFrom_EmptyString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from(""));
        assertEquals("Wrong Agent type", exception.getMessage());
    }

    @Test
    public void testFrom_WhitespaceString() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLAgentType.from("   "));
        assertEquals("Wrong Agent type", exception.getMessage());
    }

    @Test
    public void testIsV2_ConversationalV2_ReturnsTrue() {
        assertTrue(MLAgentType.CONVERSATIONAL_V2.isV2());
    }

    @Test
    public void testIsV2_FlowAgent_ReturnsFalse() {
        assertFalse(MLAgentType.FLOW.isV2());
    }

    @Test
    public void testIsV2_ConversationalAgent_ReturnsFalse() {
        assertFalse(MLAgentType.CONVERSATIONAL.isV2());
    }

    @Test
    public void testIsV2_ConversationalFlowAgent_ReturnsFalse() {
        assertFalse(MLAgentType.CONVERSATIONAL_FLOW.isV2());
    }

    @Test
    public void testIsV2_PlanExecuteAndReflectAgent_ReturnsFalse() {
        assertFalse(MLAgentType.PLAN_EXECUTE_AND_REFLECT.isV2());
    }

    @Test
    public void testIsV2_AGUIAgent_ReturnsFalse() {
        assertFalse(MLAgentType.AG_UI.isV2());
    }

    @Test
    public void testEnumValues() {
        MLAgentType[] types = MLAgentType.values();
        assertEquals(6, types.length);
        assertEquals(MLAgentType.FLOW, types[0]);
        assertEquals(MLAgentType.CONVERSATIONAL, types[1]);
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, types[2]);
        assertEquals(MLAgentType.PLAN_EXECUTE_AND_REFLECT, types[3]);
        assertEquals(MLAgentType.AG_UI, types[4]);
        assertEquals(MLAgentType.CONVERSATIONAL_V2, types[5]);
    }

    @Test
    public void testEnumName() {
        assertEquals("FLOW", MLAgentType.FLOW.name());
        assertEquals("CONVERSATIONAL", MLAgentType.CONVERSATIONAL.name());
        assertEquals("CONVERSATIONAL_FLOW", MLAgentType.CONVERSATIONAL_FLOW.name());
        assertEquals("PLAN_EXECUTE_AND_REFLECT", MLAgentType.PLAN_EXECUTE_AND_REFLECT.name());
        assertEquals("AG_UI", MLAgentType.AG_UI.name());
        assertEquals("CONVERSATIONAL_V2", MLAgentType.CONVERSATIONAL_V2.name());
    }

    @Test
    public void testEnumToString() {
        assertEquals("FLOW", MLAgentType.FLOW.toString());
        assertEquals("CONVERSATIONAL", MLAgentType.CONVERSATIONAL.toString());
        assertEquals("CONVERSATIONAL_FLOW", MLAgentType.CONVERSATIONAL_FLOW.toString());
        assertEquals("PLAN_EXECUTE_AND_REFLECT", MLAgentType.PLAN_EXECUTE_AND_REFLECT.toString());
        assertEquals("AG_UI", MLAgentType.AG_UI.toString());
        assertEquals("CONVERSATIONAL_V2", MLAgentType.CONVERSATIONAL_V2.toString());
    }

    @Test
    public void testEnumOrdinal() {
        assertEquals(0, MLAgentType.FLOW.ordinal());
        assertEquals(1, MLAgentType.CONVERSATIONAL.ordinal());
        assertEquals(2, MLAgentType.CONVERSATIONAL_FLOW.ordinal());
        assertEquals(3, MLAgentType.PLAN_EXECUTE_AND_REFLECT.ordinal());
        assertEquals(4, MLAgentType.AG_UI.ordinal());
        assertEquals(5, MLAgentType.CONVERSATIONAL_V2.ordinal());
    }

    @Test
    public void testValueOf() {
        assertEquals(MLAgentType.FLOW, MLAgentType.valueOf("FLOW"));
        assertEquals(MLAgentType.CONVERSATIONAL, MLAgentType.valueOf("CONVERSATIONAL"));
        assertEquals(MLAgentType.CONVERSATIONAL_FLOW, MLAgentType.valueOf("CONVERSATIONAL_FLOW"));
        assertEquals(MLAgentType.PLAN_EXECUTE_AND_REFLECT, MLAgentType.valueOf("PLAN_EXECUTE_AND_REFLECT"));
        assertEquals(MLAgentType.AG_UI, MLAgentType.valueOf("AG_UI"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.valueOf("CONVERSATIONAL_V2"));
    }

    @Test
    public void testValueOf_InvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> MLAgentType.valueOf("INVALID"));
    }

    @Test
    public void testValueOf_NullValue() {
        assertThrows(NullPointerException.class, () -> MLAgentType.valueOf(null));
    }

    @Test
    public void testFrom_CaseInsensitivity() {
        // Test various case combinations
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("conversational_v2"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("CONVERSATIONAL_V2"));
        assertEquals(MLAgentType.CONVERSATIONAL_V2, MLAgentType.from("CoNvErSaTiOnAl_V2"));
    }

    @Test
    public void testFrom_SpecialCharactersInvalid() {
        assertThrows(IllegalArgumentException.class, () -> MLAgentType.from("FLOW@#$"));
        assertThrows(IllegalArgumentException.class, () -> MLAgentType.from("CONVERSATIONAL!"));
    }

    @Test
    public void testIsV2_AllV1AgentTypes() {
        // Verify all V1 agent types return false for isV2()
        assertFalse("FLOW should be V1", MLAgentType.FLOW.isV2());
        assertFalse("CONVERSATIONAL should be V1", MLAgentType.CONVERSATIONAL.isV2());
        assertFalse("CONVERSATIONAL_FLOW should be V1", MLAgentType.CONVERSATIONAL_FLOW.isV2());
        assertFalse("PLAN_EXECUTE_AND_REFLECT should be V1", MLAgentType.PLAN_EXECUTE_AND_REFLECT.isV2());
        assertFalse("AG_UI should be V1", MLAgentType.AG_UI.isV2());
    }

    @Test
    public void testIsV2_OnlyV2AgentType() {
        // Verify only CONVERSATIONAL_V2 returns true for isV2()
        assertTrue("CONVERSATIONAL_V2 should be V2", MLAgentType.CONVERSATIONAL_V2.isV2());
    }
}
