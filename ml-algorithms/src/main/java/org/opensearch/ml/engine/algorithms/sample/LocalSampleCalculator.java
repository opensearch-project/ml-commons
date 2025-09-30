/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import java.util.Comparator;
import java.util.List;

import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.transport.client.Client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Function(FunctionName.LOCAL_SAMPLE_CALCULATOR)
public class LocalSampleCalculator implements Executable {

    // TODO: support calculate sum/max/min value from index.
    private Client client;
    private Settings settings;

    public LocalSampleCalculator(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener) {
        if (!(input instanceof LocalSampleCalculatorInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        LocalSampleCalculatorInput sampleCalculatorInput = (LocalSampleCalculatorInput) input;
        String operation = sampleCalculatorInput.getOperation();
        List<Double> inputData = sampleCalculatorInput.getInputData();
        switch (operation) {
            case "sum":
                double sum = inputData.stream().mapToDouble(f -> f.doubleValue()).sum();
                listener.onResponse(new LocalSampleCalculatorOutput(sum));
                return;
            case "max":
                double max = inputData.stream().max(Comparator.naturalOrder()).get();
                listener.onResponse(new LocalSampleCalculatorOutput(max));
                return;
            case "min":
                double min = inputData.stream().min(Comparator.naturalOrder()).get();
                listener.onResponse(new LocalSampleCalculatorOutput(min));
                return;
            default:
                throw new IllegalArgumentException("can't support this operation");
        }
    }
}
