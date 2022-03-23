/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.sample.SampleAlgoOutput;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;
import org.opensearch.ml.common.Model;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.Trainable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.utils.ModelSerDeSer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Function(FunctionName.SAMPLE_ALGO)
public class SampleAlgo implements Trainable, Predictable {
    private static final int DEFAULT_SAMPLE_PARAM = -1;
    private int sampleParam;

    public SampleAlgo(){}

    public SampleAlgo(MLAlgoParams parameters) {
        this.sampleParam = Optional.ofNullable(((SampleAlgoParams)parameters).getSampleParam()).orElse(DEFAULT_SAMPLE_PARAM);
    }

    @Override
    public MLOutput predict(DataFrame dataFrame, Model model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for sample algo.");
        }
        AtomicReference<Double> sum = new AtomicReference<>((double) 0);
        dataFrame.forEach(row -> {
            row.forEach(item -> sum.updateAndGet(v -> v + item.doubleValue()));
        });
        return SampleAlgoOutput.builder().sampleResult(sum.get()).build();
    }

    @Override
    public Model train(DataFrame dataFrame) {
        Model model = new Model();
        model.setName(FunctionName.SAMPLE_ALGO.name());
        model.setVersion(1);
        model.setContent(ModelSerDeSer.serialize("This is a sample testing model with parameter: " + sampleParam));
        return model;
    }
}
