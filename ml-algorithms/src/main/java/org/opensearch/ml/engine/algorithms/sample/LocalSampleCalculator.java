/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;

import java.util.Comparator;
import java.util.List;

@Data
@NoArgsConstructor
@Function(FunctionName.LOCAL_SAMPLE_CALCULATOR)
public class LocalSampleCalculator implements Executable {

    //TODO: support calculate sum/max/min value from index.
    private Client client;
    private Settings settings;

    public LocalSampleCalculator(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public Output execute(Input input) {
        if (input == null || !(input instanceof LocalSampleCalculatorInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        LocalSampleCalculatorInput sampleCalculatorInput = (LocalSampleCalculatorInput) input;
        String operation = sampleCalculatorInput.getOperation();
        List<Double> inputData = sampleCalculatorInput.getInputData();
        switch (operation) {
            case "sum":
                double sum = inputData.stream().mapToDouble(f -> f.doubleValue()).sum() ;
                return new LocalSampleCalculatorOutput(sum);
            case "max":
                double max = inputData.stream().max(Comparator.naturalOrder()).get();
                return new LocalSampleCalculatorOutput(max);
            case "min":
                double min = inputData.stream().min(Comparator.naturalOrder()).get();
                return new LocalSampleCalculatorOutput(min);
            default:
                throw new IllegalArgumentException("can't support this operation");
        }
    }
}
