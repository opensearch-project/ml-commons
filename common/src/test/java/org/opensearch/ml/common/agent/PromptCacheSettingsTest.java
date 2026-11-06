/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class PromptCacheSettingsTest {

    @Test
    public void testIsEnabled() {
        assertTrue(PromptCacheSettings.isEnabled(Map.of("tools_cache_enabled", "TRUE"), PromptCacheSettings.TOOLS_CACHE_ENABLED));
        assertFalse(PromptCacheSettings.isEnabled(Map.of("tools_cache_enabled", "yes"), PromptCacheSettings.TOOLS_CACHE_ENABLED));
        assertFalse(PromptCacheSettings.isEnabled(Map.of(), PromptCacheSettings.TOOLS_CACHE_ENABLED));
        assertFalse(PromptCacheSettings.isEnabled(null, PromptCacheSettings.TOOLS_CACHE_ENABLED));
    }

    @Test
    public void testTtl() {
        assertEquals("1h", PromptCacheSettings.ttl(null, PromptCacheSettings.MESSAGES_CACHE_TTL, "1h"));
        assertEquals("5m", PromptCacheSettings.ttl(Map.of("messages_cache_ttl", " 5m "), PromptCacheSettings.MESSAGES_CACHE_TTL, "1h"));
        assertNull(PromptCacheSettings.explicitTtl(Map.of("messages_cache_ttl", ""), PromptCacheSettings.MESSAGES_CACHE_TTL));
        assertThrows(
            IllegalArgumentException.class,
            () -> PromptCacheSettings.explicitTtl(Map.of("messages_cache_ttl", "\"}"), PromptCacheSettings.MESSAGES_CACHE_TTL)
        );
    }

    @Test
    public void testSystemPromptCacheType() {
        assertEquals("default", PromptCacheSettings.systemPromptCacheType(null));
        assertEquals("default", PromptCacheSettings.systemPromptCacheType(Map.of("system_prompt_cache_type", " ")));
        assertEquals("custom", PromptCacheSettings.systemPromptCacheType(Map.of("system_prompt_cache_type", "custom")));
    }

    @Test
    public void testValidateTtlOrder_RejectsLongBreakpointAfterShortOne() {
        Map<String, String> systemThenMessages = Map
            .of(
                "system_prompt_cache_enabled",
                "true",
                "messages_cache_enabled",
                "true",
                "system_prompt_cache_ttl",
                "5m",
                "messages_cache_ttl",
                "1h"
            );
        Map<String, String> toolsThenMessages = Map
            .of("tools_cache_enabled", "true", "messages_cache_enabled", "true", "messages_cache_ttl", "1h");

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> PromptCacheSettings.validateTtlOrder(systemThenMessages, "1h")
        );
        assertTrue(e.getMessage().contains("messages_cache_ttl"));
        // With no system TTL set, the provider's default for the earlier breakpoints decides.
        assertThrows(IllegalArgumentException.class, () -> PromptCacheSettings.validateTtlOrder(toolsThenMessages, "5m"));
        PromptCacheSettings.validateTtlOrder(toolsThenMessages, "1h");
    }

    @Test
    public void testValidateTtlOrder_RejectsInvalidTtlEvenWhenBreakpointsAreOff() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> PromptCacheSettings.validateTtlOrder(Map.of("system_prompt_cache_ttl", "1d"), "1h")
        );
        assertTrue(e.getMessage().contains("system_prompt_cache_ttl"));
    }

    @Test
    public void testValidateTtlOrder_AllowsValidCombinations() {
        PromptCacheSettings.validateTtlOrder(null, "5m");
        PromptCacheSettings.validateTtlOrder(Map.of("messages_cache_enabled", "true", "messages_cache_ttl", "1h"), "5m");
        PromptCacheSettings.validateTtlOrder(Map.of("system_prompt_cache_enabled", "true", "messages_cache_enabled", "true"), "5m");
        PromptCacheSettings
            .validateTtlOrder(
                Map
                    .of(
                        "system_prompt_cache_enabled",
                        "true",
                        "messages_cache_enabled",
                        "true",
                        "system_prompt_cache_ttl",
                        "1h",
                        "messages_cache_ttl",
                        "1h"
                    ),
                "5m"
            );
    }
}
