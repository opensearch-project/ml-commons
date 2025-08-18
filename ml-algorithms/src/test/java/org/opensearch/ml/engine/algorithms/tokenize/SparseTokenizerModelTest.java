package org.opensearch.ml.engine.algorithms.tokenize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.DLModel.ML_ENGINE;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.FileUtils;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

public class SparseTokenizerModelTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private File modelZipFile;
    private String modelId;
    private String modelName;
    private FunctionName functionName;
    private String version;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private SparseTokenizerModel sparseTokenizerModel;
    private Path mlCachePath;
    private Path mlConfigPath;
    private TextDocsInputDataSet inputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        modelId = "test_model_id";
        modelName = "test_model_name";
        functionName = FunctionName.SPARSE_TOKENIZE;
        version = "1";
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.SPARSE_TOKENIZE)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("tokenize-demo.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        sparseTokenizerModel = new SparseTokenizerModel();

        inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("today is sunny", "That is dog")).build();
    }

    @Test
    public void test_doLoadModel() throws URISyntaxException,
        TranslateException,
        ModelNotFoundException,
        MalformedModelException,
        IOException {
        SparseTokenizerModel sparseTokenizerModel = mock(SparseTokenizerModel.class);
        Predictor<Input, Output> predictor = mock(Predictor.class);
        List<Predictor<Input, Output>> predictorList = Collections.singletonList(predictor);
        ZooModel<Input, Output> model = mock(ZooModel.class);
        List<ZooModel<Input, Output>> modelList = Collections.singletonList(model);
        String engine = "engine";
        Path modelPath = mock(Path.class);
        when(modelPath.resolve((String) any())).thenReturn(Paths.get(getClass().getResource("tokenizer.json").toURI()));
        MLModelConfig modelConfig = mock(MLModelConfig.class);
        sparseTokenizerModel.doLoadModel(predictorList, modelList, engine, modelPath, modelConfig);
    }

    @Test
    public void test_Predict() throws URISyntaxException, TranslateException, ModelNotFoundException, MalformedModelException, IOException {
        SparseTokenizerModel sparseTokenizerModel = new SparseTokenizerModel();
        Predictor<Input, Output> predictor = mock(Predictor.class);
        List<Predictor<Input, Output>> predictorList = Collections.singletonList(predictor);
        ZooModel<Input, Output> model = mock(ZooModel.class);
        List<ZooModel<Input, Output>> modelList = Collections.singletonList(model);
        String engine = "engine";
        Path modelPath = Paths.get(getClass().getResource("tokenizer.json").toURI()).getParent();
        MLModelConfig modelConfig = mock(MLModelConfig.class);
        sparseTokenizerModel.doLoadModel(predictorList, modelList, engine, modelPath, modelConfig);

        MLInput mlInput = mock(MLInput.class);
        TextDocsInputDataSet textDocsInputDataSet = mock(TextDocsInputDataSet.class);
        when(mlInput.getInputDataset()).thenReturn(textDocsInputDataSet);

        when(textDocsInputDataSet.getResultFilter()).thenReturn(null);
        List<String> docs = Collections.singletonList("hello world");
        when(textDocsInputDataSet.getDocs()).thenReturn(docs);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) sparseTokenizerModel.predict(null, mlInput);
        assertNotNull(modelTensorOutput);
        List<ModelTensors> modelTensorsList = modelTensorOutput.getMlModelOutputs();
        assertEquals(1, modelTensorsList.size());
        ModelTensors modelTensors = modelTensorsList.get(0);
        List<ModelTensor> modelTensorList = modelTensors.getMlModelTensors();
        assertEquals(1, modelTensorList.size());
        ModelTensor modelTensor = modelTensorList.get(0);
        assertNotNull(modelTensor);
    }

    @Test
    public void initModel_predict_Tokenize() throws URISyntaxException, TranslateException {
        sparseTokenizerModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) sparseTokenizerModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            Map<String, ?> resultMap = tensor.getDataAsMap();
            assertEquals(resultMap.size(), 1);
            List<Map<String, Float>> resultList = (List<Map<String, Float>>) resultMap.get("response");
            assertEquals(resultList.size(), 1);
            Map<String, Float> result = resultList.get(0);
            assertEquals(result.size(), 3);
        }
    }

    // Test default WORD format (no parameters provided)
    @Test
    public void initModel_predict_Tokenize_DefaultLexicalFormat() throws URISyntaxException, TranslateException {
        sparseTokenizerModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) sparseTokenizerModel.predict(mlInput);

        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        // Verify output format is WORD (token strings as keys)
        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            Map<String, ?> resultMap = tensor.getDataAsMap();
            List<Map<String, Float>> resultList = (List<Map<String, Float>>) resultMap.get("response");
            Map<String, Float> result = resultList.get(0);

            // Verify keys are token strings rather than numeric IDs
            for (String key : result.keySet()) {
                assertTrue("Key should be a token string, not numeric ID", !isNumeric(key));
            }
        }
    }

    // Test WORD format with explicit parameter
    @Test
    public void initModel_predict_Tokenize_WithLexicalFormat() throws URISyntaxException, TranslateException {
        sparseTokenizerModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_TOKENIZE)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) sparseTokenizerModel.predict(mlInput);

        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        // Verify output format is WORD
        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            Map<String, ?> resultMap = tensor.getDataAsMap();
            List<Map<String, Float>> resultList = (List<Map<String, Float>>) resultMap.get("response");
            Map<String, Float> result = resultList.get(0);

            // Verify keys are token strings
            for (String key : result.keySet()) {
                assertTrue("Key should be a token string for WORD format", !isNumeric(key));
            }
        }
    }

    // Test TOKEN_ID format
    @Test
    public void initModel_predict_Tokenize_WithTokenIdFormat() throws URISyntaxException, TranslateException {
        sparseTokenizerModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_TOKENIZE)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) sparseTokenizerModel.predict(mlInput);

        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        // Verify output format is TOKEN_ID
        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            Map<String, ?> resultMap = tensor.getDataAsMap();
            List<Map<String, Float>> resultList = (List<Map<String, Float>>) resultMap.get("response");
            Map<String, Float> result = resultList.get(0);

            // Verify keys are numeric token ID strings
            for (String key : result.keySet()) {
                assertTrue("Key should be a numeric token ID for TOKEN_ID format", isNumeric(key));
            }
        }
    }

    // Test both content_type and sparse_embedding_format parameters
    @Test
    public void initModel_predict_Tokenize_WithBothContentTypeAndFormat() throws URISyntaxException, TranslateException {
        sparseTokenizerModel.initModel(model, params, encryptor);

        AsymmetricTextEmbeddingParameters parameters = AsymmetricTextEmbeddingParameters
            .builder()
            .embeddingContentType(AsymmetricTextEmbeddingParameters.EmbeddingContentType.QUERY)
            .sparseEmbeddingFormat(SparseEmbeddingFormat.TOKEN_ID)
            .build();

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.SPARSE_TOKENIZE)
            .inputDataset(inputDataSet)
            .parameters(parameters)
            .build();

        ModelTensorOutput output = (ModelTensorOutput) sparseTokenizerModel.predict(mlInput);

        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        // Verify output format is TOKEN_ID (sparse_embedding_format parameter takes effect)
        for (ModelTensors tensors : mlModelOutputs) {
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
            ModelTensor tensor = mlModelTensors.get(0);
            Map<String, ?> resultMap = tensor.getDataAsMap();
            List<Map<String, Float>> resultList = (List<Map<String, Float>>) resultMap.get("response");
            Map<String, Float> result = resultList.get(0);

            // Verify keys are numeric token ID strings
            for (String key : result.keySet()) {
                assertTrue("Key should be a numeric token ID for TOKEN_ID format", isNumeric(key));
            }
        }
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("tokenize-demo.zip").toURI()));
        sparseTokenizerModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("tokenize-demo.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        sparseTokenizerModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");
        model.setModelId(null);
        sparseTokenizerModel.initModel(model, params, encryptor);
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(MODEL_HELPER, modelHelper);
            params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
            params.put(ML_ENGINE, mlEngine);
            sparseTokenizerModel.initModel(model, params, encryptor);
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
        sparseTokenizerModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void predict_NullModelHelper() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        sparseTokenizerModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_NullModelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        model.setModelId(null);
        try {
            sparseTokenizerModel.initModel(model, params, encryptor);
        } catch (Exception e) {
            assertEquals("model id is null", e.getMessage());
        }
        sparseTokenizerModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build());
    }

    @Test
    public void predict_AfterModelClosed() throws TranslateException {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to inference SPARSE_TOKENIZE");
        sparseTokenizerModel.initModel(model, params, encryptor);
        sparseTokenizerModel.close();
        sparseTokenizerModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build());
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        sparseTokenizerModel.parseModelTensorOutput(null, null);
    }

    @Test
    public void predict_BeforeInitingModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        sparseTokenizerModel.predict(MLInput.builder().algorithm(FunctionName.SPARSE_TOKENIZE).inputDataset(inputDataSet).build(), model);
    }

    /**
     * Helper method to check if a string is numeric
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }
}
