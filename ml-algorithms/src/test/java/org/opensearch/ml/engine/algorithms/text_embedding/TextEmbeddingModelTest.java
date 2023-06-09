/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.utils.FileUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.ML_ENGINE;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.MODEL_ZIP_FILE;
import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.SENTENCE_EMBEDDING;


public class TextEmbeddingModelTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private File modelZipFile;
    private String modelId;
    private String modelName;
    private FunctionName functionName;
    private String version;
    private TextEmbeddingModelConfig modelConfig;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private TextEmbeddingModel textEmbeddingModel;
    private Path djlCachePath;
    private TextDocsInputDataSet inputDataSet;
    private int dimension = 384;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        djlCachePath = Path.of("/tmp/djl_cache_" + UUID.randomUUID());
        mlEngine = new MLEngine(djlCachePath, encryptor);
        modelId = "test_model_id";
        modelName = "test_model_name";
        functionName = FunctionName.TEXT_EMBEDDING;
        version = "1";
        modelConfig = TextEmbeddingModelConfig.builder()
                .modelType("bert")
                .embeddingDimension(dimension)
                .frameworkType(SENTENCE_TRANSFORMERS)
                .build();
        model = MLModel.builder()
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .name("test_model_name")
                .modelId("test_model_id")
                .algorithm(FunctionName.TEXT_EMBEDDING)
                .version("1.0.0")
                .modelConfig(modelConfig)
                .modelState(MLModelState.TRAINED)
                .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("all-MiniLM-L6-v2_torchscript_sentence-transformer.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        textEmbeddingModel = new TextEmbeddingModel();

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is a happy dog")).build();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_SmallModel() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("traced_small_model.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        TextEmbeddingModelConfig modelConfig = this.modelConfig.toBuilder().embeddingDimension(768).build();
        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).build();
        textEmbeddingModel.initModel(smallModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(modelConfig.getEmbeddingDimension().intValue(), mlModelTensors.get(position).getData().length);
        }
        textEmbeddingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer() {
        textEmbeddingModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(4, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer_ResultFilter() {
        textEmbeddingModel.initModel(model, params, encryptor);
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnNumber(true).targetResponse(Arrays.asList(SENTENCE_EMBEDDING)).build();
        TextDocsInputDataSet textDocsInputDataSet = inputDataSet.toBuilder().resultFilter(resultFilter).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingModel.close();
    }

    @Test
    public void initModel_predict_TorchScript_Huggingface() throws URISyntaxException {
        String modelFile = "all-MiniLM-L6-v2_torchscript_huggingface.zip";
        String modelType = "bert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = true;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, dimension);
    }

    @Test
    public void initModel_predict_ONNX_bert() throws URISyntaxException {
        String modelFile = "all-MiniLM-L6-v2_onnx.zip";
        String modelType = "bert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = true;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.ONNX;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, dimension);
    }

    @Test
    public void initModel_predict_ONNX_albert() throws URISyntaxException {
        String modelFile = "paraphrase-albert-small-v2_onnx.zip";
        String modelType = "albert";
        TextEmbeddingModelConfig.PoolingMode poolingMode = TextEmbeddingModelConfig.PoolingMode.MEAN;
        boolean normalize = false;
        int modelMaxLength = 512;
        MLModelFormat modelFormat = MLModelFormat.ONNX;
        initModel_predict_HuggingfaceModel(modelFile, modelType, poolingMode, normalize, modelMaxLength, modelFormat, 768);
    }

    private void initModel_predict_HuggingfaceModel(String modelFile, String modelType, TextEmbeddingModelConfig.PoolingMode poolingMode,
                                                    boolean normalizeResult, Integer modelMaxLength,
                                                    MLModelFormat modelFormat, int dimension) throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource(modelFile).toURI()));
        params.put(ML_ENGINE, mlEngine);
        TextEmbeddingModelConfig onnxModelConfig = modelConfig.toBuilder()
                .frameworkType(HUGGINGFACE_TRANSFORMERS)
                .modelType(modelType)
                .poolingMode(poolingMode)
                .normalizeResult(normalizeResult)
                .modelMaxLength(modelMaxLength)
                .build();
        MLModel mlModel = model.toBuilder().modelFormat(modelFormat).modelConfig(onnxModelConfig).build();
        textEmbeddingModel.initModel(mlModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        System.out.println(Arrays.toString(mlModelOutputs.get(0).getMlModelTensors().get(0).getData()));
        assertEquals(2, mlModelOutputs.size());
        for (int i=0;i<mlModelOutputs.size();i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            int position = findSentenceEmbeddingPosition(tensors);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            assertEquals(dimension, mlModelTensors.get(position).getData().length);
        }
        textEmbeddingModel.close();

    }

    @Test
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_onnx.zip").toURI()));
        textEmbeddingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_onnx.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        textEmbeddingModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(MODEL_HELPER, modelHelper);
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("wrong_zip_with_2_pt_file.zip").toURI()));
            params.put(ML_ENGINE, mlEngine);
            textEmbeddingModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals(MLException.class, e.getClass());
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            assertEquals(IllegalArgumentException.class, rootCause.getClass());
            assertEquals("found multiple models", rootCause.getMessage());
        }
    }

    @Test
    public void initModel_WrongFunctionName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong function name");
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        textEmbeddingModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        model.setModelId(null);
        try {
            textEmbeddingModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        textEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_AfterModelClosed() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to inference TEXT_EMBEDDING");
        textEmbeddingModel.initModel(model, params, encryptor);
        textEmbeddingModel.close();
        textEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        textEmbeddingModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        textEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), model);
    }

    @Test
    public void testA() {
        System.out.println("a1 ".repeat(10));
    }


    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(djlCachePath);
    }

    private int findSentenceEmbeddingPosition(ModelTensors modelTensors) {
        List<ModelTensor> mlModelTensors = modelTensors.getMlModelTensors();
        for (int i=0; i<mlModelTensors.size(); i++) {
            ModelTensor mlModelTensor = mlModelTensors.get(i);
            if (SENTENCE_EMBEDDING.equals(mlModelTensor.getName())) {
                return i;
            }
        }
        throw new ResourceNotFoundException("no sentence embedding found");
    }
}
