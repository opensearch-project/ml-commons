/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

public class DJLUtilsTests extends HFModelAnalyzerTestCase {
    @Test
    public void testBuildHuggingFaceTokenizer_InvalidTokenizerId() {
        Exception exception = assertThrows(
            RuntimeException.class,
            () -> DJLUtils.buildHuggingFaceTokenizer(mlEngine.getAnalysisRootPath().resolve("test2").resolve("tokenizer.json"))
        );
        assertTrue(exception.getMessage().contains("Failed to initialize Hugging Face tokenizer. java.nio.file.NoSuchFileException:"));
    }

    @Test
    public void testBuildHuggingFaceTokenizer_thenSuccess() {
        HuggingFaceTokenizer tokenizer = DJLUtils
            .buildHuggingFaceTokenizer(mlEngine.getAnalysisRootPath().resolve("test").resolve("tokenizer.json"));
        assertNotNull(tokenizer);
        assertEquals(512, tokenizer.getMaxLength());
        Encoding result = tokenizer.encode("hello world");
        assertEquals(4, result.getIds().length);
        assertEquals(7592, result.getIds()[1]);
    }

    @Test
    public void testFetchTokenWeights_InvalidParams() {
        Exception exception = assertThrows(
            RuntimeException.class,
            () -> DJLUtils.fetchTokenWeights(mlEngine.getAnalysisRootPath().resolve("test2").resolve("idf.json"))
        );
        assertTrue(exception.getMessage().contains("Failed to parse token weights file. java.nio.file.NoSuchFileException:"));
    }

    @Test
    public void testFetchTokenWeights_thenSuccess() {
        Map<String, Float> tokenWeights = DJLUtils.fetchTokenWeights(mlEngine.getAnalysisRootPath().resolve("test").resolve("idf.json"));
        assertNotNull(tokenWeights);
        assertEquals(6.93775f, tokenWeights.get("hello"), 0.0001f);
    }
}
