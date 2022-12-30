/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import lombok.Getter;
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
import java.util.Locale;
import java.util.Map;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {

    @Getter
    private final Path djlCachePath;
    private final Path djlModelsCachePath;

    public MLEngine(Path opensearchDataFolder) {
        djlCachePath = opensearchDataFolder.resolve("djl");
        djlModelsCachePath = djlCachePath.resolve("models_cache");
    }

    public String getCIPrebuiltModelConfigPath(String modelName, String version) {
        return String.format("https://ci.opensearch.org/ci/dbc/models/ml-models/%s/%s/config.json", modelName, version, Locale.ROOT);
    }

    public String getCIPrebuiltModelPath(String modelName, String version) {
        int index = modelName.lastIndexOf("/") + 1;
        return String.format("https://ci.opensearch.org/ci/dbc/models/ml-models/%s/%s/%s.zip", modelName, version, modelName.substring(index), Locale.ROOT);
    }

    public Path getUploadModelPath(String modelId, String modelName, String version) {
        return getUploadModelPath(modelId).resolve(version).resolve(modelName);
    }

    public Path getUploadModelPath(String modelId) {
        return getUploadModelRootPath().resolve(modelId);
    }

    public Path getUploadModelRootPath() {
        return djlModelsCachePath.resolve("upload");
    }

    public Path getLoadModelPath(String modelId) {
        return getLoadModelRootPath().resolve(modelId);
    }

    public String getLoadModelZipPath(String modelId, String modelName) {
        return djlModelsCachePath.resolve("load").resolve(modelId).resolve(modelName) + ".zip";
    }

    public Path getLoadModelRootPath() {
        return djlModelsCachePath.resolve("load");
    }

    public Path getLoadModelChunkPath(String modelId, Integer chunkNumber) {
        return djlModelsCachePath.resolve("load")
                .resolve(modelId)
                .resolve("chunks")
                .resolve(chunkNumber + "");
    }

    public Path getModelCachePath(String modelId, String modelName, String version) {
        return getModelCachePath(modelId).resolve(version).resolve(modelName);
    }

    public Path getModelCachePath(String modelId) {
        return getModelCacheRootPath().resolve(modelId);
    }

    public Path getModelCacheRootPath() {
        return djlModelsCachePath.resolve("models");
    }

    public MLModel train(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Trainable trainable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainable.train(mlInput);
    }

    public Predictable load(MLModel mlModel, Map<String, Object> params) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModel(mlModel, params);
        return predictable;
    }

    public MLOutput predict(Input input, MLModel model) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        Predictable predictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (predictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return predictable.predict(mlInput, model);
    }

    public MLOutput trainAndPredict(Input input) {
        validateMLInput(input);
        MLInput mlInput = (MLInput) input;
        TrainAndPredictable trainAndPredictable = MLEngineClassLoader.initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainAndPredictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainAndPredictable.trainAndPredict(mlInput);
    }

    public Output execute(Input input) {
        validateInput(input);
        Executable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
        if (executable == null) {
            throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
        }
        return executable.execute(input);
    }

    private void validateMLInput(Input input) {
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

    private void validateInput(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Input should not be null");
        }
        if (input.getFunctionName() == null) {
            throw new IllegalArgumentException("Function name should not be null");
        }
    }
}
