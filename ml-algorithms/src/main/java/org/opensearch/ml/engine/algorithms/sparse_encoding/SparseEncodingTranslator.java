/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.TranslatorContext;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.SentenceTransformerTranslator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;

public class SparseEncodingTranslator extends SentenceTransformerTranslator {
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
}
