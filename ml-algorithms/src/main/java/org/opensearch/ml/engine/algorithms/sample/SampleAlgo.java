/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.sample.SampleAlgoOutput;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.Trainable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.utils.ModelSerDeSer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Function(FunctionName.SAMPLE_ALGO)
public class SampleAlgo implements Trainable, Predictable {
    public static final String VERSION = "1.0.0";
    private static final int DEFAULT_SAMPLE_PARAM = -1;
    private int sampleParam;

    public SampleAlgo(){}

    public SampleAlgo(MLAlgoParams parameters) {
        this.sampleParam = Optional.ofNullable(((SampleAlgoParams)parameters).getSampleParam()).orElse(DEFAULT_SAMPLE_PARAM);
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params) {
        throw new MLException("Sample Algo doesn't support init model");
    }

    @Override
    public void close() {
        sampleParam = DEFAULT_SAMPLE_PARAM;
    }

    @Override
    public MLOutput predict(MLInputDataset inputDataset) {
        AtomicReference<Double> sum = new AtomicReference<>((double) 0);
        DataFrame dataFrame = ((DataFrameInputDataset)inputDataset).getDataFrame();
        dataFrame.forEach(row -> {
            row.forEach(item -> sum.updateAndGet(v -> v + item.doubleValue()));
        });
        return SampleAlgoOutput.builder().sampleResult(sum.get()).build();
    }

    @Override
    public MLOutput predict(MLInputDataset inputDataset, MLModel model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for sample algo.");
        }
        return predict(inputDataset);
    }

    @Override
    public MLModel train(MLInputDataset inputDataset) {
        MLModel model = MLModel.builder()
                .name(FunctionName.SAMPLE_ALGO.name())
                .algorithm(FunctionName.SAMPLE_ALGO)
                .version(VERSION)
                .content(ModelSerDeSer.serializeToBase64("This is a sample testing model with parameter: " + sampleParam))
                .build();
        return model;
    }
}
