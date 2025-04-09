/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

public class DJLUtilsTests extends HFModelAnalyzerTestCase {
    @Test
    public void testBuildHuggingFaceTokenizer_InvalidTokenizerId() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> DJLUtils.buildHuggingFaceTokenizer("invalid-tokenizer"));
        assertEquals("Invalid resource path invalid-tokenizer", exception.getMessage());
    }

    @Test
    public void testBuildHuggingFaceTokenizer_thenSuccess() {
        HuggingFaceTokenizer tokenizer = DJLUtils.buildHuggingFaceTokenizer("/analysis/tokenizer_en.json");
        assertNotNull(tokenizer);
        assertEquals(512, tokenizer.getMaxLength());
        Encoding result = tokenizer.encode("hello world");
        assertEquals(4, result.getIds().length);
        assertEquals(7592, result.getIds()[1]);
    }

    @Test
    public void testFetchTokenWeights_InvalidParams() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> DJLUtils.fetchTokenWeights("invalid-file"));
        assertEquals("Invalid resource path invalid-file", exception.getMessage());
    }

    @Test
    public void testFetchTokenWeights_thenSuccess() {
        Map<String, Float> tokenWeights = DJLUtils.fetchTokenWeights("/analysis/token_weights_en.txt");
        assertNotNull(tokenWeights);
        assertEquals(6.93775f, tokenWeights.get("hello"), 0.0001f);
    }
}
