/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.util.ZipUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;
import static org.opensearch.ml.engine.MLEngine.getLoadModelPath;
import static org.opensearch.ml.engine.MLEngine.getModelCachePath;
import static org.opensearch.ml.engine.ModelHelper.ONNX_FILE_EXTENSION;
import static org.opensearch.ml.engine.ModelHelper.ONNX_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_FILE_EXTENSION;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;

@Log4j2
@Function(FunctionName.TEXT_EMBEDDING)
public class TextEmbeddingModel implements Predictable {

    public static final String SENTENCE_EMBEDDING = "sentence_embedding";
    public static final String MODEL_ZIP_FILE = "model_zip_file";
    public static final String MODEL_HELPER = "model_helper";

    private ModelHelper modelHelper;
    private String modelId;

    private Predictor<Input, Output> predictor;
    private ZooModel model;

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        throw new MLException("model not loaded");
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        if (modelHelper == null || modelId == null) {
            throw new MLException("model not loaded");
        }
        return predictTextEmbedding(modelId, mlInput.getInputDataset());
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params) {
        String engine = model.getModelFormat() == MLModelFormat.TORCH_SCRIPT ? PYTORCH_ENGINE : ONNX_ENGINE;
        File modelZipFile = (File)params.get(MODEL_ZIP_FILE);
        modelHelper = (ModelHelper)params.get(MODEL_HELPER);
        if (modelZipFile == null) {
            throw new IllegalArgumentException("model file is null");
        }
        if (modelHelper == null) {
            throw new IllegalArgumentException("model helper is null");
        }
        modelId = model.getModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }
        loadTextEmbeddingModel(
                modelZipFile,
                modelId,
                model.getName(),
                model.getAlgorithm(),
                model.getVersion(),
                model.getModelConfig(),
                engine
        );
    }

    @Override
    public void close() {
        if (modelHelper != null && modelId != null) {
            modelHelper.deleteFileCache(modelId);
            if (predictor != null) {
                log.debug("close predictor for model {}", modelId);
                predictor.close();
                predictor = null;
            }
            if (model != null) {
                log.debug("close model for model {}", modelId);
                model.close();
                model = null;
            }
        }
    }

    protected void loadTextEmbeddingModel(File modelZipFile, String modelId, String modelName, FunctionName functionName, String version,
                                       MLModelConfig modelConfig,
                                       String engine) {
        try {
            if (FunctionName.TEXT_EMBEDDING != functionName) {
                throw new IllegalArgumentException("wrong function name");
            }
            if (!PYTORCH_ENGINE.equals(engine) && !ONNX_ENGINE.equals(engine)) {
                throw new IllegalArgumentException("unsupported engine");
            }
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    System.setProperty("PYTORCH_PRECXX11", "true");
                    System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                    // DJL will read "/usr/java/packages/lib" if don't set "java.library.path". That will throw
                    // access denied exception
                    System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                    System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
                    System.setProperty("ai.djl.pytorch.num_threads", "1");
                    Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());
                    Path modelPath = getModelCachePath(modelId, modelName, version);
                    File pathFile = new File(modelPath.toUri());
                    if (pathFile.exists()) {
                        FileUtils.deleteDirectory(pathFile);
                    }
                    ZipUtils.unzip(new FileInputStream(modelZipFile), modelPath);
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
                            if (!modelName.equals(name.substring(0, dotIndex))) {
                                file.renameTo(new File(modelPath.resolve(modelName + suffix).toUri()));
                            }
                        }
                    }
                    Map<String, Object> arguments = new HashMap<>();
                    Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                            .setTypes(Input.class, Output.class)
                            .optApplication(Application.UNDEFINED)
                            .optArguments(arguments)
                            .optEngine(engine)
                            .optModelPath(modelPath);
                    TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
                    TextEmbeddingModelConfig.FrameworkType transformersType = textEmbeddingModelConfig.getFrameworkType();
                    if (ONNX_ENGINE.equals(engine)) { //ONNX
                        criteriaBuilder.optTranslator(new ONNXSentenceTransformerTextEmbeddingTranslator());
                    } else { // pytorch
                        if (transformersType == SENTENCE_TRANSFORMERS) {
                            criteriaBuilder.optTranslator(new SentenceTransformerTextEmbeddingTranslator());
                        } else {
                            criteriaBuilder.optTranslatorFactory(new HuggingfaceTextEmbeddingTranslatorFactory());
                        }
                    }
                    Criteria<Input, Output> criteria = criteriaBuilder.build();
                    ZooModel<Input, Output> model = criteria.loadModel();
                    Predictor<Input, Output> predictor = model.newPredictor();
                    this.predictor = predictor;
                    this.model = model;

                    Input input = new Input();
                    input.add("warm up sentence");
                    // First request takes longer time. Predict once to warm up model.
                    this.predictor.predict(input);
                    return null;
                } catch (Exception e) {
                    String errorMessage = "Failed to load model " + modelId;
                    log.error(errorMessage, e);
                    close();
                    throw new MLException(errorMessage, e);
                } finally {
                    deleteFileQuietly(getLoadModelPath(modelId));
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                }
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to load model";
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    protected ModelTensorOutput predictTextEmbedding(String modelId, MLInputDataset inputDataSet) {
        log.debug("start to predict text embedding model {}", modelId);
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<ModelTensorOutput>) () -> {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                if (predictor == null) {
                    throw new MLException("model not loaded.");
                }
                List<ModelTensors> tensorOutputs = new ArrayList<>();
                Output output;
                TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
                ModelResultFilter resultFilter = textDocsInput.getResultFilter();
                for (String doc : textDocsInput.getDocs()) {
                    Input input = new Input();
                    input.add(doc);
                    output = predictor.predict(input);
                    tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
                }
                return new ModelTensorOutput(tensorOutputs);
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to inference text embedding";
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    protected ModelTensors parseModelTensorOutput(Output output, ModelResultFilter resultFilter) {
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
