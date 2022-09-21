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
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.dataset.MLModelResultFilter;
import org.opensearch.ml.common.dataset.TextInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelTaskType;
import org.opensearch.ml.common.output.custom_model.MLBatchModelTensorOutput;
import org.opensearch.ml.common.output.custom_model.MLModelTensorOutput;
import org.opensearch.ml.common.transport.model.unload.UnloadModelInput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;
import static org.opensearch.ml.engine.MLEngine.getCustomModelPath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;

@Log4j2
public class CustomModelManager {
    public static final String CHUNK_FILES = "chunk_files";
    public static final String MODEL_SIZE_IN_BYTES = "model_size_in_bytes";
    public static final String MODEL_FILE_MD5 = "model_file_md5";
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

    public CustomModelManager() {
        predictors = new ConcurrentHashMap<>();
        models = new ConcurrentHashMap<>();
    }

    public void downloadAndSplit(String modelId, String modelName, Integer version, String url, ActionListener<Map<String, Object>> listener) throws PrivilegedActionException {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path modelUploadPath = getUploadModelPath(modelId, modelName, version);
                String modelPath = modelUploadPath +".zip";
                Path modelPartsPath = modelUploadPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                DownloadUtils.download(url, modelPath, new ProgressBar());
                ArrayList<String> chunkFiles = readAndFragment(modelZipFile, modelPartsPath, CHUNK_SIZE);
                Map<String, Object> result = new HashMap<>();
                result.put(CHUNK_FILES, chunkFiles);
                result.put(MODEL_SIZE_IN_BYTES, modelZipFile.length());

                ByteSource byteSource = com.google.common.io.Files.asByteSource(modelZipFile);
                HashCode hc = byteSource.hash(Hashing.md5());
                result.put(MODEL_FILE_MD5, hc.toString());
                FileUtils.delete(modelZipFile);
                listener.onResponse(result);
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

    public void loadModel(File modelZipFile, String modelId, String modelName, MLModelTaskType modelTaskType, Integer version, String engine) throws PrivilegedActionException {
        AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());

                //TODO: check only one .pt or .onnx file exits, and rename it as modelName.pt/onnx
                Path modelPath = getCustomModelPath(modelId, modelName, version);
                File pathFile = new File(modelPath.toUri());
                if (pathFile.exists()) {
                    FileUtils.deleteDirectory(pathFile);
                }
                ZipUtils.unzip(new FileInputStream(modelZipFile), modelPath);

                try {
                    Map<String, Object> arguments = new HashMap<>();
                    arguments.put("engine", engine);
                    Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                            .setTypes(Input.class, Output.class)
                            .optApplication(Application.UNDEFINED)
                            .optArguments(arguments)
                            .optModelPath(modelPath);
                    switch (modelTaskType) {
                        case TEXT_EMBEDDING:
                            criteriaBuilder.optTranslator(new GeneralSentenceTransformerTranslator());
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported task type");
                    }
                    Criteria<Input, Output> criteria = criteriaBuilder.build();
                    ZooModel<Input, Output> model = criteria.loadModel();
                    Predictor<Input, Output> predictor = model.newPredictor();
                    predictors.put(modelId, predictor);
                    models.put(modelId, model);
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

    public MLBatchModelTensorOutput predict(String modelId, MLInput mlInput) throws IOException, PrivilegedActionException {
        return AccessController.doPrivileged((PrivilegedExceptionAction<MLBatchModelTensorOutput>) () -> {
            long start = System.currentTimeMillis();
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            if (!predictors.containsKey(modelId)) {
                throw new MLResourceNotFoundException("Model doesn't exist. Please upload and load model first.");
            }

            Predictor<Input, Output> predictor = predictors.get(modelId);

            List<Output> outputs = new ArrayList<>();
            MLModelTaskType mlModelTaskType = mlInput.getMlModelTaskType();
            if (mlModelTaskType != null) {
                switch (mlModelTaskType) {
                    case TEXT_EMBEDDING:
                        TextInputDataSet textDocsInput= (TextInputDataSet)mlInput.getInputDataset();
                        for (String doc : textDocsInput.getDocs()) {
                            Input input = new Input();
                            input.add("doc", doc);
                            Output output = predictor.predict(input);
                            outputs.add(output);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("unknown model task type");
                }
            }

            MLModelResultFilter resultFilter = ((TextInputDataSet) mlInput.getInputDataset()).getResultFilter();
            List<MLModelTensorOutput> tensorOutputs = new ArrayList<>();
            for (Output output : outputs) {
                byte[] bytes = output.getData().getAsBytes();
                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                StreamInput streamInput = BytesReference.fromByteBuffer(byteBuffer).streamInput();
                MLModelTensorOutput tensorOutput = new MLModelTensorOutput(streamInput);
                streamInput.close();
                tensorOutput.filter(resultFilter);
                tensorOutputs.add(tensorOutput);
            }
            long end = System.currentTimeMillis();
            log.info("Inference time for model {}: {} millisecond", modelId, (end - start));
            return new MLBatchModelTensorOutput(tensorOutputs);
        });
    }

    public Map<String, String> unloadModel(UnloadModelInput unloadModelInput) {
        Map<String, String> modelUnloadStatus = new HashMap<>();
        String[] modelIds = unloadModelInput.getModelIds();
        if (modelIds != null && modelIds.length > 0) {
            for (String modelId : modelIds) {
                if (predictors.containsKey(modelId)) {
                    predictors.get(modelId).close();
                    predictors.remove(modelId);
                    modelUnloadStatus.put(modelId, "deleted");
                    log.info("Unload model {}", modelId);
                }
                if (models.containsKey(modelId)) {
                    models.get(modelId).close();
                    models.remove(modelId);
                }
            }
        }
        return modelUnloadStatus;
    }

}
