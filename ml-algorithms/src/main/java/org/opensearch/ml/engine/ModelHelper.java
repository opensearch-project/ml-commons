/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.utils.FileUtils.calculateFileHash;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.engine.utils.FileUtils.splitFileIntoChunks;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import com.google.gson.stream.JsonReader;

import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import lombok.extern.log4j.Log4j2;

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
    private static final Set<String> DISALLOWED_MODEL_FILE_EXTENSIONS = Set.of(".jar", ".class", ".so", ".dylib", ".dll");
    private static final Set<String> CLASS_LOADING_SERVING_PROPERTIES = Set.of("blockfactory", "translatorfactory", "translator");
    private static final Set<String> NON_CLASS_SERVING_PROPERTIES = Set.of("application");
    private static final Pattern FULLY_QUALIFIED_CLASS_NAME = Pattern.compile("^([a-zA-Z_$][\\w$]*\\.)+[A-Z][\\w$]*$");
    private static final String ALLOWED_CLASS_PREFIX = "ai.djl.";
    private static final String SERVING_PROPERTIES_FILE_NAME = "serving.properties";
    // TorchScript file ops, matched as bare identifiers to catch every spelling.
    private static final Pattern DISALLOWED_TORCHSCRIPT_OPS = Pattern.compile("\\b(from_file|save|load_tensor)\\b");
    // Allowed torch.ops/torch.classes namespaces. The separator allows whitespace, line continuations and comments,
    // and is possessive to keep matching linear.
    private static final String TORCHSCRIPT_TOKEN_SEPARATOR = "(?:\\s|\\\\|#[^\\n]*+)*+";
    private static final Pattern TORCHSCRIPT_NAMESPACE_REFERENCE = Pattern
        .compile("\\b(?:ops|classes)" + TORCHSCRIPT_TOKEN_SEPARATOR + "\\." + TORCHSCRIPT_TOKEN_SEPARATOR + "([A-Za-z_]\\w*)");
    // Custom class references in pickled module state (data.pkl, constants.pkl), stored as plain text.
    private static final Pattern TORCHSCRIPT_PICKLED_CLASS_REFERENCE = Pattern.compile("\\btorch\\.classes\\.([A-Za-z_]\\w*)");
    private static final Set<String> ALLOWED_TORCHSCRIPT_NAMESPACES = Set.of("aten", "prim", "quantized");
    // Max TorchScript source and pickle bytes read per archive.
    private static final long MAX_TORCHSCRIPT_SOURCE_BYTES = 16L * 1024 * 1024;
    private final MLEngine mlEngine;

    public ModelHelper(MLEngine mlEngine) {
        this.mlEngine = mlEngine;
    }

    @SuppressWarnings("removal")
    public void downloadPrebuiltModelConfig(
        String taskId,
        MLRegisterModelInput registerModelInput,
        ActionListener<MLRegisterModelInput> listener
    ) {
        String modelName = registerModelInput.getModelName();
        FunctionName algorithm = registerModelInput.getFunctionName();
        String version = registerModelInput.getVersion();
        MLModelFormat modelFormat = registerModelInput.getModelFormat();
        Boolean isHidden = registerModelInput.getIsHidden();
        boolean deployModel = registerModelInput.isDeployModel();
        String[] modelNodeIds = registerModelInput.getModelNodeIds();
        String modelGroupId = registerModelInput.getModelGroupId();
        MLDeploySetting mlDeploySetting = registerModelInput.getDeploySetting();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {

                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                String configCacheFilePath = registerModelPath.resolve("config.json").toString();

                String configFileUrl = mlEngine.getPrebuiltModelConfigPath(modelName, version, modelFormat);
                String modelZipFileUrl = mlEngine.getPrebuiltModelPath(modelName, version, modelFormat);
                DownloadUtils.download(configFileUrl, configCacheFilePath, new ProgressBar());

                Map<?, ?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(configCacheFilePath))) {
                    config = gson.fromJson(reader, Map.class);
                }

                if (config == null) {
                    listener.onFailure(new IllegalArgumentException("model config not found"));
                    return null;
                }

                MLRegisterModelInput.MLRegisterModelInputBuilder builder = MLRegisterModelInput.builder();

                String functionName = config.containsKey("function_name")
                    ? (String) config.get("function_name")
                    : (String) config.get("model_task_type");

                builder
                    .modelName(modelName)
                    .version(version)
                    .url(modelZipFileUrl)
                    .deployModel(deployModel)
                    .modelNodeIds(modelNodeIds)
                    .isHidden(isHidden)
                    .modelGroupId(modelGroupId)
                    .functionName(FunctionName.from((functionName)))
                    .deploySetting(mlDeploySetting);

                config.entrySet().forEach(entry -> {
                    switch (entry.getKey().toString()) {
                        case MLRegisterModelInput.MODEL_FORMAT_FIELD:
                            builder.modelFormat(MLModelFormat.from(entry.getValue().toString()));
                            break;
                        case MLRegisterModelInput.MODEL_CONFIG_FIELD:
                            if (FunctionName.QUESTION_ANSWERING.equals(algorithm)) {
                                QuestionAnsweringModelConfig.QuestionAnsweringModelConfigBuilder configBuilder =
                                    QuestionAnsweringModelConfig.builder();
                                Map<?, ?> configMap = (Map<?, ?>) entry.getValue();
                                for (Map.Entry<?, ?> configEntry : configMap.entrySet()) {
                                    switch (configEntry.getKey().toString()) {
                                        case MLModelConfig.MODEL_TYPE_FIELD:
                                            configBuilder.modelType(configEntry.getValue().toString());
                                            break;
                                        case MLModelConfig.ALL_CONFIG_FIELD:
                                            configBuilder.allConfig(configEntry.getValue().toString());
                                            break;
                                        case QuestionAnsweringModelConfig.FRAMEWORK_TYPE_FIELD:
                                            configBuilder
                                                .frameworkType(
                                                    QuestionAnsweringModelConfig.FrameworkType.from(configEntry.getValue().toString())
                                                );
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                builder.modelConfig(configBuilder.build());
                            } else {
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
                                        case BaseModelConfig.ADDITIONAL_CONFIG_FIELD:
                                            configBuilder.additionalConfig((Map<String, Object>) configEntry.getValue());
                                            break;
                                        case TextEmbeddingModelConfig.EMBEDDING_DIMENSION_FIELD:
                                            configBuilder.embeddingDimension(((Double) configEntry.getValue()).intValue());
                                            break;
                                        case TextEmbeddingModelConfig.FRAMEWORK_TYPE_FIELD:
                                            configBuilder
                                                .frameworkType(
                                                    TextEmbeddingModelConfig.FrameworkType.from(configEntry.getValue().toString())
                                                );
                                            break;
                                        case TextEmbeddingModelConfig.POOLING_MODE_FIELD:
                                            configBuilder
                                                .poolingMode(
                                                    TextEmbeddingModelConfig.PoolingMode
                                                        .from(configEntry.getValue().toString().toUpperCase(Locale.ROOT))
                                                );
                                            break;
                                        case TextEmbeddingModelConfig.NORMALIZE_RESULT_FIELD:
                                            configBuilder.normalizeResult(Boolean.parseBoolean(configEntry.getValue().toString()));
                                            break;
                                        case TextEmbeddingModelConfig.MODEL_MAX_LENGTH_FIELD:
                                            configBuilder.modelMaxLength(((Double) configEntry.getValue()).intValue());
                                            break;
                                        case TextEmbeddingModelConfig.QUERY_PREFIX:
                                            configBuilder.queryPrefix(configEntry.getValue().toString());
                                            break;
                                        case TextEmbeddingModelConfig.PASSAGE_PREFIX:
                                            configBuilder.passagePrefix(configEntry.getValue().toString());
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                builder.modelConfig(configBuilder.build());
                            }
                            break;
                        case MLRegisterModelInput.MODEL_CONTENT_HASH_VALUE_FIELD:
                            builder.hashValue(entry.getValue().toString());
                            break;
                        default:
                            break;
                    }
                });
                listener.onResponse(builder.build());
                return null;
            });
        } catch (Exception e) {
            listener.onFailure(e);
        } finally {
            deleteFileQuietly(mlEngine.getRegisterModelPath(taskId));
        }
    }

    @SuppressWarnings("unchecked")
    public boolean isModelAllowed(MLRegisterModelInput registerModelInput, List modelMetaList) {
        String modelName = registerModelInput.getModelName();
        String version = registerModelInput.getVersion();
        MLModelFormat modelFormat = registerModelInput.getModelFormat();
        for (Object meta : modelMetaList) {
            String name = (String) ((Map<String, Object>) meta).get("name");
            List<String> versions = (List) ((Map<String, Object>) meta).get("version");
            List<String> formats = (List) ((Map<String, Object>) meta).get("format");
            if (name.equals(modelName)
                && versions.contains(version.toLowerCase(Locale.ROOT))
                && formats.contains(modelFormat.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("removal")
    public List downloadPrebuiltModelMetaList(String taskId, MLRegisterModelInput registerModelInput) throws PrivilegedActionException {
        String modelName = registerModelInput.getModelName();
        String version = registerModelInput.getVersion();
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<List>) () -> {

                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                String cacheFilePath = registerModelPath.resolve("model_meta_list.json").toString();
                String modelMetaListUrl = mlEngine.getPrebuiltModelMetaListPath();
                DownloadUtils.download(modelMetaListUrl, cacheFilePath, new ProgressBar());

                List<?> config = null;
                try (JsonReader reader = new JsonReader(new FileReader(cacheFilePath))) {
                    config = gson.fromJson(reader, List.class);
                }

                return config;
            });
        } finally {
            deleteFileQuietly(mlEngine.getRegisterModelPath(taskId));
        }
    }

    /**
     * Download model from URL and split it into smaller chunks.
     * @param modelFormat model format
     * @param taskId task id
     * @param modelName model name
     * @param version model version
     * @param url model file URL
     * @param modelContentHash model content hash value
     * @param listener action listener
     */
    @SuppressWarnings("removal")
    public void downloadAndSplit(
        MLModelFormat modelFormat,
        String taskId,
        String modelName,
        String version,
        String url,
        String modelContentHash,
        FunctionName functionName,
        ActionListener<Map<String, Object>> listener
    ) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Path registerModelPath = mlEngine.getRegisterModelPath(taskId, modelName, version);
                String modelPath = registerModelPath + ".zip";
                Path modelPartsPath = registerModelPath.resolve("chunks");
                File modelZipFile = new File(modelPath);
                log.debug("download model to file {}", modelZipFile.getAbsolutePath());
                DownloadUtils.download(url, modelPath, new ProgressBar());
                verifyModelZipFile(modelFormat, modelPath, modelName, functionName);
                String hash = calculateFileHash(modelZipFile);
                if (modelContentHash == null) {
                    log.error("Hash code need to be provided when register via url.");
                    throw (new IllegalArgumentException(
                        "Model content Hash code need to be provided when register via url. Please calculate sha 256 Hash code."
                    ));
                } else if (hash.equals(modelContentHash)) {
                    List<String> chunkFiles = splitFileIntoChunks(modelZipFile, modelPartsPath, CHUNK_SIZE);
                    Map<String, Object> result = new HashMap<>();
                    result.put(CHUNK_FILES, chunkFiles);
                    result.put(MODEL_SIZE_IN_BYTES, modelZipFile.length());

                    result.put(MODEL_FILE_HASH, calculateFileHash(modelZipFile));
                    deleteFileQuietly(modelZipFile);
                    listener.onResponse(result);
                    return null;
                } else {
                    log.error("Model content hash can't match original hash value when registering");
                    throw (new IllegalArgumentException("model content changed"));
                }
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public void verifyModelZipFile(MLModelFormat modelFormat, String modelZipFilePath, String modelName, FunctionName functionName)
        throws IOException {
        boolean hasPtFile = false;
        boolean hasOnnxFile = false;
        boolean hasTokenizerFile = false;
        try (ZipFile zipFile = new ZipFile(modelZipFilePath)) {
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) zipEntries.nextElement();
                String fileName = zipEntry.getName();
                verifyNoExecutableFile(fileName);
                String baseName = fileName.replace('\\', '/');
                if (baseName.substring(baseName.lastIndexOf('/') + 1).equalsIgnoreCase(SERVING_PROPERTIES_FILE_NAME)) {
                    try (InputStream in = zipFile.getInputStream(zipEntry)) {
                        verifyServingProperties(in);
                    }
                }
                hasPtFile = hasModelFile(modelFormat, MLModelFormat.TORCH_SCRIPT, PYTORCH_FILE_EXTENSION, hasPtFile, fileName);
                hasOnnxFile = hasModelFile(modelFormat, MLModelFormat.ONNX, ONNX_FILE_EXTENSION, hasOnnxFile, fileName);
                if (fileName.equals(TOKENIZER_FILE_NAME)) {
                    hasTokenizerFile = true;
                }
            }
        }
        if (!hasPtFile && !hasOnnxFile && functionName != FunctionName.SPARSE_TOKENIZE) { // sparse tokenizer model doesn't need model file.
            throw new IllegalArgumentException("Can't find model file");
        }
        if (!hasTokenizerFile) {
            if (modelName != FunctionName.METRICS_CORRELATION.toString()) {
                throw new IllegalArgumentException("No tokenizer file");
            }
        }
    }

    private static boolean hasModelFile(
        MLModelFormat modelFormat,
        MLModelFormat targetModelFormat,
        String fileExtension,
        boolean hasModelFile,
        String fileName
    ) {
        if (fileName.endsWith(fileExtension)) {
            if (modelFormat != targetModelFormat) {
                throw new IllegalArgumentException("Model format is " + modelFormat + ", but find " + fileExtension + " file");
            }
            if (hasModelFile) {
                throw new IllegalArgumentException("Find multiple model files, but expected only one");
            }
            return true;
        }
        return hasModelFile;
    }

    private static void verifyNoExecutableFile(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        for (String extension : DISALLOWED_MODEL_FILE_EXTENSIONS) {
            if (lowerName.endsWith(extension)) {
                throw new IllegalArgumentException("Model archive contains a disallowed executable file: " + fileName);
            }
        }
    }

    private static void verifyServingProperties(InputStream servingProperties) throws IOException {
        Properties properties = new Properties();
        properties.load(servingProperties);
        for (String key : properties.stringPropertyNames()) {
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            if (NON_CLASS_SERVING_PROPERTIES.contains(normalizedKey)) {
                continue;
            }
            String value = properties.getProperty(key).trim();
            boolean isClassLoadingKey = CLASS_LOADING_SERVING_PROPERTIES.contains(normalizedKey);
            boolean looksLikeClassName = FULLY_QUALIFIED_CLASS_NAME.matcher(value).matches();
            if (!isClassLoadingKey && !looksLikeClassName) {
                continue;
            }
            if (!value.isEmpty() && !value.startsWith(ALLOWED_CLASS_PREFIX)) {
                throw new IllegalArgumentException(
                    "serving.properties key '" + key + "' references a non-DJL class '" + value + "', which is not allowed"
                );
            }
        }
    }

    /**
     * Rejects TorchScript archives whose code/ sources call filesystem ops, or whose sources or pickles reference
     * disallowed namespaces; these would run in the OpenSearch process during torch::jit::load (e.g. via
     * __setstate__). Fails closed: anything that isn't a zip with
     * code/ sources (e.g. flatbuffer) is rejected, since its code can't be scanned.
     */
    private static void verifyTorchScriptArchive(File modelFile) throws IOException {
        String fileName = modelFile.getName();
        try (ZipFile zipFile = new ZipFile(modelFile)) {
            boolean hasSource = false;
            long remainingBytes = MAX_TORCHSCRIPT_SOURCE_BYTES;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                boolean isSource = entryName.endsWith(".py");
                boolean isPickle = entryName.endsWith(".pkl");
                if (!isSource && !isPickle) {
                    continue;
                }
                hasSource |= isSource && entryName.contains("/code/");
                byte[] content;
                try (InputStream in = zipFile.getInputStream(entry)) {
                    content = in.readNBytes((int) remainingBytes + 1);
                }
                remainingBytes -= content.length;
                if (remainingBytes < 0) {
                    throw new IllegalArgumentException("TorchScript model " + fileName + " source exceeds the size limit");
                }
                if (isSource) {
                    verifyTorchScriptSource(new String(content, StandardCharsets.UTF_8), entryName);
                } else {
                    verifyTorchScriptNamespaces(
                        TORCHSCRIPT_PICKLED_CLASS_REFERENCE.matcher(new String(content, StandardCharsets.ISO_8859_1)),
                        entryName
                    );
                }
            }
            if (!hasSource) {
                throw new IllegalArgumentException("Model file " + fileName + " is not a TorchScript archive: no code/ entries");
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("Model file " + fileName + " is not a valid TorchScript zip archive: " + e.getMessage());
        }
    }

    private static void verifyTorchScriptSource(String source, String entryName) {
        Matcher opMatcher = DISALLOWED_TORCHSCRIPT_OPS.matcher(source);
        if (opMatcher.find()) {
            throw new IllegalArgumentException("TorchScript model uses disallowed operation: " + opMatcher.group() + " in " + entryName);
        }
        verifyTorchScriptNamespaces(TORCHSCRIPT_NAMESPACE_REFERENCE.matcher(source), entryName);
    }

    private static void verifyTorchScriptNamespaces(Matcher namespaceMatcher, String entryName) {
        while (namespaceMatcher.find()) {
            if (!ALLOWED_TORCHSCRIPT_NAMESPACES.contains(namespaceMatcher.group(1))) {
                throw new IllegalArgumentException(
                    "TorchScript model uses disallowed operation namespace: " + namespaceMatcher.group(1) + " in " + entryName
                );
            }
        }
    }

    /**
     * Validate an unzipped model directory before DJL loads it. Rejects archives that
     * carry executable files, a serving.properties that points DJL at a non-DJL class,
     * or TorchScript code that accesses the filesystem.
     */
    public static void verifyModelDirSafety(Path modelDir) throws IOException {
        File[] files = modelDir.toFile().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                verifyModelDirSafety(file.toPath());
                continue;
            }
            verifyNoExecutableFile(file.getName());
            if (file.getName().equalsIgnoreCase(SERVING_PROPERTIES_FILE_NAME)) {
                try (InputStream in = Files.newInputStream(file.toPath())) {
                    verifyServingProperties(in);
                }
            }
            String name = file.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(PYTORCH_FILE_EXTENSION)
                || name.equalsIgnoreCase(modelDir.getFileName().toString())) {
                verifyTorchScriptArchive(file);
            }
        }
    }

    public void deleteFileCache(String modelId) {
        deleteFileQuietly(mlEngine.getModelCachePath(modelId));
        deleteFileQuietly(mlEngine.getDeployModelPath(modelId));
        deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
    }

}
