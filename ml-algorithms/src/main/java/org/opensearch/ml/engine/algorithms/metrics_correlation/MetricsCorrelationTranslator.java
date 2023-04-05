/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MetricsCorrelationTranslator implements Translator<float[][], Output> {

    @Override
    public Batchifier getBatchifier() {
        // Metrics correlation model doesn't support batchify, so we need to return null.
        // otherwise model will throw error.
        return null;
    }

    @Override
    public void prepare(TranslatorContext ctx) {
    }

    @Override
    public NDList processInput(TranslatorContext ctx, float[][] input) {
        FloatBuffer buffer = FloatBuffer.allocate(input.length * input[0].length);
        for (float[] d : input) {
            buffer.put(d);
        }
        buffer.rewind();
        NDArray array = ctx.getNDManager().create(buffer, new Shape(input.length, input[0].length));
        NDList inputNDList = new NDList(array);
        inputNDList.attach(ctx.getNDManager());
        return inputNDList;
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        Output output = new Output(200, "OK");
        List<MCorrModelTensor> outputs = new ArrayList<>();

        Iterator<NDArray> iterator = list.iterator();
        int i = 0;
        float[] range = null;
        float[] event = null;
        long[] metrics = null;
        while (iterator.hasNext()) {
            i += 1;
            NDArray ndArray = iterator.next();
            if (i % 3 == 1 && ndArray.getName().equals("range")) {
                range = ndArray.toFloatArray();
            } else if (i % 3 == 2 && ndArray.getName().equals("metrics")) {
                metrics = ndArray.toLongArray();
            } else {
                event = ndArray.toFloatArray();;
            }
            if (i % 3 == 0) {
                outputs.add(new MCorrModelTensor(range, event, metrics));
                range = null;
                event = null;
                metrics = null;
            }

        }
        MCorrModelTensors modelTensorOutput = new MCorrModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());

        return output;
    }

}
