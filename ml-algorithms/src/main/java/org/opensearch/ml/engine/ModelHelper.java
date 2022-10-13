/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import ai.djl.Application;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import ai.djl.util.ZipUtils;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.model.upload.MLUploadInput;
import org.opensearch.ml.engine.algorithms.text_embedding.MLTextEmbeddingTranslatorFactory;
import org.opensearch.ml.engine.algorithms.text_embedding.ONNXSentenceTransformerTextEmbeddingTranslator;
import org.opensearch.ml.engine.algorithms.text_embedding.SentenceTransformerTextEmbeddingTranslator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;
import static org.opensearch.ml.engine.MLEngine.getModelCachePath;
import static org.opensearch.ml.engine.MLEngine.getLoadModelPath;
import static org.opensearch.ml.engine.MLEngine.getLocalPrebuiltModelConfigPath;
import static org.opensearch.ml.engine.MLEngine.getLocalPrebuiltModelPath;
import static org.opensearch.ml.engine.MLEngine.getModelCacheRootPath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;
import static org.opensearch.ml.engine.utils.MLFileUtils.deleteFileQuietly;
import static org.opensearch.ml.engine.utils.MLFileUtils.readAndFragment;

@Log4j2
public class ModelHelper {
    public static final String CHUNK_FILES = "chunk_files";
    public static final String MODEL_SIZE_IN_BYTES = "model_size_in_bytes";
    public static final String MODEL_FILE_HASH = "model_file_hash";
    private static final int CHUNK_SIZE = 10_000_000; // 10MB

    private Map<String, Predictor> predictors;
    private Map<String, TextEmbeddingModelConfig.FrameworkType> modelTransformersTypes;
    private Map<String, ZooModel> models;
    private Gson gson;

    public ModelHelper() {
        modelTransformersTypes = new ConcurrentHashMap<>();
        predictors = new ConcurrentHashMap<>();
        models = new ConcurrentHashMap<>();
        gson = new Gson();
    }

    public void downloadPrebuiltModelConfig(String taskId, MLUploadInput uploadInput, ActionListener<MLUploadInput> listener) {
        String modelName = uploadInput.getModelName();
        Integer version = uploadInput.getVersion();
        boolean loadModel = uploadInput.isLoadModel();
        String[] modelNodeIds = uploadInput.getModelNodeIds();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {

                Path modelUploadPath = getUploadModelPath(taskId, modelName, version);
                String configCacheFilePath = modelUploadPath.resolve("config.json").toString();

                String localConfigFile = getLocalPrebuiltModelConfigPath(modelName, version).toString();
                String configFileUrl = "file://" + localConfigFile;
                String modelZipFileUrl = "file://" + getLocalPrebuiltModelPath(modelName, version);
                DownloadUtils.download(configFileUrl, configCacheFilePath, new ProgressBar());


                Map<?, ?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(localConfigFile))) {
                    config = gson.fromJson(reader, Map.class);
                }

                if (config == null) {
                    listener.onFailure(new IllegalArgumentException("model config not found"));
                    return null;
                }

                MLUploadInput.MLUploadInputBuilder builder = MLUploadInput.builder();

