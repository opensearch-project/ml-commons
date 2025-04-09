/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.analysis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

import com.google.common.io.CharStreams;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.extern.log4j.Log4j2;

/**
 * A Lucene Tokenizer implementation that uses Hugging Face tokenizer for tokenization.
 * Supports token weighting and handles overflow scenarios.
 */
@Log4j2
public class HFModelTokenizer extends Tokenizer {
    public static final String NAME = "hf_model_tokenizer";
    private static final Float DEFAULT_TOKEN_WEIGHT = 1.0f;

    private final CharTermAttribute termAtt;
    private final PayloadAttribute payloadAtt;
    private final OffsetAttribute offsetAtt;
    private final Supplier<HuggingFaceTokenizer> tokenizerSupplier;
    private final Supplier<Map<String, Float>> tokenWeightsSupplier;

    private Encoding encoding;
    private int tokenIdx = 0;
    private int overflowingIdx = 0;

    public HFModelTokenizer(Supplier<HuggingFaceTokenizer> huggingFaceTokenizerSupplier) {
        this(huggingFaceTokenizerSupplier, null);
    }

    public HFModelTokenizer(Supplier<HuggingFaceTokenizer> huggingFaceTokenizerSupplier, Supplier<Map<String, Float>> weightsSupplier) {
        termAtt = addAttribute(CharTermAttribute.class);
        offsetAtt = addAttribute(OffsetAttribute.class);
        if (Objects.nonNull(weightsSupplier)) {
            payloadAtt = addAttribute(PayloadAttribute.class);
        } else {
            payloadAtt = null;
        }
        tokenizerSupplier = huggingFaceTokenizerSupplier;
        tokenWeightsSupplier = weightsSupplier;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        tokenIdx = 0;
        overflowingIdx = -1;
        String inputStr = CharStreams.toString(input);
        // For pre-built analyzer, when create new index service, reset() will be called with empty input in checkVersions
        // And we want to lazy-load the tokenizer only really needed. So we use supplier, and skip empty input.
        encoding = StringUtils.isEmpty(inputStr) ? null : tokenizerSupplier.get().encode(inputStr, false, true);
    }

    private static boolean isLastTokenInEncodingSegment(int idx, Encoding encodingSegment) {
        return idx >= encodingSegment.getTokens().length || encodingSegment.getAttentionMask()[idx] == 0;
    }

    public static byte[] floatToBytes(float value) {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static float bytesToFloat(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getFloat();
    }

    @Override
    final public boolean incrementToken() throws IOException {
        clearAttributes();
        if (Objects.isNull(encoding))
            return false;
        Encoding curEncoding = overflowingIdx == -1 ? encoding : encoding.getOverflowing()[overflowingIdx];

        while (!isLastTokenInEncodingSegment(tokenIdx, curEncoding) || overflowingIdx < encoding.getOverflowing().length) {
            if (isLastTokenInEncodingSegment(tokenIdx, curEncoding)) {
                // reset cur segment, go to the next segment
                // until overflowingIdx = encoding.getOverflowing().length
                tokenIdx = 0;
                overflowingIdx++;
                if (overflowingIdx >= encoding.getOverflowing().length) {
                    return false;
                }
                curEncoding = encoding.getOverflowing()[overflowingIdx];
            } else {
                termAtt.append(curEncoding.getTokens()[tokenIdx]);
                offsetAtt
                    .setOffset(curEncoding.getCharTokenSpans()[tokenIdx].getStart(), curEncoding.getCharTokenSpans()[tokenIdx].getEnd());
                if (Objects.nonNull(tokenWeightsSupplier)) {
                    // for neural sparse query, write the token weight to payload field
                    payloadAtt
                        .setPayload(
                            new BytesRef(
                                floatToBytes(
                                    tokenWeightsSupplier.get().getOrDefault(curEncoding.getTokens()[tokenIdx], DEFAULT_TOKEN_WEIGHT)
                                )
                            )
                        );
                }
                tokenIdx++;
                return true;
            }
        }

        return false;
    }
}
