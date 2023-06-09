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
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.util.Collections;
import java.util.List;

import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.SENTENCE_EMBEDDING;

public class HuggingfaceTextEmbeddingServingTranslator implements Translator<Input, Output> {

    private Translator<String, float[]> translator;

    public HuggingfaceTextEmbeddingServingTranslator(Translator<String, float[]> translator) {
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
    public NDList processInput(TranslatorContext ctx, Input input) throws Exception {
        String text = input.getData().getAsString();
        return translator.processInput(ctx, text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) throws Exception {
        float[] ret = translator.processOutput(ctx, list);
        Number[] data = new Float[ret.length];
        for (int i = 0; i < ret.length; i++) {
            data[i] = ret[i];
        }
        long[] shape = new long[]{1, ret.length};
        ModelTensor tensor = ModelTensor.builder()
                .name(SENTENCE_EMBEDDING)
                .data(data)
                .shape(shape)
                .dataType(MLResultDataType.FLOAT32)
                .build();
        List<ModelTensor> outputs = Collections.singletonList(tensor);

        Output output = new Output();
        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

}