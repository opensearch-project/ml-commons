/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.custom;

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

import java.io.IOException;
import java.nio.file.Path;
import  java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeneralSentenceTransformerTranslator implements ServingTranslator {
    public static final int SIZE_LIMIT = 512;
    private HuggingFaceTokenizer tokenizer;

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.newInstance(path.resolve("tokenizer.json"));
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        List<String> sentences = Arrays.asList(input.getAsString("doc"));
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        for (String sentence : sentences) {
            sentence = sentence.strip().toLowerCase(Locale.ROOT);
            Encoding encode = tokenizer.encode(sentence);
            long[] indices = encode.getIds();
            NDArray indicesArray;
            if (indices.length > SIZE_LIMIT) {
                long[] truncatedIndices = new long[SIZE_LIMIT];
                for (int i = 0; i < SIZE_LIMIT; i++) {
                    truncatedIndices[i] = indices[i];
                }
                indicesArray = manager.create(truncatedIndices);
            } else {
                indicesArray = manager.create(indices);
            }
            indicesArray.setName("input1.input_ids");

            long[] attentionMask = encode.getAttentionMask();
            NDArray attentionMaskArray;
            if (attentionMask.length > SIZE_LIMIT) {
                long[] truncatedAttentionMask = new long[SIZE_LIMIT];
                for (int i = 0; i < SIZE_LIMIT; i++) {
                    truncatedAttentionMask[i] = attentionMask[i];
                }
                attentionMaskArray = manager.create(truncatedAttentionMask);
            } else {
                attentionMaskArray = manager.create(attentionMask);
            }
            attentionMaskArray.setName("input1.attention_mask");

            ndList.add(indicesArray);
            ndList.add(attentionMaskArray);
        }
        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        List<float[]> embeddings = new ArrayList<>();
        float[] embedding = list.get("sentence_embedding").toFloatArray();
        embeddings.add(embedding);
        Output output = new Output(200, "OK");
        output.add(Arrays.toString(embedding));
        return output;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
