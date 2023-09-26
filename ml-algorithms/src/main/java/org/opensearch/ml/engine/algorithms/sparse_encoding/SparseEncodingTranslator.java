/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

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
import org.opensearch.ml.engine.algorithms.SentenceTransformerTranslator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;

public class SparseEncodingTranslator extends SentenceTransformerTranslator {
    private Map<String, Float>  convertOutput(NDArray array)
    {
        Map<String, Float> map = new HashMap<>();
        NDArray nonZeroIndices = array.nonzero().squeeze();

        for (long index : nonZeroIndices.toLongArray()) {
            String s = this.tokenizer.decode(new long[]{index}, true);
            if (!s.isEmpty()){
                map.put(s, array.getFloat(index));
            }
        }
        return map;
    }
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");

        List<ModelTensor> outputs = new ArrayList<>();
        Iterator<NDArray> iterator = list.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            String name = ndArray.getName();
            Map<String, Float> tokenWeightsMap = convertOutput(ndArray);
            Map<String, ?> wrappedMap = Map.of(ML_MAP_RESPONSE_KEY, Collections.singletonList(tokenWeightsMap));
            ModelTensor tensor = ModelTensor.builder()
                    .name(name)
                    .dataAsMap(wrappedMap)
                    .build();
            outputs.add(tensor);
        }

        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }
}
