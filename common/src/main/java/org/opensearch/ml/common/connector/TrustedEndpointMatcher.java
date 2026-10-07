/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;

/**
 * Matches a resolved connector URL against the trusted connector endpoint regexes with a bounded amount of work.
 *
 * <p>The regexes come from an admin-only cluster setting, and {@code MLCommonsSettings#validateRegexSafety} only
 * rejects the obvious catastrophic-backtracking shapes. A pattern that slips through, such as
 * {@code ((a+)){1,100}}, could otherwise stall every predict request on a non-matching URL. Each match is therefore
 * given a budget of character reads; a match that runs out of budget is treated as not matching, so the check fails
 * closed. Compiled patterns are cached because the setting changes rarely while this runs on every predict.
 */
@Log4j2
final class TrustedEndpointMatcher {

    // Matching a URL against a well-formed endpoint regex reads each character only a few times (about 5 for the
    // default patterns), so these limits sit far above any legitimate match. The budget scales with the URL length so
    // long URLs keep matching, while a backtracking pattern is cut off after work linear in the URL length.
    static final long MIN_MATCH_BUDGET = 1_000_000L;
    static final long MATCH_BUDGET_PER_CHAR = 20L;

    // Bounds the cache in case the setting is changed many times over the lifetime of a node.
    static final int MAX_CACHED_PATTERNS = 1_000;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private TrustedEndpointMatcher() {}

    /**
     * Returns whether the URL fully matches the regex. Returns false if the match exceeds its budget.
     *
     * @throws java.util.regex.PatternSyntaxException if the regex is invalid
     */
    static boolean matches(String regex, String url) {
        long budget = Math.max(MIN_MATCH_BUDGET, MATCH_BUDGET_PER_CHAR * url.length());
        try {
            return compile(regex).matcher(new BudgetedCharSequence(url, budget)).matches();
        } catch (MatchBudgetExceededException e) {
            log.warn("Trusted connector endpoint regex exceeded its match budget and was treated as not matching: {}", regex);
            return false;
        }
    }

    static Pattern compile(String regex) {
        Pattern pattern = PATTERN_CACHE.get(regex);
        if (pattern == null) {
            if (PATTERN_CACHE.size() >= MAX_CACHED_PATTERNS) {
                PATTERN_CACHE.clear();
            }
            pattern = PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
        }
        return pattern;
    }

    static int cachedPatternCount() {
        return PATTERN_CACHE.size();
    }

    private static final class MatchBudgetExceededException extends RuntimeException {
        MatchBudgetExceededException() {
            super(null, null, false, false);
        }
    }

    /**
     * A CharSequence that throws once more than {@code budget} characters have been read. java.util.regex reads the
     * input only through charAt, so this bounds the work of a single match. Not thread-safe; use one per match.
     */
    private static final class BudgetedCharSequence implements CharSequence {
        private final String value;
        private long remaining;

        BudgetedCharSequence(String value, long budget) {
            this.value = value;
            this.remaining = budget;
        }

        @Override
        public char charAt(int index) {
            if (--remaining < 0) {
                throw new MatchBudgetExceededException();
            }
            return value.charAt(index);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
