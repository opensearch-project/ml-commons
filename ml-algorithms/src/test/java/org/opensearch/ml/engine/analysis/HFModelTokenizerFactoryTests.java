package org.opensearch.ml.engine.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.junit.Test;

import lombok.SneakyThrows;

public class HFModelTokenizerFactoryTests extends HFModelAnalyzerTestCase {
    @SneakyThrows
    @Test
    public void testCreateDefault() {
        Tokenizer tokenizer = HFModelTokenizerFactory.createDefault();
        assertNotNull(tokenizer);
        assertTrue(tokenizer instanceof HFModelTokenizer);
        tokenizer.setReader(new StringReader("test"));
        tokenizer.reset();
        CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAttribute = tokenizer.addAttribute(PayloadAttribute.class);

        assertTrue(tokenizer.incrementToken());
        assertEquals("test", charTermAttribute.toString());
        // byte ref for the token weight of test
        assertEquals("[40 86 84 b6]", payloadAttribute.getPayload().toString());
        assertFalse(tokenizer.incrementToken());
    }

    @SneakyThrows
    @Test
    public void testCreateDefaultMultilingual() {
        Tokenizer tokenizer = HFModelTokenizerFactory.createDefaultMultilingual();
        assertNotNull(tokenizer);
        assertTrue(tokenizer instanceof HFModelTokenizer);
        tokenizer.setReader(new StringReader("测"));
        tokenizer.reset();
        CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
        PayloadAttribute payloadAttribute = tokenizer.addAttribute(PayloadAttribute.class);

        assertTrue(tokenizer.incrementToken());
        assertEquals("测", charTermAttribute.toString());
        // byte ref for the token weight of test
        assertEquals("[3f dc 69 2f]", payloadAttribute.getPayload().toString());
        assertFalse(tokenizer.incrementToken());
    }
}
