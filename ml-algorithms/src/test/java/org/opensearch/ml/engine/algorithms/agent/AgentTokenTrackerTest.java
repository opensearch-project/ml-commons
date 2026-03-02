/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.agent.TokenUsage;

public class AgentTokenTrackerTest {

    private AgentTokenTracker tracker;

    @Before
    public void setUp() {
        tracker = new AgentTokenTracker();
    }

    @Test
    public void testRecordSingleTurn() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();

        tracker.setModelMetadata("gpt-4", "https://api.openai.com/v1/chat/completions", "gpt-4");
        tracker.recordTurn("gpt-4", usage);

        assertTrue(tracker.hasUsage());

        Map<String, Object> output = tracker.toOutputMap();
        assertNotNull(output);

        List<Map<String, Object>> perTurnUsage = (List<Map<String, Object>>) output.get("per_turn_usage");
        assertEquals(1, perTurnUsage.size());

        Map<String, Object> turn = perTurnUsage.get(0);
        assertEquals(1, turn.get("turn"));
        assertEquals("gpt-4", turn.get("model_name"));
        assertEquals(100L, turn.get("input_tokens"));
        assertEquals(50L, turn.get("output_tokens"));
        assertEquals(150L, turn.get("total_tokens"));
    }

    @Test
    public void testRecordMultipleTurns() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();

        TokenUsage usage2 = TokenUsage.builder().inputTokens(200L).outputTokens(75L).totalTokens(275L).build();

        TokenUsage usage3 = TokenUsage.builder().inputTokens(150L).outputTokens(60L).totalTokens(210L).build();

        tracker.setModelMetadata("gpt-4", "https://api.openai.com/v1/chat/completions", "gpt-4");
        tracker.recordTurn("gpt-4", usage1);
        tracker.recordTurn("gpt-4", usage2);
        tracker.recordTurn("gpt-4", usage3);

        Map<String, Object> output = tracker.toOutputMap();

        List<Map<String, Object>> perTurnUsage = (List<Map<String, Object>>) output.get("per_turn_usage");
        assertEquals(3, perTurnUsage.size());

        // Verify turn numbers
        assertEquals(1, perTurnUsage.get(0).get("turn"));
        assertEquals(2, perTurnUsage.get(1).get("turn"));
        assertEquals(3, perTurnUsage.get(2).get("turn"));
    }

    @Test
    public void testPerModelAggregation() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();

        TokenUsage usage2 = TokenUsage.builder().inputTokens(200L).outputTokens(75L).totalTokens(275L).build();

        tracker.recordTurn("gpt-4", usage1);
        tracker.recordTurn("gpt-4", usage2);

        Map<String, Object> output = tracker.toOutputMap();

        List<Map<String, Object>> perModelUsage = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(1, perModelUsage.size());

        Map<String, Object> modelData = perModelUsage.get(0);
        assertEquals("gpt-4", modelData.get("model_name"));
        assertEquals(300L, modelData.get("input_tokens"));
        assertEquals(125L, modelData.get("output_tokens"));
        assertEquals(425L, modelData.get("total_tokens"));
        assertEquals(2, modelData.get("call_count"));
    }

    @Test
    public void testMultipleModels() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).build();

        TokenUsage usage2 = TokenUsage.builder().inputTokens(200L).outputTokens(75L).build();

        TokenUsage usage3 = TokenUsage.builder().inputTokens(150L).outputTokens(60L).build();

        tracker.setModelMetadata("gpt-4", "https://api.openai.com/v1/chat/completions", "gpt-4");
        tracker.setModelMetadata("claude-3", "https://api.anthropic.com/v1/messages", "claude-3");
        tracker.recordTurn("gpt-4", usage1);
        tracker.recordTurn("claude-3", usage2);
        tracker.recordTurn("gpt-4", usage3);

        Map<String, Object> output = tracker.toOutputMap();

        List<Map<String, Object>> perModelUsage = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(2, perModelUsage.size());

        // Find each model's data
        Map<String, Object> gpt4Data = perModelUsage.stream().filter(m -> "gpt-4".equals(m.get("model_id"))).findFirst().orElse(null);
        Map<String, Object> claudeData = perModelUsage.stream().filter(m -> "claude-3".equals(m.get("model_id"))).findFirst().orElse(null);

        assertNotNull(gpt4Data);
        assertNotNull(claudeData);

        assertEquals(250L, gpt4Data.get("input_tokens"));
        assertEquals(110L, gpt4Data.get("output_tokens"));
        assertEquals(2, gpt4Data.get("call_count"));

        assertEquals(200L, claudeData.get("input_tokens"));
        assertEquals(75L, claudeData.get("output_tokens"));
        assertEquals(1, claudeData.get("call_count"));
    }

    @Test
    public void testRecordWithCacheTokens() {
        TokenUsage usage = TokenUsage
            .builder()
            .inputTokens(100L)
            .outputTokens(50L)
            .totalTokens(150L)
            .cacheReadInputTokens(20L)
            .cacheCreationInputTokens(10L)
            .build();

        tracker.setModelMetadata("claude-3", "https://api.anthropic.com/v1/messages", "claude-3");
        tracker.recordTurn("claude-3", usage);

        Map<String, Object> output = tracker.toOutputMap();
        List<Map<String, Object>> perTurnUsage = (List<Map<String, Object>>) output.get("per_turn_usage");

        Map<String, Object> turn = perTurnUsage.get(0);
        assertEquals(20L, turn.get("cache_read_input_tokens"));
        assertEquals(10L, turn.get("cache_creation_input_tokens"));
    }

    @Test
    public void testRecordWithReasoningTokens() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).reasoningTokens(30L).build();

        tracker.setModelMetadata("o1-preview", "https://api.openai.com/v1/chat/completions", "o1-preview");
        tracker.recordTurn("o1-preview", usage);

        Map<String, Object> output = tracker.toOutputMap();
        List<Map<String, Object>> perTurnUsage = (List<Map<String, Object>>) output.get("per_turn_usage");

        Map<String, Object> turn = perTurnUsage.get(0);
        assertEquals(30L, turn.get("reasoning_tokens"));
    }

    @Test
    public void testRecordWithNullModelName() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).build();

        tracker.recordTurn(null, usage);

        assertFalse(tracker.hasUsage());
    }

    @Test
    public void testRecordWithNullUsage() {
        tracker.recordTurn("gpt-4", null);

        assertFalse(tracker.hasUsage());
    }

    @Test
    public void testHasUsageWhenEmpty() {
        assertFalse(tracker.hasUsage());
    }

    @Test
    public void testEmptyOutputMap() {
        Map<String, Object> output = tracker.toOutputMap();

        assertNotNull(output);
        assertTrue(((List<?>) output.get("per_turn_usage")).isEmpty());
        assertTrue(((List<?>) output.get("per_model_usage")).isEmpty());
    }

    // --- mergeSubAgentUsage tests ---

    @Test
    public void testMergeSubAgentUsage_basic() {
        Map<String, Object> subAgentUsage = new HashMap<>();
        subAgentUsage
            .put(
                "per_turn_usage",
                List
                    .of(
                        Map
                            .of(
                                "turn",
                                1,
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                100L,
                                "output_tokens",
                                50L,
                                "total_tokens",
                                150L
                            ),
                        Map
                            .of(
                                "turn",
                                2,
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                200L,
                                "output_tokens",
                                75L,
                                "total_tokens",
                                275L
                            )
                    )
            );
        subAgentUsage
            .put(
                "per_model_usage",
                List
                    .of(
                        Map
                            .of(
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                300L,
                                "output_tokens",
                                125L,
                                "total_tokens",
                                425L,
                                "call_count",
                                2
                            )
                    )
            );

        tracker.mergeSubAgentUsage(subAgentUsage);

        assertTrue(tracker.hasUsage());
        Map<String, Object> output = tracker.toOutputMap();

        List<Map<String, Object>> perTurn = (List<Map<String, Object>>) output.get("per_turn_usage");
        assertEquals(2, perTurn.size());
        assertEquals(1, perTurn.get(0).get("turn"));
        assertEquals(2, perTurn.get(1).get("turn"));

        List<Map<String, Object>> perModel = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(1, perModel.size());
        assertEquals(300L, perModel.get(0).get("input_tokens"));
        assertEquals(2, perModel.get(0).get("call_count"));
    }

    @Test
    public void testMergeSubAgentUsage_afterExistingTurns() {
        // Record 2 direct planner turns first
        tracker.setModelMetadata("gpt-4", "https://openai", "gpt-4");
        tracker.recordTurn("gpt-4", TokenUsage.builder().inputTokens(50L).outputTokens(25L).totalTokens(75L).build());
        tracker.recordTurn("gpt-4", TokenUsage.builder().inputTokens(60L).outputTokens(30L).totalTokens(90L).build());

        // Merge sub-agent usage with 3 turns
        Map<String, Object> subAgentUsage = new HashMap<>();
        subAgentUsage
            .put(
                "per_turn_usage",
                List
                    .of(
                        Map.of("turn", 1, "model_id", "claude-3", "input_tokens", 100L, "output_tokens", 50L, "total_tokens", 150L),
                        Map.of("turn", 2, "model_id", "claude-3", "input_tokens", 200L, "output_tokens", 75L, "total_tokens", 275L),
                        Map.of("turn", 3, "model_id", "claude-3", "input_tokens", 150L, "output_tokens", 60L, "total_tokens", 210L)
                    )
            );
        subAgentUsage
            .put(
                "per_model_usage",
                List
                    .of(
                        Map
                            .of(
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                450L,
                                "output_tokens",
                                185L,
                                "total_tokens",
                                635L,
                                "call_count",
                                3
                            )
                    )
            );

        tracker.mergeSubAgentUsage(subAgentUsage);

        Map<String, Object> output = tracker.toOutputMap();
        List<Map<String, Object>> perTurn = (List<Map<String, Object>>) output.get("per_turn_usage");
        assertEquals(5, perTurn.size());

        // Planner turns keep 1, 2; sub-agent turns re-numbered to 3, 4, 5
        assertEquals(1, perTurn.get(0).get("turn"));
        assertEquals(2, perTurn.get(1).get("turn"));
        assertEquals(3, perTurn.get(2).get("turn"));
        assertEquals(4, perTurn.get(3).get("turn"));
        assertEquals(5, perTurn.get(4).get("turn"));

        // Two separate models in per_model_usage
        List<Map<String, Object>> perModel = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(2, perModel.size());
    }

    @Test
    public void testMergeSubAgentUsage_sameModel() {
        // Planner uses same model as executor
        tracker.setModelMetadata("claude-3", "https://bedrock", "claude-3");
        tracker.recordTurn("claude-3", TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build());

        Map<String, Object> subAgentUsage = new HashMap<>();
        subAgentUsage
            .put(
                "per_turn_usage",
                List.of(Map.of("turn", 1, "model_id", "claude-3", "input_tokens", 200L, "output_tokens", 75L, "total_tokens", 275L))
            );
        subAgentUsage
            .put(
                "per_model_usage",
                List
                    .of(
                        Map
                            .of(
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                200L,
                                "output_tokens",
                                75L,
                                "total_tokens",
                                275L,
                                "call_count",
                                1
                            )
                    )
            );

        tracker.mergeSubAgentUsage(subAgentUsage);

        Map<String, Object> output = tracker.toOutputMap();
        List<Map<String, Object>> perModel = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(1, perModel.size());

        // Aggregated: 100+200=300 input, 50+75=125 output, 150+275=425 total, 1+1=2 calls
        assertEquals(300L, perModel.get(0).get("input_tokens"));
        assertEquals(125L, perModel.get(0).get("output_tokens"));
        assertEquals(425L, perModel.get(0).get("total_tokens"));
        assertEquals(2, perModel.get(0).get("call_count"));
    }

    @Test
    public void testMergeSubAgentUsage_nullInput() {
        tracker.mergeSubAgentUsage(null);
        assertFalse(tracker.hasUsage());
    }

    @Test
    public void testMergeSubAgentUsage_emptyMap() {
        tracker.mergeSubAgentUsage(new HashMap<>());
        assertFalse(tracker.hasUsage());
    }

    @Test
    public void testMergeSubAgentUsage_multipleSteps() {
        // Simulate PER executing 2 steps, each executor returns token usage
        tracker.setModelMetadata("gpt-4", "https://openai", "gpt-4");

        // Planner call 1
        tracker.recordTurn("gpt-4", TokenUsage.builder().inputTokens(50L).outputTokens(25L).totalTokens(75L).build());

        // Executor step 1
        Map<String, Object> step1Usage = new HashMap<>();
        step1Usage
            .put(
                "per_turn_usage",
                List
                    .of(
                        Map
                            .of(
                                "turn",
                                1,
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                100L,
                                "output_tokens",
                                50L,
                                "total_tokens",
                                150L
                            )
                    )
            );
        step1Usage
            .put(
                "per_model_usage",
                List
                    .of(
                        Map
                            .of(
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                100L,
                                "output_tokens",
                                50L,
                                "total_tokens",
                                150L,
                                "call_count",
                                1
                            )
                    )
            );
        tracker.mergeSubAgentUsage(step1Usage);

        // Planner reflect call
        tracker.recordTurn("gpt-4", TokenUsage.builder().inputTokens(80L).outputTokens(30L).totalTokens(110L).build());

        // Executor step 2
        Map<String, Object> step2Usage = new HashMap<>();
        step2Usage
            .put(
                "per_turn_usage",
                List
                    .of(
                        Map
                            .of(
                                "turn",
                                1,
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                200L,
                                "output_tokens",
                                75L,
                                "total_tokens",
                                275L
                            ),
                        Map
                            .of(
                                "turn",
                                2,
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                150L,
                                "output_tokens",
                                60L,
                                "total_tokens",
                                210L
                            )
                    )
            );
        step2Usage
            .put(
                "per_model_usage",
                List
                    .of(
                        Map
                            .of(
                                "model_id",
                                "claude-3",
                                "model_name",
                                "claude-3",
                                "model_url",
                                "https://bedrock",
                                "input_tokens",
                                350L,
                                "output_tokens",
                                135L,
                                "total_tokens",
                                485L,
                                "call_count",
                                2
                            )
                    )
            );
        tracker.mergeSubAgentUsage(step2Usage);

        Map<String, Object> output = tracker.toOutputMap();

        // 1 planner + 1 executor step1 + 1 planner reflect + 2 executor step2 = 5 turns
        List<Map<String, Object>> perTurn = (List<Map<String, Object>>) output.get("per_turn_usage");
        assertEquals(5, perTurn.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, perTurn.get(i).get("turn"));
        }

        // 2 models: gpt-4 (planner) and claude-3 (executor)
        List<Map<String, Object>> perModel = (List<Map<String, Object>>) output.get("per_model_usage");
        assertEquals(2, perModel.size());

        Map<String, Object> gpt4 = perModel.stream().filter(m -> "gpt-4".equals(m.get("model_id"))).findFirst().orElse(null);
        Map<String, Object> claude3 = perModel.stream().filter(m -> "claude-3".equals(m.get("model_id"))).findFirst().orElse(null);

        assertNotNull(gpt4);
        assertEquals(130L, gpt4.get("input_tokens")); // 50+80
        assertEquals(55L, gpt4.get("output_tokens"));  // 25+30
        assertEquals(2, gpt4.get("call_count"));

        assertNotNull(claude3);
        assertEquals(450L, claude3.get("input_tokens")); // 100+350
        assertEquals(185L, claude3.get("output_tokens")); // 50+135
        assertEquals(3, claude3.get("call_count"));        // 1+2
    }

    @Test
    public void testSubAgentFlag_defaultFalse() {
        assertFalse(tracker.isSubAgent());
    }

    @Test
    public void testSubAgentFlag_setTrue() {
        tracker.setSubAgent(true);
        assertTrue(tracker.isSubAgent());
    }
}
