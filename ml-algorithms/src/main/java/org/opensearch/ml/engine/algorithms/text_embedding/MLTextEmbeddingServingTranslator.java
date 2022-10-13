/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.exception.MLException;

import java.nio.ByteBuffer;

public class MLTextEmbeddingServingTranslator implements Translator<Input, Output> {

    private Translator<String, float[]> translator;

    public MLTextEmbeddingServingTranslator(Translator<String, float[]> translator) {
        this.translator = translator;
    }

    @Override
    public Batchifier getBatchifier() {
        return translator.getBatchifier();
    }

    @Override
    public void prepare(TranslatorContext ctx) throws Exception {
        translator.prepare(ctx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NDList processInput(TranslatorContext ctx, Input input) throws Exception{
        String text = input.getData().getAsString();
        return translator.processInput(ctx, text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) throws Exception {
        float[] ret = translator.processOutput(ctx, list);
        Output output = new Output();
        output.add(toBytes(ret));
        return output;
    }

    public static byte[] toBytes(float[] floats) {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            bytesStreamOutput.writeFloatArray(floats);
            bytesStreamOutput.flush();
            return bytesStreamOutput.bytes().toBytesRef().bytes;
        } catch (Exception e) {
            throw new MLException("Failed to parse result");
        }
    }

    public static Number[] toFloats(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try (StreamInput streamInput = BytesReference.fromByteBuffer(byteBuffer).streamInput()) {
            float[] floats = streamInput.readFloatArray();
            Number[] result = new Number[floats.length];
            for (int i =0;i<floats.length;i++) {
                float f = floats[i];
                result[i] = f;
            }
            return result;
        } catch (Exception e) {
            throw new MLException("Failed to parse result");
        }
    }

}