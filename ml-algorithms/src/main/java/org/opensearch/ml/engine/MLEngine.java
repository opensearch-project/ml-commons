/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.transport.TransportChannel;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * This is the interface to all ml algorithms.
 */
@Log4j2
public class MLEngine {

    public static final String REGISTER_MODEL_FOLDER = "register";
    public static final String DEPLOY_MODEL_FOLDER = "deploy";
    public static final String ANALYSIS_FOLDER = "analysis";
    private final String MODEL_REPO = "https://artifacts.opensearch.org/models/ml-models";

    @Getter
    private final Path mlConfigPath;

    @Getter
    private final Path mlCachePath;
    private final Path mlModelsCachePath;

    private Encryptor encryptor;

    public MLEngine(Path opensearchDataFolder, Encryptor encryptor) {
        this.mlCachePath = opensearchDataFolder.resolve("ml_cache");
        this.mlModelsCachePath = mlCachePath.resolve("models_cache");
        this.mlConfigPath = mlCachePath.resolve("config");
        this.encryptor = encryptor;
    }

    public String getPrebuiltModelMetaListPath() {
        return "https://artifacts.opensearch.org/models/ml-models/model_listing/pre_trained_models.json";
    }

    public String getPrebuiltModelConfigPath(String modelName, String version, MLModelFormat modelFormat) {
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%s/%s/%s/%s/config.json", MODEL_REPO, modelName, version, format);
    }

    public String getPrebuiltModelPath(String modelName, String version, MLModelFormat modelFormat) {
        int index = modelName.indexOf("/") + 1;
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/sentence-transformers_msmarco-distilbert-base-tas-b-1.0.0-torch_script.zip
        // /huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.0/onnx/config.json
        String format = modelFormat.name().toLowerCase(Locale.ROOT);
        String modelZipFileName = modelName.substring(index).replace("/", "_") + "-" + version + "-" + format;
        return String.format(Locale.ROOT, "%s/%s/%s/%s/%s.zip", MODEL_REPO, modelName, version, format, modelZipFileName);
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
        return mlModelsCachePath.resolve(DEPLOY_MODEL_FOLDER).resolve(modelId).resolve("chunks").resolve(chunkNumber + "");
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

    public Path getAnalysisRootPath() {
        return mlModelsCachePath.resolve(ANALYSIS_FOLDER);
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

    public void getConnectorCredential(Connector connector, ActionListener<Map<String, String>> listener) {
        ActionListener<Boolean> decryptSuccessfulListener = ActionListener.wrap(r -> {
            Map<String, String> decryptedCredential = connector.getDecryptedCredential();
            String region = connector.getParameters().get(REGION_FIELD);
            if (region != null) {
                decryptedCredential.putIfAbsent(REGION_FIELD, region);
            }
            listener.onResponse(decryptedCredential);
        }, e -> {
            log.error("Failed to decrypt credentials in connector", e);
            listener.onFailure(e);
        });
        connector.decrypt(PREDICT.name(), encryptor::decrypt, connector.getTenantId(), decryptSuccessfulListener);
    }

    public Predictable deploy(MLModel mlModel, Map<String, Object> params) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModel(mlModel, params, encryptor);
        return predictable;
    }

    public void deploy(MLModel mlModel, Map<String, Object> params, ActionListener<Predictable> listener) {
        Predictable predictable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        predictable.initModelAsync(mlModel, params, encryptor, listener);
    }

    public MLExecutable deployExecute(MLModel mlModel, Map<String, Object> params) {
        MLExecutable executable = MLEngineClassLoader.initInstance(mlModel.getAlgorithm(), null, MLAlgoParams.class);
        executable.initModel(mlModel, params);
        return executable;
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
        TrainAndPredictable trainAndPredictable = MLEngineClassLoader
            .initInstance(mlInput.getAlgorithm(), mlInput.getParameters(), MLAlgoParams.class);
        if (trainAndPredictable == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + mlInput.getAlgorithm());
        }
        return trainAndPredictable.trainAndPredict(mlInput);
    }

    public void execute(Input input, ActionListener<Output> listener, TransportChannel channel) throws Exception {
        validateInput(input);
        if (input.getFunctionName() == FunctionName.METRICS_CORRELATION) {
            MLExecutable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
            if (executable == null) {
                throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
            }
            executable.execute(input, listener);
        } else {
            Executable executable = MLEngineClassLoader.initInstance(input.getFunctionName(), input, Input.class);
            if (executable == null) {
                throw new IllegalArgumentException("Unsupported executable function: " + input.getFunctionName());
            }
            if (channel != null) {
                executable.execute(input, listener, channel);
                return;
            }
            executable.execute(input, listener);
        }
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
            DataFrame dataFrame = ((DataFrameInputDataset) inputDataset).getDataFrame();
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

    public void encrypt(List<String> credentials, String tenantId, ActionListener<List<String>> listener) {
        encryptor.encrypt(credentials, tenantId, listener);
    }

}
