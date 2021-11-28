package org.opensearch.ml.engine.algorithms.sample;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorParams;
import org.opensearch.ml.common.parameter.MLAlgoName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.SampleAlgoOutput;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.MLAlgoMetaData;
import org.opensearch.ml.engine.annotation.MLAlgorithm;

import java.util.concurrent.atomic.AtomicReference;

@MLAlgorithm("local_sample_calculator")
public class LocalSampleCalculator implements MLAlgo {

    public LocalSampleCalculator() {}

    @Override
    public MLOutput execute(MLAlgoParams params, DataFrame dataFrame) {
        if (params == null) {
            throw new IllegalArgumentException("params should not be null");
        }
        if (!(params instanceof LocalSampleCalculatorParams)) {
            throw new IllegalArgumentException("wrong param type");
        }
        String operation = ((LocalSampleCalculatorParams) params).getOperation();
        switch (operation) {
            case "sum":
                AtomicReference<Double> sum = new AtomicReference<>((double) 0);
                dataFrame.forEach(row -> {
                    row.forEach(item -> sum.updateAndGet(v -> v + item.doubleValue()));
                });
                return new SampleAlgoOutput(sum.get());
            case "max":
                AtomicReference<Double> max = new AtomicReference<>(Double.MIN_VALUE);
                dataFrame.forEach(row -> {
                    row.forEach(item -> {
                        Double value = item.doubleValue();
                        if (max.get() < value) {
                            max.set(value);
                        }
                    });
                });
                return new SampleAlgoOutput(max.get());
            default:
                throw new IllegalArgumentException("can't support this operation " + operation);
        }
    }

    @Override
    public MLAlgoMetaData getMetaData() {
        return MLAlgoMetaData.builder().name(MLAlgoName.SAMPLE_ALGO.name())
                .description("A sample algorithm.")
                .version("1.0")
                .predictable(true)
                .trainable(true)
                .build();
    }
}
