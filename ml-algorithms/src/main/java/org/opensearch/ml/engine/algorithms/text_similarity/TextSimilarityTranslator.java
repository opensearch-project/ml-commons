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
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.PairList;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TextSimilarityTranslator extends SentenceTransformerTranslator {
    public final String SIMILARITY_NAME = "similarity";
    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String sentence = input.getAsString(0);
        String context = input.getAsString(1);
        NDManager manager = ctx.getNDManager();
        NDList ndList = new NDList();
        Encoding encodings = tokenizer.encode(sentence, context);
        long[] indices = encodings.getIds();
        long[] attentionMask = encodings.getAttentionMask();

        NDArray indicesArray = manager.create(indices);
        indicesArray.setName(INPUT_IDS);

        NDArray attentionMaskArray = manager.create(attentionMask);
        attentionMaskArray.setName(ATTENTION_MASK);

        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);

        if (requiresTokenTypeIds(ctx)) {
            NDArray tokenTypeArray = manager.create(encodings.getTypeIds());
            tokenTypeArray.setName(TOKEN_TYPE_IDS);
            ndList.add(tokenTypeArray);
        }
        return ndList;
    }

    /**
     * Determines whether the loaded model expects a token_type_ids input.
     */
    private boolean requiresTokenTypeIds(TranslatorContext ctx) {
        try {
            PairList<String, Shape> describedInput = ctx.getModel().getBlock().describeInput();
            if (describedInput == null || describedInput.isEmpty()) {
                return true;
            }
            return describedInput.contains(TOKEN_TYPE_IDS);
        } catch (Exception e) {
            log.warn("Failed to inspect model input signature, sending {} by default", TOKEN_TYPE_IDS, e);
            return true;
        }
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

}
