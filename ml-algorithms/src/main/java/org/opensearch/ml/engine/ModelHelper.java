/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.action.ActionListener;

import java.io.File;
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

import static org.opensearch.ml.engine.MLEngine.getModelCachePath;
import static org.opensearch.ml.engine.MLEngine.getLoadModelPath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;
import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.engine.utils.FileUtils.splitFileIntoChunks;

@NoArgsConstructor
@Log4j2
public class ModelHelper {
    public static final String CHUNK_FILES = "chunk_files";
    public static final String MODEL_SIZE_IN_BYTES = "model_size_in_bytes";
    public static final String MODEL_FILE_HASH = "model_file_hash";
    private static final int CHUNK_SIZE = 10_000_000; // 10MB
    public static final String PYTORCH_FILE_EXTENSION = ".pt";
    public static final String ONNX_FILE_EXTENSION = ".onnx";
    public static final String TOKENIZER_FILE_NAME = "tokenizer.json";
    public static final String PYTORCH_ENGINE = "PyTorch";
    public static final String ONNX_ENGINE = "OnnxRuntime";

    /**
     * Download model from URL and split it into smaller chunks.
     * @param modelId model id
     * @param modelName model name
     * @param version model version
     * @param url model file URL
     * @param listener action listener
     */
    public void downloadAndSplit(String modelId, String modelName, String version, String url, ActionListener<Map<String, Object>> listener) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path modelUploadPath = getUploadModelPath(modelId, modelName, version);
                String modelPath = modelUploadPath +".zip";
                Path modelPartsPath = modelUploadPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                DownloadUtils.download(url, modelPath, new ProgressBar());
                verifyModelZipFile(modelPath);

                List<String> chunkFiles = splitFileIntoChunks(modelZipFile, modelPartsPath, CHUNK_SIZE);
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
        deleteFileQuietly(getModelCachePath(modelId));
        deleteFileQuietly(getLoadModelPath(modelId));
        deleteFileQuietly(getUploadModelPath(modelId));
    }

}
