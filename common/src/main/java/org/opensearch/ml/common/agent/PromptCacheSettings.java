/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.Map;
import java.util.Set;

/**
 * Opt-in prompt caching settings, read from an agent's {@code model_parameters}.
 *
 * <p>Each flag adds one cache breakpoint to the requests a model provider renders. The cached prefix is
 * built in the order tools, system prompt, messages, so a breakpoint covers everything before it.
 * <ul>
 *   <li>{@code system_prompt_cache_enabled} (default {@code false}): breakpoint after the system prompt.
 *       {@code system_prompt_cache_ttl} refines it. When it isn't set, Anthropic Messages applies
 *       {@code 1h}; Bedrock Converse omits the TTL and Bedrock applies {@code 5m}.
 *       {@code system_prompt_cache_type} (default {@code default}) is the Bedrock {@code cachePoint} type,
 *       used for every Bedrock breakpoint; Anthropic always sends {@code ephemeral}.
 *       This breakpoint is built into the connector when the agent's model is created, so changing
 *       {@code system_prompt_cache_enabled}, {@code system_prompt_cache_ttl} or
 *       {@code system_prompt_cache_type} afterwards takes effect only once the model is re-created or updated.</li>
 *   <li>{@code tools_cache_enabled} (default {@code false}): breakpoint after the last tool definition.
 *       It uses the system prompt TTL and its per-provider default, because the tools come first in the
 *       prefix and a provider may reject a longer-lived breakpoint that follows a shorter-lived one.</li>
 *   <li>{@code messages_cache_enabled} (default {@code false}): breakpoint at the end of the conversation.
 *       {@code messages_cache_ttl} (default {@code 5m}) refines it.</li>
 * </ul>
 * The tools and messages flags are read on every request. The TTL order is checked when the model is
 * created and again on every request. Each provider renders the breakpoints in its own format; with every
 * flag off the request is unchanged.
 */
public final class PromptCacheSettings {

    public static final String SYSTEM_PROMPT_CACHE_ENABLED = "system_prompt_cache_enabled";
    public static final String SYSTEM_PROMPT_CACHE_TYPE = "system_prompt_cache_type";
    public static final String SYSTEM_PROMPT_CACHE_TTL = "system_prompt_cache_ttl";
    public static final String TOOLS_CACHE_ENABLED = "tools_cache_enabled";
    public static final String MESSAGES_CACHE_ENABLED = "messages_cache_enabled";
    public static final String MESSAGES_CACHE_TTL = "messages_cache_ttl";

    public static final String DEFAULT_SYSTEM_PROMPT_CACHE_TYPE = "default";
    public static final String DEFAULT_SYSTEM_PROMPT_CACHE_TTL = "1h";
    public static final String DEFAULT_MESSAGES_CACHE_TTL = "5m";

    public static final String TTL_5_MINUTES = "5m";
    public static final String TTL_1_HOUR = "1h";
    private static final Set<String> SUPPORTED_TTLS = Set.of(TTL_5_MINUTES, TTL_1_HOUR);

    private PromptCacheSettings() {}

    /**
     * @return true only when the flag is present and set to "true" (case-insensitive)
     */
    public static boolean isEnabled(Map<String, String> modelParameters, String flag) {
        return modelParameters != null && Boolean.parseBoolean(modelParameters.get(flag));
    }

    /**
     * Returns the configured TTL, or the default when it is not set.
     *
     * @throws IllegalArgumentException if the configured TTL is not one of "5m" and "1h"
     */
    public static String ttl(Map<String, String> modelParameters, String key, String defaultTtl) {
        String configured = explicitTtl(modelParameters, key);
        return configured != null ? configured : defaultTtl;
    }

    /**
     * Returns the TTL only when it is configured explicitly, else null.
     *
     * @throws IllegalArgumentException if the configured TTL is not one of "5m" and "1h"
     */
    public static String explicitTtl(Map<String, String> modelParameters, String key) {
        String value = modelParameters == null ? null : modelParameters.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        String ttl = value.trim();
        if (!SUPPORTED_TTLS.contains(ttl)) {
            throw new IllegalArgumentException(key + " must be one of " + TTL_5_MINUTES + ", " + TTL_1_HOUR + ", got: " + value);
        }
        return ttl;
    }

    /**
     * @return the system prompt cache type, defaulting to "default"
     */
    public static String systemPromptCacheType(Map<String, String> modelParameters) {
        String value = modelParameters == null ? null : modelParameters.get(SYSTEM_PROMPT_CACHE_TYPE);
        return value == null || value.isBlank() ? DEFAULT_SYSTEM_PROMPT_CACHE_TYPE : value.trim();
    }

    /**
     * Rejects a 1h messages breakpoint that follows a 5m tools or system breakpoint: providers reject a
     * longer-lived breakpoint after a shorter-lived one, and the tools and system breakpoints come first.
     * Both TTLs are validated even when their breakpoints are off.
     *
     * @param defaultPrefixTtl the TTL the provider applies to the tools and system breakpoints when
     *                         {@code system_prompt_cache_ttl} isn't set
     * @throws IllegalArgumentException if a TTL is not one of "5m" and "1h", or if a 1h messages breakpoint
     *                                  follows a 5m tools or system breakpoint
     */
    public static void validateTtlOrder(Map<String, String> modelParameters, String defaultPrefixTtl) {
        String prefixTtl = ttl(modelParameters, SYSTEM_PROMPT_CACHE_TTL, defaultPrefixTtl);
        String messagesTtl = ttl(modelParameters, MESSAGES_CACHE_TTL, DEFAULT_MESSAGES_CACHE_TTL);
        boolean prefixCached = isEnabled(modelParameters, SYSTEM_PROMPT_CACHE_ENABLED) || isEnabled(modelParameters, TOOLS_CACHE_ENABLED);
        if (prefixCached
            && isEnabled(modelParameters, MESSAGES_CACHE_ENABLED)
            && TTL_5_MINUTES.equals(prefixTtl)
            && TTL_1_HOUR.equals(messagesTtl)) {
            throw new IllegalArgumentException(
                MESSAGES_CACHE_TTL
                    + " cannot be longer than "
                    + SYSTEM_PROMPT_CACHE_TTL
                    + ": a 1h cache breakpoint must not follow a 5m one"
            );
        }
    }
}
