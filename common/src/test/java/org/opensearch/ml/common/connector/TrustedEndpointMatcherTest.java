/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.regex.PatternSyntaxException;

import org.junit.Test;

public class TrustedEndpointMatcherTest {

    private static final String OPENAI_REGEX = "^https://api\\.openai\\.com/.*$";
    private static final String CATASTROPHIC_REGEX = "^https://host/((a+)){1,100}/path$";

    @Test
    public void matches_fullMatchRequired() {
        assertTrue(TrustedEndpointMatcher.matches(OPENAI_REGEX, "https://api.openai.com/v1/chat/completions"));
        assertFalse(TrustedEndpointMatcher.matches("https://api\\.openai\\.com", "https://api.openai.com/v1/chat/completions"));
        assertFalse(TrustedEndpointMatcher.matches(OPENAI_REGEX, "https://attacker.example.com/?https://api.openai.com/"));
    }

    @Test
    public void matches_emptyUrl() {
        assertTrue(TrustedEndpointMatcher.matches(".*", ""));
        assertFalse(TrustedEndpointMatcher.matches(OPENAI_REGEX, ""));
    }

    @Test(timeout = 10_000)
    public void matches_catastrophicPattern_returnsFalseWithinBudget() {
        assertFalse(TrustedEndpointMatcher.matches(CATASTROPHIC_REGEX, "https://host/" + "a".repeat(40) + "/nope"));
    }

    @Test(timeout = 10_000)
    public void matches_catastrophicPattern_matchingUrl_returnsTrue() {
        assertTrue(TrustedEndpointMatcher.matches(CATASTROPHIC_REGEX, "https://host/" + "a".repeat(25) + "/path"));
    }

    @Test
    public void matches_budgetScalesWithUrlLength() {
        // ~5 character reads per character for this pattern, so a URL this long needs more than MIN_MATCH_BUDGET
        String url = "https://bedrock-runtime.us-east-1.amazonaws.com/model/m/invoke?q=" + "x".repeat(1_000_000);
        assertTrue(TrustedEndpointMatcher.matches("^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$", url));
    }

    @Test
    public void matches_invalidRegex_throws() {
        assertThrows(PatternSyntaxException.class, () -> TrustedEndpointMatcher.matches("^https://(unclosed", "https://host/"));
    }

    @Test
    public void compile_reusesCompiledPattern() {
        assertSame(TrustedEndpointMatcher.compile(OPENAI_REGEX), TrustedEndpointMatcher.compile(OPENAI_REGEX));
    }

    @Test
    public void compile_cacheIsBounded() {
        for (int i = 0; i < TrustedEndpointMatcher.MAX_CACHED_PATTERNS + 10; i++) {
            TrustedEndpointMatcher.compile("^https://host-" + i + "\\.example\\.com/.*$");
            assertTrue(TrustedEndpointMatcher.cachedPatternCount() <= TrustedEndpointMatcher.MAX_CACHED_PATTERNS);
        }
        assertTrue(TrustedEndpointMatcher.matches(OPENAI_REGEX, "https://api.openai.com/v1/chat/completions"));
    }
}
