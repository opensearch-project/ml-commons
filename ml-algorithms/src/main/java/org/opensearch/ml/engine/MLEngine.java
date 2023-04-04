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
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.engine.encryptor.Encryptor;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {

    public static final String REGISTER_MODEL_FOLDER = "register";
    public static final String DEPLOY_MODEL_FOLDER = "deploy";
    private final String MODEL_REPO = "https://artifacts.opensearch.org/models/ml-models";

    @Getter
    private final Path mlCachePath;
    private final Path mlModelsCachePath;

    private final Encryptor encryptor;

    public MLEngine(Path opensearchDataFolder, Encryptor encryptor) {
        mlCachePath = opensearchDataFolder.resolve("ml_cache");
        mlModelsCachePath = mlCachePath.resolve("models_cache");
        this.encryptor = encryptor;
    }

    public String getPrebuiltModelConfigPath(String modelName, String version, MLModelFormat modelFormat) {
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        return String.format("%s/%s/%s/%s/config.json", MODEL_REPO, modelName, version, format, Locale.ROOT);
    }

    public String getPrebuiltModelPath(String modelName, String version, MLModelFormat modelFormat) {
        int index = modelName.indexOf("/") + 1;
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/sentence-transformers_msmarco-distilbert-base-tas-b-1.0.0-torch_script.zip
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/config.json
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        String modelZipFileName = modelName.substring(index).replace("/", "_") + "-" + version + "-" + format;
        return String.format("%s/%s/%s/%s/%s.zip", MODEL_REPO, modelName, version, format, modelZipFileName, Locale.ROOT);
    }

    public Path getRegisterModelPath(String modelId, String modelName, String version) {
        return getRegisterModelPath(modelId).resolve(version).resolve(modelName);
    }

    public Path getRegisterModelPath(String modelId) {
        return getRegisterModelRootPath().resolve(modelId);
    }

    public Path getRegisterModelRootPath() {
        return mlModelsCachePath.resolve(REGISTER_MODEL_FOLDER);
    }

    public Path getDeployModelPath(String modelId) {
        return getDeployModelRootPath().resolve(modelId);
    }

    public String getDeployModelZipPath(String modelId, String modelName) {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER).resolve(modelId).resolve(modelName) + ".zip";
    }

    public Path getDeployModelRootPath() {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER);
    }

    public Path getDeployModelChunkPath(String modelId, Integer chunkNumber) {
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER)
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
        return mlModelsCachePath.resolve("models");
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

    public Predictable deploy(MLModel mlModel, Map<String, Object> params) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModel(mlModel, params, encryptor);
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

    public String encrypt(String credential) {
        return encryptor.encrypt(credential);
    }
}
