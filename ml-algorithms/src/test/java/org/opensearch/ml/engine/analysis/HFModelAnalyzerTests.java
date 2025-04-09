/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

import lombok.SneakyThrows;

public class HFModelAnalyzerTests extends HFModelAnalyzerTestCase {
    @SneakyThrows
    @Test
    public void testDefaultAnalyzer() {
        HFModelAnalyzer analyzer = new HFModelAnalyzer(HFModelTokenizerFactory::createDefault);
        TokenStream tokenStream = analyzer.tokenStream("", "hello world");
        tokenStream.reset();
        CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PayloadAttribute payloadAttribute = tokenStream.addAttribute(PayloadAttribute.class);

        assertTrue(tokenStream.incrementToken());
        assertEquals("hello", termAttribute.toString());
        BytesRef payload = payloadAttribute.getPayload();
        assertNotNull(payload);
        assertEquals(6.93775f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals(0, offsetAttribute.startOffset());
        assertEquals(5, offsetAttribute.endOffset());

        assertTrue(tokenStream.incrementToken());
        assertEquals("world", termAttribute.toString());
        payload = payloadAttribute.getPayload();
        assertNotNull(payload);
        assertEquals(3.42089f, HFModelTokenizer.bytesToFloat(payload.bytes), 0.0001f);
        assertEquals(6, offsetAttribute.startOffset());
        assertEquals(11, offsetAttribute.endOffset());

        assertFalse(tokenStream.incrementToken());
    }
}
