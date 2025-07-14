/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;
import static org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.SPARSE_EMBEDDING_FORMAT_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.SentenceTransformerTranslator;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.TranslatorContext;

public class SparseEncodingTranslator extends SentenceTransformerTranslator {

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        String embeddingFormat = input.getAsString(SPARSE_EMBEDDING_FORMAT_FIELD);
        if (embeddingFormat != null) {
            ctx.setAttachment(SPARSE_EMBEDDING_FORMAT_FIELD, embeddingFormat);
        }
        return super.processInput(ctx, input);
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");
        Object embeddingFormatObject = ctx.getAttachment(SPARSE_EMBEDDING_FORMAT_FIELD);
        SparseEmbeddingFormat embeddingFormat = embeddingFormatObject != null
            ? SparseEmbeddingFormat.valueOf(embeddingFormatObject.toString())
            : SparseEmbeddingFormat.WORD;

        List<ModelTensor> outputs = new ArrayList<>();
        for (NDArray ndArray : list) {
            String name = ndArray.getName();
            Object result = convertOutput(ndArray, embeddingFormat);
            Map<String, ?> wrappedMap = Map.of(ML_MAP_RESPONSE_KEY, Collections.singletonList(result));
            ModelTensor tensor = ModelTensor.builder().name(name).dataAsMap(wrappedMap).build();
            outputs.add(tensor);
        }

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    private Object convertOutput(NDArray array, SparseEmbeddingFormat embeddingFormat) {
        NDArray nonZeroIndices = array.nonzero().squeeze();
        long[] indices = nonZeroIndices.toLongArray();

        if (embeddingFormat == SparseEmbeddingFormat.TOKEN_ID) {
            // Return token_id format: {"123": 1.1, "456": 2.2}
            Map<String, Float> tokenIdWeights = new HashMap<>();

            for (long index : indices) {
                tokenIdWeights.put(String.valueOf(index), array.getFloat(index));
            }

            return tokenIdWeights;
        } else {
            // Return word format: {"token": weight, ...}
            Map<String, Float> tokenWeights = new HashMap<>();
            for (long index : indices) {
                String token = this.tokenizer.decode(new long[] { index }, true);
                if (!token.isEmpty()) {
                    tokenWeights.put(token, array.getFloat(index));
                }
            }
            return tokenWeights;
        }
    }
}
