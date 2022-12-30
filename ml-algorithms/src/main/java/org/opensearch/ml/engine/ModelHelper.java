/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.upload.MLUploadInput;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.engine.utils.FileUtils.splitFileIntoChunks;

@Log4j2
public class ModelHelper {
    public static final String CHUNK_FILES = "chunk_files";
    public static final String MODEL_SIZE_IN_BYTES = "model_size_in_bytes";
    public static final String MODEL_FILE_HASH = "model_file_hash";
    public static final int CHUNK_SIZE = 10_000_000; // 10MB
    public static final String PYTORCH_FILE_EXTENSION = ".pt";
    public static final String ONNX_FILE_EXTENSION = ".onnx";
    public static final String TOKENIZER_FILE_NAME = "tokenizer.json";
    public static final String PYTORCH_ENGINE = "PyTorch";
    public static final String ONNX_ENGINE = "OnnxRuntime";
    private final MLEngine mlEngine;
    private Gson gson;

    public ModelHelper(MLEngine mlEngine) {
        this.mlEngine = mlEngine;
        gson = new Gson();
    }

    public void downloadPrebuiltModelConfig(String taskId, MLUploadInput uploadInput, ActionListener<MLUploadInput> listener) {
        String modelName = uploadInput.getModelName();
        String version = uploadInput.getVersion();
        boolean loadModel = uploadInput.isLoadModel();
        String[] modelNodeIds = uploadInput.getModelNodeIds();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {

                Path modelUploadPath = mlEngine.getUploadModelPath(taskId, modelName, version);
                String configCacheFilePath = modelUploadPath.resolve("config.json").toString();

                String configFileUrl = mlEngine.getCIPrebuiltModelConfigPath(modelName, version);
                String modelZipFileUrl = mlEngine.getCIPrebuiltModelPath(modelName, version);
                DownloadUtils.download(configFileUrl, configCacheFilePath, new ProgressBar());

                Map<?, ?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(configCacheFilePath))) {
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
                                    case TextEmbeddingModelConfig.POOLING_METHOD_FIELD:
                                        configBuilder.poolingMethod(TextEmbeddingModelConfig.PoolingMethod.from(configEntry.getValue().toString()));
                                        break;
                                    case TextEmbeddingModelConfig.NORMALIZE_RESULT_FIELD:
                                        configBuilder.normalizeResult(Boolean.parseBoolean(configEntry.getValue().toString()));
                                        break;
                                    case TextEmbeddingModelConfig.MODEL_MAX_LENGTH_FIELD:
                                        configBuilder.modelMaxLength(((Double)configEntry.getValue()).intValue());
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
            deleteFileQuietly(mlEngine.getUploadModelPath(taskId));
        }
    }

    /**
     * Download model from URL and split it into smaller chunks.
     * @param taskId task id
     * @param modelName model name
     * @param version model version
     * @param url model file URL
     * @param listener action listener
     */
    public void downloadAndSplit(String taskId, String modelName, String version, String url, ActionListener<Map<String, Object>> listener) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path modelUploadPath = mlEngine.getUploadModelPath(taskId, modelName, version);
                String modelPath = modelUploadPath +".zip";
                Path modelPartsPath = modelUploadPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                log.debug("download model to file {}", modelZipFile.getAbsolutePath());
                DownloadUtils.download(url, modelPath, new ProgressBar());
                verifyModelZipFile(modelPath);

                List<String> chunkFiles = splitFileIntoChunks(modelZipFile, modelPartsPath, CHUNK_SIZE);
                Map<String, Object> result = new HashMap<>();
                result.put(CHUNK_FILES, chunkFiles);
                result.put(MODEL_SIZE_IN_BYTES, modelZipFile.length());

                result.put(MODEL_FILE_HASH, calculateFileHash(modelZipFile));
                deleteFileQuietly(modelZipFile);
                listener.onResponse(result);
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void verifyModelZipFile(String modelZipFilePath) throws IOException {
        boolean hasModelFile = false;
        boolean hasTokenizerFile = false;
        try (ZipFile zipFile = new ZipFile(modelZipFilePath)) {
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = ((ZipEntry) zipEntries.nextElement()).getName();
                if (fileName.endsWith(PYTORCH_FILE_EXTENSION) || fileName.endsWith(ONNX_FILE_EXTENSION)) {
                    if (hasModelFile) {
                        throw new IllegalArgumentException("Find multiple model files, but expected only one");
                    }
                    hasModelFile = true;
                }
                if (fileName.equals(TOKENIZER_FILE_NAME)) {
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

    public void deleteFileCache(String modelId) {
        deleteFileQuietly(mlEngine.getModelCachePath(modelId));
        deleteFileQuietly(mlEngine.getLoadModelPath(modelId));
        deleteFileQuietly(mlEngine.getUploadModelPath(modelId));
    }

}
