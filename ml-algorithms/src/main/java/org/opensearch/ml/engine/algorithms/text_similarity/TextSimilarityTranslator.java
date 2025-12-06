/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.engine.algorithms.text_similarity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.SentenceTransformerTranslator;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.PairList;

public class TextSimilarityTranslator extends SentenceTransformerTranslator {
    public final String SIMILARITY_NAME = "similarity";

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String sentence = input.getAsString(0);
        String context = input.getAsString(1);
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        Encoding encodings = tokenizer.encode(sentence, context);
        long[] indices = encodings.getIds();
        long[] attentionMask = encodings.getAttentionMask();
        long[] tokenTypes = encodings.getTypeIds();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName("input_ids");

        NDArray attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName("attention_mask");

        NDArray tokenTypeArray = manager.create(tokenTypes);
        tokenTypeArray.setName("token_type_ids");

        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);
        ndList.add(tokenTypeArray);
        return ndList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");

        List<ModelTensor> outputs = new ArrayList<>();
        Iterator<NDArray> iterator = list.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            String name = SIMILARITY_NAME;
            Number[] data = ndArray.toArray();
            long[] shape = ndArray.getShape().getShape();
            DataType dataType = ndArray.getDataType();
            MLResultDataType mlResultDataType = MLResultDataType.valueOf(dataType.name());
            ByteBuffer buffer = ndArray.toByteBuffer();
            ModelTensor tensor = ModelTensor
                .builder()
                .name(name)
                .data(data)
                .shape(shape)
                .dataType(mlResultDataType)
                .byteBuffer(buffer)
                .build();
            outputs.add(tensor);
        }

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    @Override
    public NDList batchProcessInput(TranslatorContext ctx, List<Input> inputs) {
        NDManager manager = ctx.getNDManager();
        int batchSize = inputs.size();
        List<String> sentences = new ArrayList<>(batchSize);
        List<String> contexts = new ArrayList<>(batchSize);
        for (Input input : inputs) {
            String sentence = input.getAsString(0);
            String context = input.getAsString(1);
            sentences.add(sentence);
            contexts.add(context);
        }
        // Tokenize in batches
        Encoding[] encodings = tokenizer.batchEncode(new PairList<>(sentences, contexts));
        int seqLen = encodings[0].getIds().length;
        for (Encoding enc : encodings) {
            seqLen = Math.max(seqLen, enc.getIds().length);
        }
        long[][] inputIds = new long[batchSize][seqLen];
        long[][] attentionMasks = new long[batchSize][seqLen];
        long[][] tokenTypeIds = new long[batchSize][seqLen];
        for (int i = 0; i < batchSize; i++) {
            inputIds[i] = encodings[i].getIds();
            attentionMasks[i] = encodings[i].getAttentionMask();
            tokenTypeIds[i] = encodings[i].getTypeIds();
        }
        NDArray inputIdsArray = manager.create(inputIds);
        inputIdsArray.setName("input_ids");
        NDArray attentionMaskArray = manager.create(attentionMasks);
        attentionMaskArray.setName("attention_mask");
        NDArray tokenTypeArray = manager.create(tokenTypeIds);
        tokenTypeArray.setName("token_type_ids");
        NDList ndList = new NDList();
        ndList.add(inputIdsArray);
        ndList.add(attentionMaskArray);
        ndList.add(tokenTypeArray);
        return ndList;
    }

    @Override
    public List<Output> batchProcessOutput(TranslatorContext ctx, NDList list) {
        NDArray batchArray = list.getFirst();
        int batchSize = (int) batchArray.getShape().get(0);
        List<Output> outputs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            NDArray itemArray = batchArray.get(i);

            Number[] itemData = itemArray.toArray();
            long[] itemShape = itemArray.getShape().getShape();
            DataType dataType = itemArray.getDataType();
            MLResultDataType mlResultDataType = MLResultDataType.valueOf(dataType.name());
            ByteBuffer itemBuffer = itemArray.toByteBuffer();

            ModelTensor tensor = ModelTensor
                .builder()
                .name(SIMILARITY_NAME)
                .data(itemData)
                .shape(itemShape)
                .dataType(mlResultDataType)
                .byteBuffer(itemBuffer)
                .build();

            ModelTensors modelTensorOutput = new ModelTensors(List.of(tensor));
            Output output = new Output(200, "OK");
            output.add(modelTensorOutput.toBytes());
            outputs.add(output);
        }
        return outputs;
    }
}
