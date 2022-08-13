/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.bertqa;

import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.Vocabulary;
import ai.djl.modality.nlp.bert.BertToken;
import ai.djl.modality.nlp.bert.BertTokenizer;
import ai.djl.modality.nlp.qa.QAInput;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BertQATranslator implements Translator<QAInput, String> {
    private List<String> tokens;
    private Vocabulary vocabulary;
    private BertTokenizer tokenizer;

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        vocabulary = DefaultVocabulary.builder()
                .optMinFrequency(1)
                .addFromTextFile(path.resolve("vocab.txt"))
                .optUnknownToken("[UNK]")
                .build();
        tokenizer = new BertTokenizer();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, QAInput input) {
        BertToken token =
                tokenizer.encode(
                        input.getQuestion().toLowerCase(),
                        input.getParagraph().toLowerCase());
        tokens = token.getTokens();
        NDManager manager = ctx.getNDManager();
        long[] indices = tokens.stream().mapToLong(vocabulary::getIndex).toArray();
        long[] attentionMask = token.getAttentionMask().stream().mapToLong(i -> i).toArray();
        long[] tokenType = token.getTokenTypes().stream().mapToLong(i -> i).toArray();
        NDArray indicesArray = manager.create(indices);
        NDArray attentionMaskArray =
                manager.create(attentionMask);
        NDArray tokenTypeArray = manager.create(tokenType);
        return new NDList(indicesArray, attentionMaskArray, tokenTypeArray);
    }

    @Override
    public String processOutput(TranslatorContext ctx, NDList list) {
        NDArray startLogits = list.get(0);
        NDArray endLogits = list.get(1);
        int startIdx = (int) startLogits.argMax().getLong();
        int endIdx = (int) endLogits.argMax().getLong();
        return tokens.subList(startIdx, endIdx + 1).toString();
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}
