/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.SentenceTransformerTranslator;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.TranslatorContext;

public class QuestionAnsweringTranslator extends SentenceTransformerTranslator {
    // private static final int[] AXIS = {0};
    private List<String> tokens;

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        NDManager manager = ctx.getNDManager();
        String question = input.getAsString(0);
        String paragraph = input.getAsString(1);
        NDList ndList = new NDList();

        Encoding encodings = tokenizer.encode(question, paragraph);
        tokens = Arrays.asList(encodings.getTokens());
        ctx.setAttachment("encoding", encodings);
        long[] indices = encodings.getIds();
        long[] attentionMask = encodings.getAttentionMask();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName("input_ids");

        NDArray attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName("attention_mask");

        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);
        return ndList;
    }

    /** {@inheritDoc} */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");

        List<ModelTensor> outputs = new ArrayList<>();

        NDArray startLogits = list.get(0);
        NDArray endLogits = list.get(1);
        int startIdx = (int) startLogits.argMax().getLong();
        int endIdx = (int) endLogits.argMax().getLong();
        if (startIdx >= endIdx) {
            int tmp = startIdx;
            startIdx = endIdx;
            endIdx = tmp;
        }
        String answer = tokenizer.buildSentence(tokens.subList(startIdx, endIdx + 1));

        outputs.add(new ModelTensor("answer", answer));

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

}
