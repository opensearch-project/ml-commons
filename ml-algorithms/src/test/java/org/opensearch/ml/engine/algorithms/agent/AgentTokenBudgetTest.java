/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.agent.TokenUsage;

public class AgentTokenBudgetTest {

    @Test
    public void testFromExecutionParams_PositiveBudget() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "500"));

        assertTrue(budget.isLimited());
        assertEquals(500L, budget.getMaxTokens());
    }

    @Test
    public void testFromExecutionParams_MissingIsUnlimited() {
        assertFalse(AgentTokenBudget.fromExecutionParams(Map.of()).isLimited());
    }

    @Test
    public void testFromExecutionParams_InvalidExplicitValuesThrow() {
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "invalid")));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "0")));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "-1")));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", " 100 ")));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100.5")));
        assertThrows(IllegalArgumentException.class, () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "1e10")));
        assertThrows(
            IllegalArgumentException.class,
            () -> AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "9999999999999999999"))
        );
    }

    @Test
    public void testRemainingAndExhaustionUseEffectiveTotalTokens() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100"));
        TokenUsage usage = TokenUsage.builder().inputTokens(20L).outputTokens(30L).build();

        assertEquals(50L, budget.remaining(usage));
        assertFalse(budget.isExhausted(usage));

        TokenUsage exhaustedUsage = TokenUsage.builder().totalTokens(100L).build();
        assertEquals(0L, budget.remaining(exhaustedUsage));
        assertTrue(budget.isExhausted(exhaustedUsage));
    }

    @Test
    public void testApplyRemainingToParams_WritesAllProviderLimitFields() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100"));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");

        budget.applyRemainingToParams(params, null, 25L);

        assertEquals("75", params.get("max_tokens"));
        assertEquals("75", params.get("maxTokens"));
        assertEquals("75", params.get("maxOutputTokens"));
    }

    @Test
    public void testApplyRemainingToParams_ClampsToSmallerExistingLimit() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100"));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");
        params.put("max_tokens", "100");
        Map<String, String> llmSpecParams = Map.of("max_tokens", "30");

        budget.applyRemainingToParams(params, llmSpecParams, 10L);

        assertEquals("30", params.get("max_tokens"));
        assertEquals("30", params.get("maxTokens"));
        assertEquals("30", params.get("maxOutputTokens"));
    }

    @Test
    public void testApplyRemainingToParams_UsesRemainingWhenSmallerThanExistingLimit() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100"));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");
        params.put("max_tokens", "80");

        budget.applyRemainingToParams(params, null, 75L);

        assertEquals("25", params.get("max_tokens"));
        assertEquals("25", params.get("maxTokens"));
        assertEquals("25", params.get("maxOutputTokens"));
    }

    @Test
    public void testApplyRemainingToParams_RemainingZeroDoesNotWriteUnconstrainedLimit() {
        AgentTokenBudget budget = AgentTokenBudget.fromExecutionParams(Map.of("max_tokens", "100"));
        Map<String, String> params = new HashMap<>();

        budget.applyRemainingToParams(params, null, 100L);

        assertFalse(params.containsKey("max_tokens"));
        assertFalse(params.containsKey("maxTokens"));
        assertFalse(params.containsKey("maxOutputTokens"));
    }
}
