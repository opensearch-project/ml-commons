/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TokenUsageTest {

    @Test
    public void testBuilder() {
        TokenUsage usage = TokenUsage
            .builder()
            .inputTokens(100L)
            .outputTokens(50L)
            .totalTokens(150L)
            .cacheReadInputTokens(20L)
            .cacheCreationInputTokens(10L)
            .reasoningTokens(5L)
            .build();

        assertEquals(Long.valueOf(100L), usage.getInputTokens());
        assertEquals(Long.valueOf(50L), usage.getOutputTokens());
        assertEquals(Long.valueOf(150L), usage.getTotalTokens());
        assertEquals(Long.valueOf(20L), usage.getCacheReadInputTokens());
        assertEquals(Long.valueOf(10L), usage.getCacheCreationInputTokens());
        assertEquals(Long.valueOf(5L), usage.getReasoningTokens());
    }

    @Test
    public void testBuilderWithNullValues() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).build();

        assertEquals(Long.valueOf(100L), usage.getInputTokens());
        assertEquals(Long.valueOf(50L), usage.getOutputTokens());
        assertNull(usage.getTotalTokens());
        assertNull(usage.getCacheReadInputTokens());
        assertNull(usage.getCacheCreationInputTokens());
        assertNull(usage.getReasoningTokens());
    }

    @Test
    public void testAddTokens() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();

        TokenUsage usage2 = TokenUsage.builder().inputTokens(200L).outputTokens(75L).totalTokens(275L).build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(300L), combined.getInputTokens());
        assertEquals(Long.valueOf(125L), combined.getOutputTokens());
        assertEquals(Long.valueOf(425L), combined.getTotalTokens());
    }

    @Test
    public void testAddTokensWithNulls() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).build();

        TokenUsage usage2 = TokenUsage.builder().outputTokens(50L).build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(100L), combined.getInputTokens());
        assertEquals(Long.valueOf(50L), combined.getOutputTokens());
        assertNull(combined.getTotalTokens());
    }

    @Test
    public void testAddTokensWithNull() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).build();

        TokenUsage result = usage.addTokens(null);

        assertSame(usage, result);
    }

    @Test
    public void testAddTokensWithAdditionalUsage() {
        Map<String, Long> additional1 = new HashMap<>();
        additional1.put("audio_tokens", 10L);

        Map<String, Long> additional2 = new HashMap<>();
        additional2.put("audio_tokens", 5L);
        additional2.put("video_tokens", 20L);

        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).additionalUsage(additional1).build();

        TokenUsage usage2 = TokenUsage.builder().inputTokens(50L).additionalUsage(additional2).build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(150L), combined.getInputTokens());
        assertEquals(Long.valueOf(15L), combined.getAdditionalUsage().get("audio_tokens"));
        assertEquals(Long.valueOf(20L), combined.getAdditionalUsage().get("video_tokens"));
    }

    @Test
    public void testGetEffectiveTotalTokens() {
        // Test when totalTokens is provided
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();
        assertEquals(Long.valueOf(150L), usage1.getEffectiveTotalTokens());

        // Test when totalTokens is computed
        TokenUsage usage2 = TokenUsage.builder().inputTokens(100L).outputTokens(50L).build();
        assertEquals(Long.valueOf(150L), usage2.getEffectiveTotalTokens());

        // Test when input or output is missing
        TokenUsage usage3 = TokenUsage.builder().inputTokens(100L).build();
        assertNull(usage3.getEffectiveTotalTokens());
    }

    @Test
    public void testToMap() {
        TokenUsage usage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).cacheReadInputTokens(20L).build();

        Map<String, Object> map = usage.toMap();

        assertEquals(100L, map.get("input_tokens"));
        assertEquals(50L, map.get("output_tokens"));
        assertEquals(150L, map.get("total_tokens"));
        assertEquals(20L, map.get("cache_read_input_tokens"));
        assertFalse(map.containsKey("cache_creation_input_tokens"));
        assertFalse(map.containsKey("reasoning_tokens"));
    }

    @Test
    public void testToMap_allFieldsPopulated() {
        Map<String, Long> additional = new HashMap<>();
        additional.put("audio_tokens", 10L);

        TokenUsage usage = TokenUsage
            .builder()
            .inputTokens(100L)
            .outputTokens(50L)
            .totalTokens(150L)
            .cacheReadInputTokens(20L)
            .cacheCreationInputTokens(15L)
            .reasoningTokens(30L)
            .additionalUsage(additional)
            .build();

        Map<String, Object> map = usage.toMap();

        assertEquals(100L, map.get("input_tokens"));
        assertEquals(50L, map.get("output_tokens"));
        assertEquals(150L, map.get("total_tokens"));
        assertEquals(20L, map.get("cache_read_input_tokens"));
        assertEquals(15L, map.get("cache_creation_input_tokens"));
        assertEquals(30L, map.get("reasoning_tokens"));
        assertEquals(10L, map.get("audio_tokens"));
    }

    @Test
    public void testToMap_emptyUsage() {
        TokenUsage usage = TokenUsage.builder().build();

        Map<String, Object> map = usage.toMap();

        assertTrue(map.isEmpty());
    }

    @Test
    public void testAddTokens_withCacheAndReasoningTokens() {
        TokenUsage usage1 = TokenUsage
            .builder()
            .inputTokens(100L)
            .outputTokens(50L)
            .cacheReadInputTokens(10L)
            .cacheCreationInputTokens(5L)
            .reasoningTokens(20L)
            .build();

        TokenUsage usage2 = TokenUsage
            .builder()
            .inputTokens(200L)
            .outputTokens(75L)
            .cacheReadInputTokens(15L)
            .cacheCreationInputTokens(8L)
            .reasoningTokens(30L)
            .build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(300L), combined.getInputTokens());
        assertEquals(Long.valueOf(125L), combined.getOutputTokens());
        assertEquals(Long.valueOf(25L), combined.getCacheReadInputTokens());
        assertEquals(Long.valueOf(13L), combined.getCacheCreationInputTokens());
        assertEquals(Long.valueOf(50L), combined.getReasoningTokens());
    }

    @Test
    public void testAddTokens_oneSideNull() {
        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).cacheReadInputTokens(10L).reasoningTokens(20L).build();

        TokenUsage usage2 = TokenUsage.builder().outputTokens(50L).build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(100L), combined.getInputTokens());
        assertEquals(Long.valueOf(50L), combined.getOutputTokens());
        assertEquals(Long.valueOf(10L), combined.getCacheReadInputTokens());
        assertNull(combined.getCacheCreationInputTokens());
        assertEquals(Long.valueOf(20L), combined.getReasoningTokens());
    }

    @Test
    public void testAddTokens_additionalUsageOnlyOnOneSide() {
        Map<String, Long> additional = new HashMap<>();
        additional.put("audio_tokens", 10L);

        TokenUsage usage1 = TokenUsage.builder().inputTokens(100L).additionalUsage(additional).build();
        TokenUsage usage2 = TokenUsage.builder().inputTokens(50L).build();

        TokenUsage combined = usage1.addTokens(usage2);

        assertEquals(Long.valueOf(150L), combined.getInputTokens());
        assertEquals(Long.valueOf(10L), combined.getAdditionalUsage().get("audio_tokens"));
    }

    @Test
    public void testGetEffectiveTotalTokens_onlyOutputTokens() {
        TokenUsage usage = TokenUsage.builder().outputTokens(50L).build();
        assertNull(usage.getEffectiveTotalTokens());
    }

    @Test
    public void testAdditionalUsage_defaultsToEmptyMap() {
        TokenUsage usage = TokenUsage.builder().build();
        assertNotNull(usage.getAdditionalUsage());
        assertTrue(usage.getAdditionalUsage().isEmpty());
    }

}
