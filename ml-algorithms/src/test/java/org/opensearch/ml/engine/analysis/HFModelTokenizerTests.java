/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.BytesRef;
import org.junit.Before;
import org.junit.Test;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.SneakyThrows;

public class HFModelTokenizerTests extends HFModelAnalyzerTestCase {
    private HuggingFaceTokenizer huggingFaceTokenizer;
    private Map<String, Float> tokenWeights;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        huggingFaceTokenizer = DJLUtils.buildHuggingFaceTokenizer(mlEngine.getAnalysisRootPath().resolve("test").resolve("tokenizer.json"));
        tokenWeights = new HashMap<>();
        tokenWeights.put("hello", 0.5f);
        tokenWeights.put("world", 0.3f);
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithoutWeights() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer);
        tokenizer.setReader(new StringReader("hello world a"));
        tokenizer.reset();

        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAtt = tokenizer.addAttribute(PayloadAttribute.class);

        assertTrue(tokenizer.incrementToken());
        assertEquals("hello", termAtt.toString());
        BytesRef payload = payloadAtt.getPayload();
        assertNull(payload);

        assertTrue(tokenizer.incrementToken());
        assertEquals("world", termAtt.toString());
        payload = payloadAtt.getPayload();
        assertNull(payload);

        assertTrue(tokenizer.incrementToken());
        assertEquals("a", termAtt.toString());
        payload = payloadAtt.getPayload();
        assertNull(payload);

        // No more tokens
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithWeights() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer, () -> tokenWeights);
        tokenizer.setReader(new StringReader("hello world a"));
        tokenizer.reset();

        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAtt = tokenizer.addAttribute(PayloadAttribute.class);
        TypeAttribute typeAtt = tokenizer.addAttribute(TypeAttribute.class);

        assertTrue(tokenizer.incrementToken());
        assertEquals("hello", termAtt.toString());
        BytesRef payload = payloadAtt.getPayload();
        assertNotNull(payload);
        assertEquals(0.5f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals("word", typeAtt.type());

        assertTrue(tokenizer.incrementToken());
        assertEquals("world", termAtt.toString());
        payload = payloadAtt.getPayload();
        assertNotNull(payload);
        assertEquals(0.3f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals("word", typeAtt.type());

        assertTrue(tokenizer.incrementToken());
        assertEquals("a", termAtt.toString());
        payload = payloadAtt.getPayload();
        assertNotNull(payload);
        assertEquals(1f, HFModelTokenizer.bytesToFloat(payload.bytes), 0f);
        assertEquals("word", typeAtt.type());

        // No more tokens
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testTokenizeLongText() {
        // Create a text longer than the max length to test overflow handling
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("hello world ");
        }

        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer);
        tokenizer.setReader(new StringReader(longText.toString()));
        tokenizer.reset();

        int tokenCount = 0;
        while (tokenizer.incrementToken()) {
            tokenCount++;
        }

        assertEquals(2000, tokenCount);
    }

    @Test
    public void testFloatBytesConversion() {
        float originalValue = 0.5f;
        byte[] bytes = HFModelTokenizer.floatToBytes(originalValue);
        float convertedValue = HFModelTokenizer.bytesToFloat(bytes);
        assertEquals(originalValue, convertedValue, 0f);
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithTypeWord() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer);
        tokenizer.setReader(new StringReader("hello world"));
        tokenizer.reset();

        // Add and set type attribute before reset
        TypeAttribute typeAtt = tokenizer.addAttribute(TypeAttribute.class);
        typeAtt.setType("word");
        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);

        assertTrue(tokenizer.incrementToken());
        assertEquals("hello", termAtt.toString());
        assertEquals("word", typeAtt.type());

        assertTrue(tokenizer.incrementToken());
        assertEquals("world", termAtt.toString());
        assertEquals("word", typeAtt.type());

        // No more tokens
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithTypeTokenId() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer);
        tokenizer.setReader(new StringReader("hello world"));
        tokenizer.reset();

        // Add and set type attribute before reset
        TypeAttribute typeAtt = tokenizer.addAttribute(TypeAttribute.class);
        typeAtt.setType("token_id");
        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);

        assertTrue(tokenizer.incrementToken());
        String firstToken = termAtt.toString();
        assertEquals("7592", termAtt.toString());

        assertTrue(tokenizer.incrementToken());
        String secondToken = termAtt.toString();
        assertEquals("2088", termAtt.toString());

        // No more tokens
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithTypeTokenIdAndWeights() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer, () -> tokenWeights);
        tokenizer.setReader(new StringReader("hello world"));
        tokenizer.reset();

        // Add and set type attribute before reset
        TypeAttribute typeAtt = tokenizer.addAttribute(TypeAttribute.class);
        typeAtt.setType("token_id");
        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAtt = tokenizer.addAttribute(PayloadAttribute.class);

        assertTrue(tokenizer.incrementToken());
        String firstToken = termAtt.toString();
        assertEquals("7592", termAtt.toString());
        BytesRef payload = payloadAtt.getPayload();
        assertNotNull(payload);
        assertEquals(0.5f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals("token_id", typeAtt.type());

        assertTrue(tokenizer.incrementToken());
        String secondToken = termAtt.toString();
        assertEquals("2088", termAtt.toString());
        payload = payloadAtt.getPayload();
        assertNotNull(payload);
        assertEquals(0.3f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals("token_id", typeAtt.type());

        // No more tokens
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testTokenizeWithInvalidType() {
        HFModelTokenizer tokenizer = new HFModelTokenizer(() -> huggingFaceTokenizer);
        tokenizer.setReader(new StringReader("hello world"));

        // Add and set invalid type attribute before reset
        TypeAttribute typeAtt = tokenizer.addAttribute(TypeAttribute.class);
        typeAtt.setType("invalid_type");
        CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);

        tokenizer.reset();

        // Should throw IllegalArgumentException when incrementToken() is called with invalid type
        try {
            tokenizer.incrementToken();
            fail("Expected IllegalArgumentException for invalid type");
        } catch (IllegalArgumentException e) {
            assertTrue(
                "Exception message should contain enum error",
                e.getMessage().contains("No enum constant") && e.getMessage().contains("SparseEmbeddingFormat.INVALID_TYPE")
            );
        }
    }
}
