/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.utils.ZipUtils;

import java.io.File;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opensearch.ml.engine.ModelHelper.ONNX_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.ONNX_FILE_EXTENSION;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_FILE_EXTENSION;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;

@Log4j2
public abstract class DLModel implements Predictable {
    public static final String MODEL_ZIP_FILE = "model_zip_file";
    public static final String MODEL_HELPER = "model_helper";
    public static final String ML_ENGINE = "ml_engine";
    protected ModelHelper modelHelper;
    protected MLEngine mlEngine;
    protected String modelId;

    protected Predictor<Input, Output>[] predictors;
    protected ZooModel[] models;
    protected Device[] devices;
    protected AtomicInteger nextDevice = new AtomicInteger(0);

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        throw new IllegalArgumentException("model not deployed");
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        if (modelHelper == null || modelId == null) {
            throw new IllegalArgumentException("model not deployed");
        }
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<ModelTensorOutput>) () -> {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                if (predictors == null) {
                    throw new MLException("model not deployed.");
                }
                return predict(modelId, mlInput);
            });
        } catch (Throwable e) {
            String errorMsg = "Failed to inference " + mlInput.getAlgorithm() + " model: " + modelId;
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    protected Predictor<Input, Output> getPredictor() {
        int currentDevice = nextDevice.getAndIncrement();
        if (currentDevice > devices.length - 1) {
            currentDevice = currentDevice % devices.length;
            nextDevice.set(currentDevice + 1);
        }
        return predictors[currentDevice];
    }

    public abstract ModelTensorOutput predict(String modelId, MLInput input) throws TranslateException;

    @Override
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        String engine;
        switch (model.getModelFormat()) {
            case TORCH_SCRIPT:
                engine = PYTORCH_ENGINE;
                break;
            case ONNX:
                engine = ONNX_ENGINE;
                break;
            default:
                throw new IllegalArgumentException("unsupported engine");
        }

        File modelZipFile = (File)params.get(MODEL_ZIP_FILE);
        modelHelper = (ModelHelper)params.get(MODEL_HELPER);
        mlEngine = (MLEngine)params.get(ML_ENGINE);
        if (modelZipFile == null) {
            throw new IllegalArgumentException("model file is null");
        }
        if (modelHelper == null) {
            throw new IllegalArgumentException("model helper is null");
        }
        if (mlEngine == null) {
            throw new IllegalArgumentException("ML engine is null");
        }
        modelId = model.getModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }
        if (!FunctionName.isDLModel(model.getAlgorithm())) {
            throw new IllegalArgumentException("wrong function name");
        }
        loadModel(
                modelZipFile,
                modelId,
                model.getName(),
                model.getVersion(),
                model.getModelConfig(),
                engine
        );
    }

    @Override
    public void close() {
        if (modelHelper != null && modelId != null) {
            modelHelper.deleteFileCache(modelId);
            if (predictors != null) {
                closePredictors(predictors);
                predictors = null;
            }
            if (models != null) {
                closeModels(models);
                models = null;
            }
        }
    }

    public abstract Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig);

    public abstract TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig);

    public Map<String, Object> getArguments(MLModelConfig modelConfig) {
        return null;
    }

    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {}

    protected void loadModel(File modelZipFile, String modelId, String modelName, String version,
                             MLModelConfig modelConfig,
                             String engine) {
        try {
            if (!PYTORCH_ENGINE.equals(engine) && !ONNX_ENGINE.equals(engine)) {
                throw new IllegalArgumentException("unsupported engine");
            }
            List<Predictor<Input, Output>> predictorList = new ArrayList<>();
            List<ZooModel<Input, Output>> modelList = new ArrayList<>();
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    System.setProperty("PYTORCH_PRECXX11", "true");
                    System.setProperty("DJL_CACHE_DIR", mlEngine.getMlCachePath().toAbsolutePath().toString());
                    // DJL will read "/usr/java/packages/lib" if don't set "java.library.path". That will throw
                    // access denied exception
                    System.setProperty("java.library.path", mlEngine.getMlCachePath().toAbsolutePath().toString());
                    System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
                    System.setProperty("ai.djl.pytorch.num_threads", "1");
                    Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());
                    Path modelPath = mlEngine.getModelCachePath(modelId, modelName, version);
                    File pathFile = new File(modelPath.toUri());
                    if (pathFile.exists()) {
                        FileUtils.deleteDirectory(pathFile);
                    }
                    ZipUtils.unzip(modelZipFile, modelPath);
                    boolean findModelFile = false;
                    for (File file : pathFile.listFiles()) {
                        String name = file.getName();
                        if (name.endsWith(PYTORCH_FILE_EXTENSION) || name.endsWith(ONNX_FILE_EXTENSION)) {
                            if (findModelFile) {
                                throw new IllegalArgumentException("found multiple models");
                            }
                            findModelFile = true;
                            int dotIndex = name.lastIndexOf(".");
                            String suffix = name.substring(dotIndex);
                            String targetModelFileName = modelPath.getFileName().toString();
                            if (!targetModelFileName.equals(name.substring(0, dotIndex))) {
                                file.renameTo(new File(modelPath.resolve(targetModelFileName + suffix).toUri()));
                            }
                        }
                    }
                    devices = Engine.getEngine(engine).getDevices();
                    for (int i = 0; i < devices.length; i++) {
                        log.debug("load model {} to device {}: {}", modelId, i, devices[i]);
                        Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                                .setTypes(Input.class, Output.class)
                                .optApplication(Application.UNDEFINED)
                                .optEngine(engine)
                                .optDevice(devices[i])
                                .optModelPath(modelPath);
                        Translator translator = getTranslator(engine, modelConfig);
                        TranslatorFactory translatorFactory = getTranslatorFactory(engine, modelConfig);
                        if (translatorFactory != null) {
                            criteriaBuilder.optTranslatorFactory(translatorFactory);
                        } else if (translator != null) {
                            criteriaBuilder.optTranslator(translator);
                        }

                        Map<String, Object> arguments = getArguments(modelConfig);
                        if (arguments != null && arguments.size() > 0) {
                            for (Map.Entry<String,Object> entry : arguments.entrySet()) {
                                criteriaBuilder.optArgument(entry.getKey(), entry.getValue());
                            }
                        }

                        Criteria<Input, Output> criteria = criteriaBuilder.build();
                        ZooModel<Input, Output> model = criteria.loadModel();
                        Predictor<Input, Output> predictor = model.newPredictor();
                        predictorList.add(predictor);
                        modelList.add(model);

                        // First request takes longer time. Predict once to warm up model.
                        warmUp(predictor, modelId, modelConfig);
                    }
                    if (predictorList.size() > 0) {
                        this.predictors = predictorList.toArray(new Predictor[0]);
                        predictorList.clear();
                    }
                    if (modelList.size() > 0) {
                        this.models = modelList.toArray(new ZooModel[0]);
                        modelList.clear();
                    }
                    log.info("Model {} is successfully deployed on {} devices", modelId, devices.length);
                    return null;
                } catch (Throwable e) {
                    String errorMessage = "Failed to deploy model " + modelId;
                    log.error(errorMessage, e);
                    close();
                    if (predictorList.size() > 0) {
                        closePredictors(predictorList.toArray(new Predictor[0]));
                        predictorList.clear();
                    }
                    if (modelList.size() > 0) {
                        closeModels(modelList.toArray(new ZooModel[0]));
                        modelList.clear();
                    }
                    throw new MLException(errorMessage, e);
                } finally {
                    deleteFileQuietly(mlEngine.getDeployModelPath(modelId));
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                }
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to deploy model " + modelId;
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    protected void closePredictors(Predictor[] predictors) {
        log.debug("will close {} predictor for model {}", predictors.length, modelId);
        for (Predictor<Input, Output> predictor : predictors) {
            predictor.close();
        }
    }

    protected void closeModels(ZooModel[] models) {
        log.debug("will close {} zoo model for model {}", models.length, modelId);
        for (ZooModel model : models) {
            model.close();
        }
    }

    /**
     * Parse model output to model tensor output and apply result filter.
     * @param output model output
     * @param resultFilter result filter
     * @return model tensor output
     */
    public ModelTensors parseModelTensorOutput(Output output, ModelResultFilter resultFilter) {
        if (output == null) {
            throw new MLException("No output generated");
        }
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        if (resultFilter != null) {
            tensorOutput.filter(resultFilter);
        }
        return tensorOutput;
    }

}