                builder.modelName(modelName).version(version).url(modelZipFileUrl).loadModel(loadModel).modelNodeIds(modelNodeIds);
                config.entrySet().forEach(entry -> {
                    switch (entry.getKey().toString()) {
                        case MLUploadInput.MODEL_FORMAT_FIELD:
                            builder.modelFormat(MLModelFormat.from(entry.getValue().toString()));
                            break;
                        case MLUploadInput.MODEL_CONFIG_FIELD:
                            TextEmbeddingModelConfig.TextEmbeddingModelConfigBuilder configBuilder = TextEmbeddingModelConfig.builder();
                            Map<?, ?> configMap = (Map<?, ?>) entry.getValue();
                            for (Map.Entry<?, ?> configEntry : configMap.entrySet()) {
                                switch (configEntry.getKey().toString()) {
                                    case MLModelConfig.MODEL_TYPE_FIELD:
                                        configBuilder.modelType(configEntry.getValue().toString());
                                        break;
                                    case MLModelConfig.ALL_CONFIG_FIELD:
                                        configBuilder.allConfig(configEntry.getValue().toString());
                                        break;
                                    case TextEmbeddingModelConfig.EMBEDDING_DIMENSION_FIELD:
                                        configBuilder.embeddingDimension(((Double)configEntry.getValue()).intValue());
                                        break;
                                    case TextEmbeddingModelConfig.FRAMEWORK_TYPE_FIELD:
                                        configBuilder.frameworkType(TextEmbeddingModelConfig.FrameworkType.from(configEntry.getValue().toString()));
                                        break;
                                    default:
                                        break;
                                }
                            }
                            builder.modelConfig(configBuilder.build());
                            break;
                        default:
                            break;
                    }
                });
                MLUploadInput mlUploadInput = builder.build();
                listener.onResponse(mlUploadInput);
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        } finally {
            deleteFileQuietly(getUploadModelPath(taskId));
        }
    }

    public void downloadAndSplit(String modelId, String modelName, Integer version, String url, ActionListener<Map<String, Object>> listener) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path modelUploadPath = getUploadModelPath(modelId, modelName, version);
                String modelPath = modelUploadPath +".zip";
                Path modelPartsPath = modelUploadPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                DownloadUtils.download(url, modelPath, new ProgressBar());
                verifyModelZipFile(modelPath);

                ArrayList<String> chunkFiles = readAndFragment(modelZipFile, modelPartsPath, CHUNK_SIZE);
                Map<String, Object> result = new HashMap<>();
                result.put(CHUNK_FILES, chunkFiles);
                result.put(MODEL_SIZE_IN_BYTES, modelZipFile.length());

                result.put(MODEL_FILE_HASH, calculateFileHash(modelZipFile));
                FileUtils.delete(modelZipFile);
                listener.onResponse(result);
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public String calculateFileHash(File modelZipFile) throws IOException {
        ByteSource byteSource = com.google.common.io.Files.asByteSource(modelZipFile);
        HashCode hc = byteSource.hash(Hashing.sha256());
        return hc.toString();
    }

    //TODO: check if model is ONNX or torchscript with content
    private void verifyModelZipFile(String modelZipFilePath) throws IOException {
        boolean hasModelFile = false;
        boolean hasTokenizerFile = false;
        try (ZipFile zipFile = new ZipFile(modelZipFilePath)) {
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = ((ZipEntry) zipEntries.nextElement()).getName();
                if (fileName.endsWith(".pt") || fileName.endsWith(".onnx")) {
                    if (hasModelFile) {
                        throw new IllegalArgumentException("Find multiple model files");
                    }
                    hasModelFile = true;
                }
                if (fileName.equals("tokenizer.json")) {
                    if (hasTokenizerFile) {
                        throw new IllegalArgumentException("Find tokenizer files");
                    }
                    hasTokenizerFile = true;
                }
            }
        }
        if (!hasModelFile) {
            throw new IllegalArgumentException("Can't find model file");
        }
        if (!hasTokenizerFile) {
            throw new IllegalArgumentException("Can't find tokenizer file");
        }
    }

    public void loadModel(File modelZipFile, String modelId, String modelName, FunctionName functionName, Integer version,
                          MLModelConfig modelConfig,
                          String engine) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    System.setProperty("PYTORCH_PRECXX11", "true");
                    System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                    System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                    Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());
                    Engine.debugEnvironment();
                    Path modelPath = getModelCachePath(modelId, modelName, version);
                    File pathFile = new File(modelPath.toUri());
                    if (pathFile.exists()) {
                        FileUtils.deleteDirectory(pathFile);
                    }
                    ZipUtils.unzip(new FileInputStream(modelZipFile), modelPath);
                    boolean findModelFile = false;
                    for (File file : pathFile.listFiles()) {
                        String name = file.getName();
                        if (name.endsWith(".pt") || name.endsWith(".onnx")) {
                            if (findModelFile) {
                                throw new IllegalArgumentException("found multiple models");
                            }
                            findModelFile = true;
                            String suffix = name.substring(name.lastIndexOf("."));
                            file.renameTo(new File(modelPath.resolve(modelName + suffix).toUri()));
                        }
                    }
                    try {
                        Map<String, Object> arguments = new HashMap<>();
                        arguments.put("engine", engine);
                        Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                                .setTypes(Input.class, Output.class)
                                .optApplication(Application.UNDEFINED)
                                .optArguments(arguments)
                                .optModelPath(modelPath);
                        if (functionName == FunctionName.TEXT_EMBEDDING) {
                            TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
                            TextEmbeddingModelConfig.FrameworkType transformersType = textEmbeddingModelConfig.getFrameworkType();
                            if ("OnnxRuntime".equals(engine)) {
                                criteriaBuilder.optTranslator(new ONNXSentenceTransformerTextEmbeddingTranslator());
                            } else {
                                if (transformersType == SENTENCE_TRANSFORMERS) {
                                    criteriaBuilder.optTranslator(new SentenceTransformerTextEmbeddingTranslator());
                                } else {
                                    criteriaBuilder.optTranslatorFactory(new MLTextEmbeddingTranslatorFactory());
                                }
                            }
                            modelTransformersTypes.put(modelId, transformersType);
                        }
                        Criteria<Input, Output> criteria = criteriaBuilder.build();
                        ZooModel<Input, Output> model = criteria.loadModel();
                        Predictor<Input, Output> predictor = model.newPredictor();
                        predictors.put(modelId, predictor);
                        models.put(modelId, model);
                    } catch (Exception e) {
                        String errorMessage = "Failed to load model " + modelName + ", version: " + version;
                        log.error(errorMessage, e);
                        removeModel(modelId);
                        throw new MLException(errorMessage);
                    } finally {
                        deleteFileQuietly(getLoadModelPath(modelId));
                    }
                    return null;
                } finally {
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                }
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to load model";
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    private void removeModel(String modelId) {
        predictors.remove(modelId);
        models.remove(modelId);
        modelTransformersTypes.remove(modelId);
    }

    public void unloadModel(String modelId) {
        deleteFileCache(modelId);
        if (predictors.containsKey(modelId)) {
            log.debug("unload mode: close and remove predictor {}", modelId);
            predictors.get(modelId).close();
            predictors.remove(modelId);
        }
        if (models.containsKey(modelId)) {
            log.debug("unload mode: close and remove model {}", modelId);
            models.get(modelId).close();
            models.remove(modelId);
        }
    }

    private void deleteFileCache(String modelId) {
        deleteFileQuietly(getModelCachePath(modelId));
        deleteFileQuietly(getLoadModelPath(modelId));
        deleteFileQuietly(getUploadModelPath(modelId));
    }

    public void cleanUpFileCache() {//TODO: clean all files
        Path path = getModelCacheRootPath();
        File modelCacheFolder = new File(path.toUri());
        for (File file: modelCacheFolder.listFiles()) {
            String modelId = file.getName();
            if (!predictors.containsKey(modelId)) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    public Predictor getPredictor(String modelId) {
        return predictors.get(modelId);
    }

    public TextEmbeddingModelConfig.FrameworkType getFrameworkType(String modelId) {
        return modelTransformersTypes.get(modelId);
    }
}
