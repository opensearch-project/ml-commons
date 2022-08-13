/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.custom;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import ai.djl.util.ZipUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelInput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;
import static org.opensearch.ml.engine.MLEngine.DJL_CUSTOM_MODELS_PATH;
import static org.opensearch.ml.engine.MLEngine.getCustomModelPath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;

@Log4j2
public class CustomModelManager {
    /**
     * Key: modelName + ":" + version;
     * Value: predictor
     */
    private Map<String, Predictor> predictors;
    /**
     * Key: modelName + ":" + version;
     * Value: ZooModel
     */
    private Map<String, ZooModel> models;
    private static final int CHUNK_SIZE = 10_000_000; // 10MB

    private final GeneralSentenceTransformerTranslator generalSentenceTransformerTranslator;

    public CustomModelManager() {
        predictors = new ConcurrentHashMap<>();
        models = new ConcurrentHashMap<>();
        generalSentenceTransformerTranslator = new GeneralSentenceTransformerTranslator();
    }

    public void downloadAndSplit(String modelName, Integer version, String url, ActionListener<ArrayList<String>> listener) throws PrivilegedActionException {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path modelUploadPath = getUploadModelPath(modelName, version);
                String modelPath = modelUploadPath +".zip";
                Path modelPartsPath = modelUploadPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                DownloadUtils.download(url, modelPath, new ProgressBar());
                ArrayList<String> nameList = readAndFragment(modelZipFile, modelPartsPath, CHUNK_SIZE);
                FileUtils.delete(modelZipFile);
                listener.onResponse(nameList);
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private ArrayList<String> readAndFragment(File file, Path outputPath, int chunkSize) throws IOException {
        int fileSize = (int) file.length();
        ArrayList<String> nameList = new ArrayList<>();
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(file))){
            int numberOfChunk = 0;
            int totalBytesRead = 0;
            while (totalBytesRead < fileSize) {
                String partName = numberOfChunk + "";
                int bytesRemaining = fileSize - totalBytesRead;
                if (bytesRemaining < chunkSize) {
                    chunkSize = bytesRemaining;
                }
                byte[] temporary = new byte[chunkSize];
                int bytesRead = inStream.read(temporary, 0, chunkSize);
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead;
                    numberOfChunk++;
                }
                Path partFileName = outputPath.resolve(partName + "");
                write(temporary, partFileName.toString());
                nameList.add(partFileName.toString());
            }
        }
         return nameList;
    }

    public void write(byte[] data, String destinationFileName) throws IOException {
        File file = new File(destinationFileName);
        write(data, file, false);
    }

    public void write(byte[] data, File destinationFile, boolean append) throws IOException {
        FileUtils.createParentDirectories(destinationFile);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(destinationFile, append))){
            output.write(data);
        }
    }

    public void mergeFiles(File[] files, File mergedFile) {
        boolean failed = false;
        for (int i = 0; i< files.length ; i++) {
            File f = files[i];
            try (InputStream inStream = new BufferedInputStream(new FileInputStream(f))) {
                if (!failed) {
                    int fileLength = (int) f.length();
                    byte fileContent[] = new byte[fileLength];
                    inStream.read(fileContent, 0, fileLength);

                    write(fileContent, mergedFile, true);
                }
                FileUtils.deleteQuietly(f);
                if (i == files.length - 1) {
                    FileUtils.deleteQuietly(f.getParentFile());
                }
            } catch (IOException e) {
                log.error("Failed to merge file " + f.getAbsolutePath() + " to " + mergedFile.getAbsolutePath());
                failed = true;
            }
        }
        if (failed) {
            FileUtils.deleteQuietly(mergedFile);
            throw new MLException("Failed to merge model chunks");
        }
    }

    public void loadModel(String modelZipPath, String modelName, Integer version, String engine) throws PrivilegedActionException {
        AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());

                Path modelPath = getCustomModelPath(modelName, version);
                File pathFile = new File(modelPath.toUri());
                if (pathFile.exists()) {
                    FileUtils.deleteDirectory(pathFile);
                }
                File modelZipFile = new File(modelZipPath);
                ZipUtils.unzip(new FileInputStream(modelZipFile), modelPath);

                try {
                    Map<String, Object> arguments = new HashMap<>();
                    arguments.put("engine", engine);
                    Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                            .setTypes(Input.class, Output.class)
                            .optApplication(Application.UNDEFINED)
                            .optArguments(arguments)
                            .optModelPath(modelPath);
                    if (modelName.startsWith("sentence_transformer")) {
                        criteriaBuilder.optTranslator(generalSentenceTransformerTranslator);
                    }
                    Criteria<Input, Output> criteria = criteriaBuilder.build();
                    ZooModel<Input, Output> model = criteria.loadModel();
                    Predictor<Input, Output> predictor = model.newPredictor();
                    String key = cacheKey(modelName, version);
                    predictors.put(key, predictor);
                    models.put(key, model);
                } catch (Exception e) {
                    String errorMessage = "Failed to load model " + modelName + ", version: " + version;
                    log.error(errorMessage, e);
                    throw new MLException(errorMessage);
                } finally {
                    FileUtils.deleteQuietly(modelZipFile);
                }
                return null;
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
    }

    public String predict(MLPredictModelInput predictInput) throws IOException, PrivilegedActionException {
        return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            String modelName = predictInput.getModelName();
            Integer version = predictInput.getVersion();
            String key = cacheKey(modelName, version);
            if (!predictors.containsKey(key)) {
                throw new MLResourceNotFoundException("Model doesn't exist. Please upload and load model first.");
            }

            Input input = new Input();
            if (predictInput.getImageUrl() != null) {
                input.add("url", predictInput.getImageUrl());
            }
            if (predictInput.getQuestion() != null) {
                input.add("question", predictInput.getQuestion());
            }
            if (predictInput.getDoc() != null) {
                input.add("doc", predictInput.getDoc());
            }
            Predictor<Input, Output> predictor = predictors.get(key);
            Output output = predictor.predict(input);
            String content = output.getAsString(0);
            return content;
        });
    }


    public Map<String, String> unloadModel(String[] modelNames, int[] versions) {
        Map<String, String> modelUnloadStatus = new HashMap<>();
        for (String modelName : modelNames) {
            for (int version : versions) {
                String key = cacheKey(modelName, version);
                if (predictors.containsKey(key)) {
                    predictors.get(key).close();
                    predictors.remove(key);
                    modelUnloadStatus.put(key, "deleted");
                    log.info("Unload model {}, version: {}", modelName, version);
                }
                if (models.containsKey(key)) {
                    models.get(key).close();
                    models.remove(key);
                }
            }
        }
        return modelUnloadStatus;
    }

    public static String cacheKey(String modelName, int version) {
        return modelName + ":" + version;
    }
}
