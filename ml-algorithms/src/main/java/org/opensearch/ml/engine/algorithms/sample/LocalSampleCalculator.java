/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.engine.algorithms.sample;

import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.parameter.SampleAlgoOutput;
import org.opensearch.ml.engine.Executable;

import java.util.Comparator;
import java.util.List;

public class LocalSampleCalculator implements Executable {

    private LocalSampleCalculatorInput sampleCalculatorInput;
    public LocalSampleCalculator(Input input) {
        sampleCalculatorInput = (LocalSampleCalculatorInput) input;
    }

    @Override
    public Output execute(Input input) {
        if (input == null || !(input instanceof LocalSampleCalculatorInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        String operation = sampleCalculatorInput.getOperation();
        List<Double> inputData = sampleCalculatorInput.getInputData();
        switch (operation) {
            case "sum":
                double sum = inputData.stream().mapToDouble(f -> f.doubleValue()).sum();
                return new SampleAlgoOutput(sum);
            case "max":
                double max = inputData.stream().max(Comparator.naturalOrder()).get();
                return new SampleAlgoOutput(max);
            case "min":
                double min = inputData.stream().min(Comparator.naturalOrder()).get();
                return new SampleAlgoOutput(min);
            default:
                throw new IllegalArgumentException("can't support this operation " + operation);
        }
    }

}
