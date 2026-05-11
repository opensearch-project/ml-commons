/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.ml.common.agent.TokenUsage;

import lombok.extern.log4j.Log4j2;

@Log4j2
class AgentTokenBudget {

    static final String MAX_TOKENS_FIELD = "max_tokens";
    static final String BEDROCK_MAX_TOKENS_FIELD = "maxTokens";
    static final String GEMINI_MAX_OUTPUT_TOKENS_FIELD = "maxOutputTokens";
    static final long UNLIMITED = -1L;

    private static final List<String> OUTPUT_LIMIT_FIELDS = List
        .of(MAX_TOKENS_FIELD, BEDROCK_MAX_TOKENS_FIELD, GEMINI_MAX_OUTPUT_TOKENS_FIELD);

    private final long maxTokens;

    private AgentTokenBudget(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    static AgentTokenBudget fromExecutionParams(Map<String, String> params) {
        if (params == null) {
            return unlimited();
        }

        String maxTokensStr = params.get(MAX_TOKENS_FIELD);
        if (maxTokensStr != null) {
            try {
                long value = Long.parseLong(maxTokensStr);
                if (value > 0) {
                    return new AgentTokenBudget(value);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid max_tokens value: {}", maxTokensStr);
            }
        }
        return unlimited();
    }

    static AgentTokenBudget unlimited() {
        return new AgentTokenBudget(UNLIMITED);
    }

    long getMaxTokens() {
        return maxTokens;
    }

    boolean isLimited() {
        return maxTokens > 0;
    }

    boolean isExhausted(long consumedTokens) {
        return isLimited() && consumedTokens >= maxTokens;
    }

    boolean isExhausted(TokenUsage tokenUsage) {
        return isExhausted(consumedTokens(tokenUsage));
    }

    boolean isExhausted(AgentTokenTracker tokenTracker) {
        return isExhausted(consumedTokens(tokenTracker));
    }

    long remaining(long consumedTokens) {
        if (!isLimited()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, maxTokens - consumedTokens);
    }

    long remaining(TokenUsage tokenUsage) {
        return remaining(consumedTokens(tokenUsage));
    }

    long remaining(AgentTokenTracker tokenTracker) {
        return remaining(consumedTokens(tokenTracker));
    }

    void applyRemainingToParams(Map<String, String> targetParams, Map<String, String> llmSpecParams, long consumedTokens) {
        if (!isLimited() || targetParams == null) {
            return;
        }

        long remaining = remaining(consumedTokens);
        if (remaining <= 0) {
            return;
        }

        long effectiveLimit = remaining;
        Long existingLimit = smallestPositiveOutputLimit(targetParams, llmSpecParams);
        if (existingLimit != null) {
            effectiveLimit = Math.min(existingLimit, remaining);
        }

        targetParams.put(outputLimitField(targetParams), Long.toString(effectiveLimit));
    }

    void applyRemainingToParams(Map<String, String> targetParams, Map<String, String> llmSpecParams, TokenUsage tokenUsage) {
        applyRemainingToParams(targetParams, llmSpecParams, consumedTokens(tokenUsage));
    }

    void applyRemainingToParams(Map<String, String> targetParams, Map<String, String> llmSpecParams, AgentTokenTracker tokenTracker) {
        applyRemainingToParams(targetParams, llmSpecParams, consumedTokens(tokenTracker));
    }

    String exhaustedMessage(long consumedTokens) {
        return String.format(Locale.ROOT, "Agent execution stopped: token budget exhausted (%d/%d tokens used)", consumedTokens, maxTokens);
    }

    String exhaustedMessage(TokenUsage tokenUsage) {
        return exhaustedMessage(consumedTokens(tokenUsage));
    }

    String exhaustedMessage(AgentTokenTracker tokenTracker) {
        return exhaustedMessage(consumedTokens(tokenTracker));
    }

    static long consumedTokens(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return 0L;
        }
        Long consumed = tokenUsage.getEffectiveTotalTokens();
        return consumed == null ? 0L : consumed;
    }

    static long consumedTokens(AgentTokenTracker tokenTracker) {
        return tokenTracker == null ? 0L : tokenTracker.getCumulativeTotalTokens();
    }

    private static Long smallestPositiveOutputLimit(Map<String, String> targetParams, Map<String, String> llmSpecParams) {
        Long smallest = null;
        for (String field : OUTPUT_LIMIT_FIELDS) {
            smallest = minPositive(smallest, parsePositiveLong(llmSpecParams, field));
            smallest = minPositive(smallest, parsePositiveLong(targetParams, field));
        }
        return smallest;
    }

    private static Long minPositive(Long current, Long candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.min(current, candidate);
    }

    private static Long parsePositiveLong(Map<String, String> params, String field) {
        if (params == null) {
            return null;
        }
        String value = params.get(field);
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String outputLimitField(Map<String, String> params) {
        String llmInterface = params == null ? null : params.get(MLChatAgentRunner.LLM_INTERFACE);
        if (llmInterface == null) {
            return MAX_TOKENS_FIELD;
        }

        String normalized = llmInterface.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(LLM_INTERFACE_BEDROCK_CONVERSE) || (normalized.contains("bedrock") && normalized.contains("converse"))) {
            return BEDROCK_MAX_TOKENS_FIELD;
        }
        if (normalized.startsWith(LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT) || normalized.contains("gemini")) {
            return GEMINI_MAX_OUTPUT_TOKENS_FIELD;
        }
        return MAX_TOKENS_FIELD;
    }
}
