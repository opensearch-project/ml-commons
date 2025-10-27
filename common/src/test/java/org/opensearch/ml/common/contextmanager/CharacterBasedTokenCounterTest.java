/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for CharacterBasedTokenCounter.
 */
public class CharacterBasedTokenCounterTest {

    private CharacterBasedTokenCounter tokenCounter;

    @Before
    public void setUp() {
        tokenCounter = new CharacterBasedTokenCounter();
    }

    @Test
    public void testCountWithNullText() {
        Assert.assertEquals(0, tokenCounter.count(null));
    }

    @Test
    public void testCountWithEmptyText() {
        Assert.assertEquals(0, tokenCounter.count(""));
    }

    @Test
    public void testCountWithShortText() {
        String text = "Hi";
        int expectedTokens = (int) Math.ceil(text.length() / 4.0);
        Assert.assertEquals(expectedTokens, tokenCounter.count(text));
    }

    @Test
    public void testCountWithMediumText() {
        String text = "This is a test message";
        int expectedTokens = (int) Math.ceil(text.length() / 4.0);
        Assert.assertEquals(expectedTokens, tokenCounter.count(text));
    }

    @Test
    public void testCountWithLongText() {
        String text = "This is a very long text that should result in multiple tokens when counted using the character-based approach.";
        int expectedTokens = (int) Math.ceil(text.length() / 4.0);
        Assert.assertEquals(expectedTokens, tokenCounter.count(text));
    }

    @Test
    public void testTruncateFromEndWithNullText() {
        Assert.assertNull(tokenCounter.truncateFromEnd(null, 10));
    }

    @Test
    public void testTruncateFromEndWithEmptyText() {
        Assert.assertEquals("", tokenCounter.truncateFromEnd("", 10));
    }

    @Test
    public void testTruncateFromEndWithShortText() {
        String text = "Short";
        String result = tokenCounter.truncateFromEnd(text, 10);
        Assert.assertEquals(text, result);
    }

    @Test
    public void testTruncateFromEndWithLongText() {
        String text = "This is a very long text that needs to be truncated";
        String result = tokenCounter.truncateFromEnd(text, 5);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.length() < text.length());
        Assert.assertTrue(result.length() <= 5 * 4); // 5 tokens * 4 chars per token
        Assert.assertTrue(text.startsWith(result));
    }

    @Test
    public void testTruncateFromBeginningWithNullText() {
        Assert.assertNull(tokenCounter.truncateFromBeginning(null, 10));
    }

    @Test
    public void testTruncateFromBeginningWithEmptyText() {
        Assert.assertEquals("", tokenCounter.truncateFromBeginning("", 10));
    }

    @Test
    public void testTruncateFromBeginningWithShortText() {
        String text = "Short";
        String result = tokenCounter.truncateFromBeginning(text, 10);
        Assert.assertEquals(text, result);
    }

    @Test
    public void testTruncateFromBeginningWithLongText() {
        String text = "This is a very long text that needs to be truncated";
        String result = tokenCounter.truncateFromBeginning(text, 5);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.length() < text.length());
        Assert.assertTrue(result.length() <= 5 * 4); // 5 tokens * 4 chars per token
        Assert.assertTrue(text.endsWith(result));
    }

    @Test
    public void testTruncateMiddleWithNullText() {
        Assert.assertNull(tokenCounter.truncateMiddle(null, 10));
    }

    @Test
    public void testTruncateMiddleWithEmptyText() {
        Assert.assertEquals("", tokenCounter.truncateMiddle("", 10));
    }

    @Test
    public void testTruncateMiddleWithShortText() {
        String text = "Short";
        String result = tokenCounter.truncateMiddle(text, 10);
        Assert.assertEquals(text, result);
    }

    @Test
    public void testTruncateMiddleWithLongText() {
        String text = "This is a very long text that needs to be truncated from the middle";
        String result = tokenCounter.truncateMiddle(text, 5);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.length() < text.length());
        Assert.assertTrue(result.length() <= 5 * 4); // 5 tokens * 4 chars per token

        // Result should contain parts from both beginning and end
        int halfChars = (5 * 4) / 2;
        String expectedBeginning = text.substring(0, halfChars);
        String expectedEnd = text.substring(text.length() - halfChars);

        Assert.assertTrue(result.startsWith(expectedBeginning));
        Assert.assertTrue(result.endsWith(expectedEnd));
    }

    @Test
    public void testTruncateConsistency() {
        String text = "This is a test text for truncation consistency";
        int maxTokens = 3;

        String fromEnd = tokenCounter.truncateFromEnd(text, maxTokens);
        String fromBeginning = tokenCounter.truncateFromBeginning(text, maxTokens);
        String fromMiddle = tokenCounter.truncateMiddle(text, maxTokens);

        // All truncated results should have similar token counts
        int tokensFromEnd = tokenCounter.count(fromEnd);
        int tokensFromBeginning = tokenCounter.count(fromBeginning);
        int tokensFromMiddle = tokenCounter.count(fromMiddle);

        Assert.assertTrue(tokensFromEnd <= maxTokens);
        Assert.assertTrue(tokensFromBeginning <= maxTokens);
        Assert.assertTrue(tokensFromMiddle <= maxTokens);
    }
}
