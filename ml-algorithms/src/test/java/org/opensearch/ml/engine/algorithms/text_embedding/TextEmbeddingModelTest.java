/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

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
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
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
import static org.opensearch.ml.engine.MLEngine.getModelCachePath;
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


    @Before
    public void setUp() throws URISyntaxException {
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
                .build();
        modelHelper = new ModelHelper();
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("all-MiniLM-L6-v2_torchscript_sentence-transformer.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        textEmbeddingModel = new TextEmbeddingModel();
        djlCachePath = Path.of("/tmp/djl_cache_" + UUID.randomUUID());
        MLEngine.setDjlCachePath(djlCachePath);

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is a happy dog")).build();
    }

    @Test
    public void initModel_predict_TorchScript_SentenceTransformer() {
        textEmbeddingModel.initModel(model, params);
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(inputDataSet);
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
        textEmbeddingModel.initModel(model, params);
        ModelResultFilter resultFilter = ModelResultFilter.builder().returnNumber(true).targetResponse(Arrays.asList(SENTENCE_EMBEDDING)).build();
        TextDocsInputDataSet textDocsInputDataSet = inputDataSet.toBuilder().resultFilter(resultFilter).build();
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(textDocsInputDataSet);
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
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_torchscript_huggingface.zip").toURI()));
        Path modelCachePath = getModelCachePath(model.getModelId(), model.getName(), model.getVersion());
        File file = new File(modelCachePath.toUri());
        file.mkdirs();
        TextEmbeddingModelConfig hugginfaceModelConfig = modelConfig.toBuilder()
                .frameworkType(HUGGINGFACE_TRANSFORMERS).build();
        MLModel mlModel = model.toBuilder().modelFormat(MLModelFormat.TORCH_SCRIPT).modelConfig(hugginfaceModelConfig).build();
        textEmbeddingModel.initModel(mlModel, params);
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(inputDataSet);
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
    public void initModel_predict_ONNX() throws URISyntaxException {
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("all-MiniLM-L6-v2_onnx.zip").toURI()));
        TextEmbeddingModelConfig onnxModelConfig = modelConfig.toBuilder()
                .frameworkType(HUGGINGFACE_TRANSFORMERS).build();
        MLModel mlModel = model.toBuilder().modelFormat(MLModelFormat.ONNX).modelConfig(onnxModelConfig).build();
        textEmbeddingModel.initModel(mlModel, params);
        ModelTensorOutput output = (ModelTensorOutput)textEmbeddingModel.predict(inputDataSet);
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
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        params.remove(MODEL_ZIP_FILE);
        textEmbeddingModel.initModel(model, params);
    }

    @Test
    public void initModel_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        params.remove(MODEL_HELPER);
        textEmbeddingModel.initModel(model, params);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        textEmbeddingModel.initModel(model, params);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("wrong_zip_with_2_pt_file.zip").toURI()));
            textEmbeddingModel.initModel(model, params);
        } catch (Exception e) {
            assertEquals(MLException.class, e.getClass());
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals("found multiple models", e.getCause().getMessage());
        }

    }

    @Test
    public void initModel_WrongFunctionName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong function name");
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        textEmbeddingModel.initModel(mlModel, params);
    }

    @Test
    public void loadTextEmbeddingModel_WrongEngine() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported engine");
        textEmbeddingModel.loadTextEmbeddingModel(modelZipFile, modelId, modelName, functionName, version, modelConfig, "wrong_engine");
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("model not loaded");
        textEmbeddingModel.predict(inputDataSet);
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("model not loaded");
        model.setModelId(null);
        try {
            textEmbeddingModel.initModel(model, params);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        textEmbeddingModel.predict(inputDataSet);
    }

    @Test
    public void predict_AfterModelClosed() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("model not loaded");
        textEmbeddingModel.initModel(model, params);
        textEmbeddingModel.close();
        textEmbeddingModel.predict(inputDataSet);
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        textEmbeddingModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("model not loaded");
        textEmbeddingModel.predict(inputDataSet, model);
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
