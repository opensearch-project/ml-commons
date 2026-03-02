/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import lombok.extern.log4j.Log4j2;

/**
 * Character-based token counter implementation.
 * Uses a simple heuristic of approximately 4 characters per token.
 * This is a fallback implementation when more sophisticated token counting is not available.
 */
@Log4j2
public class CharacterBasedTokenCounter implements TokenCounter {

    private static final double CHARS_PER_TOKEN = 4.0;

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public String truncateFromEnd(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int currentTokens = count(text);
        if (currentTokens <= maxTokens) {
            return text;
        }

        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (maxChars >= text.length()) {
            return text;
        }

        return text.substring(0, maxChars);
    }

    @Override
    public String truncateFromBeginning(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int currentTokens = count(text);
        if (currentTokens <= maxTokens) {
            return text;
        }

        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (maxChars >= text.length()) {
            return text;
        }

        return text.substring(text.length() - maxChars);
    }

    @Override
    public String truncateMiddle(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int currentTokens = count(text);
        if (currentTokens <= maxTokens) {
            return text;
        }

        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (maxChars >= text.length()) {
            return text;
        }

        // Keep equal portions from beginning and end
        int halfChars = maxChars / 2;
        String beginning = text.substring(0, halfChars);
        String end = text.substring(text.length() - halfChars);

        return beginning + end;
    }
}
