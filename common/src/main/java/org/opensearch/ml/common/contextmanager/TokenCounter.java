/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

/**
 * Interface for counting and truncating tokens in text.
 * Provides methods for accurate token counting and various truncation strategies.
 */
public interface TokenCounter {

    /**
     * Count the number of tokens in the given text.
     * @param text the text to count tokens for
     * @return the number of tokens
     */
    int count(String text);

    /**
     * Truncate text from the end to fit within the specified token limit.
     * @param text the text to truncate
     * @param maxTokens the maximum number of tokens to keep
     * @return the truncated text
     */
    String truncateFromEnd(String text, int maxTokens);

    /**
     * Truncate text from the beginning to fit within the specified token limit.
     * @param text the text to truncate
     * @param maxTokens the maximum number of tokens to keep
     * @return the truncated text
     */
    String truncateFromBeginning(String text, int maxTokens);

    /**
     * Truncate text from the middle to fit within the specified token limit.
     * Preserves both beginning and end portions of the text.
     * @param text the text to truncate
     * @param maxTokens the maximum number of tokens to keep
     * @return the truncated text
     */
    String truncateMiddle(String text, int maxTokens);
}
