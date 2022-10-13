/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.exception.MLException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;

//TODO: tune this class
public class ONNXSentenceTransformerTextEmbeddingTranslator implements ServingTranslator {
    private static final int[] AXIS = {0};
    private HuggingFaceTokenizer tokenizer;

    @Override
    public Batchifier getBatchifier() {
        return null;

    }
    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(path.resolve("tokenizer.json")).build();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        NDManager manager = ctx.getNDManager();
        String sentence = input.getAsString(0);
        NDList ndList = new NDList();

        Encoding encode = tokenizer.encode(sentence);
        ctx.setAttachment("encoding", encode);
        long[] indices = encode.getIds();
        long[] attentionMask = encode.getAttentionMask();
        long[] tokenTypeIds = encode.getTypeIds();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName("input_ids");
        NDArray attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName("attention_mask");
        NDArray tokenTypeIdsArray = manager.create(tokenTypeIds);
        tokenTypeIdsArray.setName("token_type_ids");
        ndList.add(indicesArray.expandDims(0));
        ndList.add(tokenTypeIdsArray.expandDims(0));
        ndList.add(attentionMaskArray.expandDims(0));
        return ndList;
    }

    /** {@inheritDoc} */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        NDArray embeddings = list.get(0);
        int shapeLength = embeddings.getShape().getShape().length;
        if (shapeLength == 3) {
            embeddings = embeddings.get(0);
        }
        Encoding encoding = (Encoding) ctx.getAttachment("encoding");
        long[] attentionMask = encoding.getAttentionMask();
        NDManager manager = ctx.getNDManager();
        NDArray inputAttentionMask = manager.create(attentionMask);
        long[] shape = embeddings.getShape().getShape();
        inputAttentionMask = inputAttentionMask.expandDims(-1).broadcast(shape);
        NDArray inputAttentionMaskSum = inputAttentionMask.sum(AXIS);
        NDArray clamp = inputAttentionMaskSum.clip(1e-9, 1e12);
        NDArray prod = embeddings.mul(inputAttentionMask);
        NDArray sum = prod.sum(AXIS);
        embeddings = sum.div(clamp).normalize(2, 0);

        Output output = new Output();
        output.add(toBytes(embeddings.toFloatArray()));
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

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
