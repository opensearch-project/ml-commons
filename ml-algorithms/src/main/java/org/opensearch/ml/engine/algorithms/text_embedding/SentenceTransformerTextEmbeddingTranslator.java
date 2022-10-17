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
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Batchifier;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SentenceTransformerTextEmbeddingTranslator implements ServingTranslator {
    private HuggingFaceTokenizer tokenizer;

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(path.resolve("tokenizer.json")).build();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String sentence = input.getAsString(0);
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        Encoding encodings = tokenizer.encode(sentence);
        long[] indices = encodings.getIds();
        long[] attentionMask = encodings.getAttentionMask();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName("input1.input_ids");

        NDArray attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName("input1.attention_mask");

        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);
        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");

        List<ModelTensor> outputs = new ArrayList<>();
        Iterator<NDArray> iterator = list.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            String name = ndArray.getName();
            Number[] data = ndArray.toArray();
            long[] shape = ndArray.getShape().getShape();
            DataType dataType = ndArray.getDataType();
            MLResultDataType mlResultDataType = MLResultDataType.valueOf(dataType.name());
            ByteBuffer buffer = ndArray.toByteBuffer();
            outputs.add(new ModelTensor(name, data, shape, mlResultDataType, buffer));
        }

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
