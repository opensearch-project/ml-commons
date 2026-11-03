/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * Represents token usage information from LLM API calls.
 * Supports multiple providers (OpenAI, Anthropic Claude, Bedrock, Gemini) including
 * cache tokens and reasoning tokens.
 */
@Getter
public class TokenUsage {

    // Field name constants
    public static final String INPUT_TOKENS = "input_tokens";
    public static final String OUTPUT_TOKENS = "output_tokens";
    public static final String TOTAL_TOKENS = "total_tokens";
    public static final String CACHE_READ_INPUT_TOKENS = "cache_read_input_tokens";
    public static final String CACHE_CREATION_INPUT_TOKENS = "cache_creation_input_tokens";
    public static final String REASONING_TOKENS = "reasoning_tokens";

    // Core token counts (all providers)
    private final Long inputTokens;           // prompt tokens
    private final Long outputTokens;          // completion/candidate tokens
    private final Long totalTokens;           // sum (computed if not provided)

    // Cache tokens (Anthropic, OpenAI, Gemini)
    private final Long cacheReadInputTokens;     // tokens served from cache
    private final Long cacheCreationInputTokens; // tokens used to create cache

    // Extended tokens
    private final Long reasoningTokens;          // thinking tokens (OpenAI o1, Gemini)

    // Provider-specific additional fields
    private final Map<String, Long> additionalUsage;

    /**
     * Constructor for builder pattern
     */
    @lombok.Builder
    public TokenUsage(
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Long cacheReadInputTokens,
        Long cacheCreationInputTokens,
        Long reasoningTokens,
        Map<String, Long> additionalUsage
    ) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cacheReadInputTokens = cacheReadInputTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.reasoningTokens = reasoningTokens;
        this.additionalUsage = additionalUsage != null ? additionalUsage : new HashMap<>();
    }

    /**
     * Adds tokens from another TokenUsage instance to this one.
     * Used for aggregating usage across multiple API calls.
     *
     * @param other The TokenUsage to add
     * @return A new TokenUsage with aggregated values
     */
    public TokenUsage addTokens(TokenUsage other) {
        if (other == null) {
            return this;
        }

        Map<String, Long> mergedAdditional = new HashMap<>(this.additionalUsage);
        if (other.additionalUsage != null) {
            other.additionalUsage.forEach((key, value) -> mergedAdditional.merge(key, value, Long::sum));
        }

        return TokenUsage
            .builder()
            .inputTokens(addNullable(this.inputTokens, other.inputTokens))
            .outputTokens(addNullable(this.outputTokens, other.outputTokens))
            .totalTokens(addNullable(this.totalTokens, other.totalTokens))
            .cacheReadInputTokens(addNullable(this.cacheReadInputTokens, other.cacheReadInputTokens))
            .cacheCreationInputTokens(addNullable(this.cacheCreationInputTokens, other.cacheCreationInputTokens))
            .reasoningTokens(addNullable(this.reasoningTokens, other.reasoningTokens))
            .additionalUsage(mergedAdditional)
            .build();
    }

    /**
     * Converts TokenUsage to a Map suitable for JSON serialization in agent responses
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (inputTokens != null) {
            map.put(INPUT_TOKENS, inputTokens);
        }
        if (outputTokens != null) {
            map.put(OUTPUT_TOKENS, outputTokens);
        }
        if (totalTokens != null) {
            map.put(TOTAL_TOKENS, totalTokens);
        }
        if (cacheReadInputTokens != null) {
            map.put(CACHE_READ_INPUT_TOKENS, cacheReadInputTokens);
        }
        if (cacheCreationInputTokens != null) {
            map.put(CACHE_CREATION_INPUT_TOKENS, cacheCreationInputTokens);
        }
        if (reasoningTokens != null) {
            map.put(REASONING_TOKENS, reasoningTokens);
        }
        if (additionalUsage != null && !additionalUsage.isEmpty()) {
            map.putAll(additionalUsage);
        }
        return map;
    }

    /**
     * Computes total tokens if not provided by summing input and output tokens
     */
    public Long getEffectiveTotalTokens() {
        if (totalTokens != null) {
            return totalTokens;
        }
        if (inputTokens != null && outputTokens != null) {
            return inputTokens + outputTokens;
        }
        return null;
    }

    private static Long addNullable(Long a, Long b) {
        return a == null ? b : b == null ? a : Long.valueOf(a + b);
    }
}
