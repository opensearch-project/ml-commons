/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;
import static org.opensearch.ml.common.input.parameter.textembedding.SparseEncodingParameters.EMBEDDING_FORMAT_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.input.parameter.textembedding.AbstractSparseEncodingParameters;
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
        String embeddingFormat = input.getAsString(EMBEDDING_FORMAT_FIELD);
        if (embeddingFormat != null) {
            ctx.setAttachment(EMBEDDING_FORMAT_FIELD, embeddingFormat);
        }
        return super.processInput(ctx, input);
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");
        Object embeddingFormatObject = ctx.getAttachment(EMBEDDING_FORMAT_FIELD);
        String embeddingFormatString = embeddingFormatObject != null
            ? embeddingFormatObject.toString()
            : AbstractSparseEncodingParameters.EmbeddingFormat.LEXICAL.name();

        List<ModelTensor> outputs = new ArrayList<>();
        for (NDArray ndArray : list) {
            String name = ndArray.getName();
            Object result = convertOutput(ndArray, embeddingFormatString);
            Map<String, ?> wrappedMap = Map.of(ML_MAP_RESPONSE_KEY, Collections.singletonList(result));
            ModelTensor tensor = ModelTensor.builder().name(name).dataAsMap(wrappedMap).build();
            outputs.add(tensor);
        }

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    private Object convertOutput(NDArray array, String embeddingFormat) {
        NDArray nonZeroIndices = array.nonzero().squeeze();
        long[] indices = nonZeroIndices.toLongArray();

        if (embeddingFormat.equals(AbstractSparseEncodingParameters.EmbeddingFormat.VECTOR.name())) {
            // Return vector format: {"indices": [...], "values": [...]}
            // Sort indices for vector format
            java.util.Arrays.sort(indices);
            List<Long> indicesList = new ArrayList<>();
            List<Float> valuesList = new ArrayList<>();

            for (long index : indices) {
                indicesList.add(index);
                valuesList.add(array.getFloat(index));
            }

            Map<String, Object> vectorFormat = new HashMap<>();
            vectorFormat.put("indices", indicesList);
            vectorFormat.put("values", valuesList);
            return vectorFormat;
        } else {
            // Return lexical format: {"token": weight, ...}
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
