/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.Output;

import java.nio.file.Path;
import java.util.Map;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {

    public static Path DJL_CACHE_PATH;
    public static Path DJL_MODELS_CACHE_PATH;
    public static void setDjlCachePath(Path opensearchDataFolder) {
        DJL_CACHE_PATH = opensearchDataFolder.resolve("djl");
        DJL_MODELS_CACHE_PATH = DJL_CACHE_PATH.resolve("models_cache");
    }

    public static Path getUploadModelPath(String modelId, String modelName, String version) {
        return getUploadModelPath(modelId).resolve(version).resolve(modelName);
    }

    public static Path getUploadModelPath(String modelId) {
        return getUploadModelRootPath().resolve(modelId);
    }

    public static Path getUploadModelRootPath() {
        return DJL_MODELS_CACHE_PATH.resolve("upload");
    }

    public static Path getLoadModelPath(String modelId) {
        return getLoadModelRootPath().resolve(modelId);
    }

    public static String getLoadModelZipPath(String modelId, String modelName) {
        return DJL_MODELS_CACHE_PATH.resolve("load").resolve(modelId).resolve(modelName) + ".zip";
    }

    public static Path getLoadModelRootPath() {
        return DJL_MODELS_CACHE_PATH.resolve("load");
    }

    public static Path getLoadModelChunkPath(String modelId, Integer chunkNumber) {
        return DJL_MODELS_CACHE_PATH.resolve("load")
                .resolve(modelId)
                .resolve("chunks")
                .resolve(chunkNumber + "");
    }

    public static Path getModelCachePath(String modelId, String modelName, String version) {
        return getModelCachePath(modelId).resolve(version).resolve(modelName);
    }

    public static Path getModelCachePath(String modelId) {
        return getModelCacheRootPath().resolve(modelId);
    }

    public static Path getModelCacheRootPath() {
        return DJL_MODELS_CACHE_PATH.resolve("models");
    }

    public static MLModel train(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Trainable trainable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainable.train(mlInput.getInputDataset());
    }

    public static Predictable load(MLModel mlModel, Map<String, Object> params) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModel(mlModel, params);
        return predictable;
    }

    public static MLOutput predict(Input input, MLModel model) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Predictable predictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (predictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return predictable.predict(mlInput.getInputDataset(), model);
    }

    public static MLOutput trainAndPredict(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        TrainAndPredictable trainAndPredictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainAndPredictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainAndPredictable.trainAndPredict(mlInput.getInputDataset());
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
        MLInputDataset inputDataset = mlInput.getInputDataset();
        if (inputDataset == null) {
            throw new IllegalArgumentException("Input data set should not be null");
        }
        if (inputDataset instanceof DataFrameInputDataset) {
            DataFrame dataFrame = ((DataFrameInputDataset)inputDataset).getDataFrame();
            if (dataFrame == null || dataFrame.size() == 0) {
                throw new IllegalArgumentException("Input data frame should not be null or empty");
            }
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
