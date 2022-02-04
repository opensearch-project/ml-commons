/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.Model;
import org.opensearch.ml.common.parameter.Output;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {

    public static Model train(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Trainable trainable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainable.train(mlInput.getDataFrame());
    }

    public static MLOutput predict(Input input, Model model) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Predictable predictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (predictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return predictable.predict(mlInput.getDataFrame(), model);
    }

    public static MLOutput trainAndPredict(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        TrainAndPredictable trainAndPredictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainAndPredictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainAndPredictable.trainAndPredict(mlInput.getDataFrame());
    }

    public static Output execute(Input input) {
        validateInput(input);
        Executable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
        if (executable == null) {
            throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
        }
        return executable.execute(input);
    }

    private static void validateMLInput(Input input) {
        validateInput(input);
        if (!(input instanceof MLInput)) {
            throw new IllegalArgumentException("Input should be MLInput");
        }
        MLInput mlInput = (MLInput) input;
        DataFrame dataFrame = mlInput.getDataFrame();
        if (dataFrame == null || dataFrame.size() == 0) {
            throw new IllegalArgumentException("Input data frame should not be null or empty");
        }
    }

    private static void validateInput(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Input should not be null");
        }
        if (input.getFunctionName() == null) {
            throw new IllegalArgumentException("Function name should not be null");
        }
    }
}
