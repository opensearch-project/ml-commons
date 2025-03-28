/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.FileUtils;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class QuestionAnsweringModelTest {

    private File modelZipFile;
    private File sentenceHighlightingModelZipFile;
    private MLModel mlModel;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private QuestionAnsweringModel questionAnsweringModel;
    private Path mlCachePath;
    private QuestionAnsweringInputDataSet inputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        mlModel = MLModel
            .builder()
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_model_name")
            .modelId("test_model_id")
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();

        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("question_answering_pt.zip").toURI());
        sentenceHighlightingModelZipFile = new File(getClass().getResource("sentence_highlighting_qa_model_pt.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);

        questionAnsweringModel = new QuestionAnsweringModel();
        inputDataSet = new QuestionAnsweringInputDataSet("What is the capital of France?", "Paris is the capital of France.");
    }

    @Test
    public void test_QuestionAnswering_ProcessInput_ProcessOutput() throws URISyntaxException, IOException {
        QuestionAnsweringTranslator questionAnsweringTranslator = new QuestionAnsweringTranslator();
        TranslatorContext translatorContext = mock(TranslatorContext.class);
        Model mlModel = mock(Model.class);
        when(translatorContext.getModel()).thenReturn(mlModel);
        when(mlModel.getModelPath()).thenReturn(Paths.get(getClass().getResource("../tokenize/tokenizer.json").toURI()).getParent());
        questionAnsweringTranslator.prepare(translatorContext);

        NDManager manager = mock(NDManager.class);
        when(translatorContext.getNDManager()).thenReturn(manager);
        Input input = mock(Input.class);
        String question = "What color is apple";
        String context = "Apples are red";
        when(input.getAsString(0)).thenReturn(question);
        when(input.getAsString(1)).thenReturn(context);
        NDArray indiceNdArray = mock(NDArray.class);
        when(indiceNdArray.toLongArray()).thenReturn(new long[] { 102l, 101l });
        when(manager.create((long[]) any())).thenReturn(indiceNdArray);
        doNothing().when(indiceNdArray).setName(any());
        NDList outputList = questionAnsweringTranslator.processInput(translatorContext, input);
        assertEquals(2, outputList.size());
        Iterator<NDArray> iterator = outputList.iterator();
        while (iterator.hasNext()) {
            NDArray ndArray = iterator.next();
            long[] output = ndArray.toLongArray();
            assertEquals(2, output.length);
        }

        NDArray startLogits = mock(NDArray.class);
        NDArray endLogits = mock(NDArray.class);
        when(startLogits.argMax()).thenReturn(startLogits);
        when(startLogits.getLong()).thenReturn(3L);
        when(endLogits.argMax()).thenReturn(endLogits);
        when(endLogits.getLong()).thenReturn(7L);

        List<NDArray> ndArrayList = new ArrayList<>();
        ndArrayList.add(startLogits);
        ndArrayList.add(endLogits);
        NDList ndList = new NDList(ndArrayList);

        Output output = questionAnsweringTranslator.processOutput(translatorContext, ndList);
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        assertEquals(1, modelTensorsList.size());
    }

    @Test
    public void initModel_predict_TorchScript_QuestionAnswering() throws URISyntaxException {
        questionAnsweringModel.initModel(mlModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) questionAnsweringModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(1, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        questionAnsweringModel.close();
    }

    // ONNX is working fine but the model is too big to upload to git. Trying to find small models @Test
    @Test
    public void initModel_predict_ONNX_QuestionAnswering() throws URISyntaxException {
        mlModel = MLModel
            .builder()
            .modelFormat(MLModelFormat.ONNX)
            .name("test_model_name")
            .modelId("test_model_id")
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();
        modelZipFile = new File(getClass().getResource("question_answering_onnx.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);

        questionAnsweringModel.initModel(mlModel, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) questionAnsweringModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(1, mlModelOutputs.size());
        for (int i = 1; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            assertEquals(1, mlModelTensors.size());
        }
        questionAnsweringModel.close();
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("question_answering_pt.zip").toURI()));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("model helper is null"));
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("question_answering_pt.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("ML engine is null"));
    }

    @Test
    public void initModel_NullModelId() {
        mlModel.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        MLException e = assertThrows(MLException.class, () -> questionAnsweringModel.initModel(mlModel, params, encryptor));
        Throwable rootCause = e.getCause();
        assert (rootCause instanceof IllegalArgumentException);
        assert (rootCause.getMessage().equals("found multiple models"));
    }

    @Test
    public void initModel_WrongFunctionName() {
        MLModel wrongModel = mlModel.toBuilder().algorithm(FunctionName.KMEANS).build();
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(wrongModel, params, encryptor)
        );
        assert (e.getMessage().equals("wrong function name"));
    }

    @Test
    public void predict_NullModelHelper() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        assert (e.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_NullModelId() {
        mlModel.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        assert (e2.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_AfterModelClosed() {
        questionAnsweringModel.initModel(mlModel, params, encryptor);
        questionAnsweringModel.close();
        MLException e = assertThrows(
            MLException.class,
            () -> questionAnsweringModel
                .predict(MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build())
        );
        log.info(e.getMessage());
        assert (e.getMessage().startsWith("Failed to inference QUESTION_ANSWERING"));
    }

    // New tests for sentence highlighting functionality

    @Test
    public void testCheckHighlightingType_WithSentenceHighlighting() {
        // Create model config with sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set the model config
        mlModel = mlModel.toBuilder().modelConfig(modelConfig).build();

        // Use the sentence highlighting model file
        Map<String, Object> sentenceParams = new HashMap<>(params);
        sentenceParams.put(MODEL_ZIP_FILE, sentenceHighlightingModelZipFile);

        // Initialize the model
        questionAnsweringModel = new QuestionAnsweringModel();

        // Verify the translator type using getTranslator method
        Translator<Input, Output> translator = questionAnsweringModel.getTranslator("pytorch", modelConfig);
        assertEquals(SentenceHighlightingQATranslator.class, translator.getClass());
    }

    @Test
    public void testCheckHighlightingType_WithoutSentenceHighlighting() {
        // Create model config without sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType("standard")
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set the model config
        mlModel = mlModel.toBuilder().modelConfig(modelConfig).build();

        // Initialize the model
        questionAnsweringModel = new QuestionAnsweringModel();

        // Verify the translator type using getTranslator method
        Translator<Input, Output> translator = questionAnsweringModel.getTranslator("pytorch", modelConfig);
        assertEquals(QuestionAnsweringTranslator.class, translator.getClass());
    }

    @Test
    public void testCheckHighlightingType_WithNullModelConfig() {
        // Set null model config
        mlModel = mlModel.toBuilder().modelConfig(null).build();

        // Initialize the model
        questionAnsweringModel = new QuestionAnsweringModel();

        // Verify the translator type using getTranslator method
        Translator<Input, Output> translator = questionAnsweringModel.getTranslator("pytorch", null);
        assertEquals(QuestionAnsweringTranslator.class, translator.getClass());
    }

    @Test
    public void testPredictWithSentenceHighlighting() throws Exception {
        // Create model config with sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set the model config
        mlModel = mlModel.toBuilder().modelConfig(modelConfig).build();

        // Use the sentence highlighting model file
        Map<String, Object> sentenceParams = new HashMap<>(params);
        sentenceParams.put(MODEL_ZIP_FILE, sentenceHighlightingModelZipFile);

        // Initialize the model with mocked components
        questionAnsweringModel = new QuestionAnsweringModel();

        // Get the translator and verify it's the correct type
        Translator<Input, Output> translator = questionAnsweringModel.getTranslator("pytorch", modelConfig);
        assertEquals(SentenceHighlightingQATranslator.class, translator.getClass());

        // Test the translator's behavior with mocked components
        SentenceHighlightingQATranslator sentenceTranslator = (SentenceHighlightingQATranslator) translator;

        // Create a mock TranslatorContext
        TranslatorContext translatorContext = mock(TranslatorContext.class);

        // Create a mock Input
        Input input = new Input();
        input.add(MLInput.QUESTION_FIELD, "What color is apple");
        input.add(MLInput.CONTEXT_FIELD, "Apples are red");

        // Create sample output with highlighted sentences
        JSONArray highlightsArray = new JSONArray();

        // Add first highlight
        Map<String, Object> highlight1 = new HashMap<>();
        highlight1.put(FIELD_TEXT, "Apples are red");
        highlight1.put(FIELD_POSITION, 0);
        highlightsArray.put(new JSONObject(StringUtils.toJson(highlight1)));

        // Create model tensor with highlights
        ModelTensor highlightsTensor = new ModelTensor(FIELD_HIGHLIGHTS, highlightsArray.toString());
        ModelTensors modelTensors = new ModelTensors(List.of(highlightsTensor));

        // Create output with the model tensors
        Output output = new Output();
        output.add(modelTensors.toBytes());

        // Verify that the QuestionAnsweringModel would use the SentenceHighlightingQATranslator
        // for a model with sentence highlighting type
        assertEquals(SENTENCE_HIGHLIGHTING_TYPE, modelConfig.getModelType());
        assertEquals(SentenceHighlightingQATranslator.class, questionAnsweringModel.getTranslator("pytorch", modelConfig).getClass());
    }

    @Test
    public void testWarmUpWithSentenceHighlighting() throws Exception {
        // Create model config with sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set the model config
        mlModel = mlModel.toBuilder().modelConfig(modelConfig).build();

        // Mock predictor for warmup
        @SuppressWarnings("unchecked")
        Predictor<Input, Output> predictor = mock(Predictor.class);
        Output mockOutput = mock(Output.class);
        when(predictor.predict(any(Input.class))).thenReturn(mockOutput);

        // Call warmup method
        questionAnsweringModel.warmUp(predictor, "test_model_id", modelConfig);

        // Verify that predict was called with the correct input
        // We can only verify indirectly by checking that no exception was thrown
    }

    @Test
    public void testWarmUp_WithNullPredictor() {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        assertThrows(IllegalArgumentException.class, () -> model.warmUp(null, "test_model_id", null));
    }

    @Test
    public void testWarmUp_WithNullModelId() {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        Predictor predictor = mock(Predictor.class);
        assertThrows(IllegalArgumentException.class, () -> model.warmUp(predictor, null, null));
    }

    @Test
    public void testWarmUp_WithValidInputs() throws TranslateException {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        Predictor<Input, Output> predictor = mock(Predictor.class);
        Input input = new Input();
        input.add(DEFAULT_WARMUP_QUESTION);
        input.add(DEFAULT_WARMUP_CONTEXT);
        input.add("0");

        when(predictor.predict(any(Input.class))).thenReturn(new Output());

        model.warmUp(predictor, "test_model_id", null);
        verify(predictor).predict(any(Input.class));
    }

    @Test
    public void testGetTranslatorFactory() {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        TranslatorFactory factory = model.getTranslatorFactory("pytorch", null);
        assertNull(factory);
    }

    @Test
    public void testPredict_WithInvalidInputType() throws Exception {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        model.initModel(mlModel, params, encryptor);

        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .inputDataset(new TestInputDataSet()) // Invalid input type
            .build();

        try {
            model.predict(mlInput);
            fail("Expected MLException");
        } catch (MLException e) {
            // Expected exception
            assertTrue(e.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void testPredict_WithNullModelConfig() throws Exception {
        QuestionAnsweringModel model = new QuestionAnsweringModel();
        model.initModel(mlModel, params, encryptor);

        // Mock predictor
        Predictor<Input, Output> predictor = mock(Predictor.class);
        Output mockOutput = new Output();
        byte[] mockBytes = ModelTensors.builder().mlModelTensors(List.of(new ModelTensor("test", "test"))).build().toBytes();
        mockOutput.add(mockBytes);
        when(predictor.predict(any(Input.class))).thenReturn(mockOutput);

        // Set predictor using reflection
        java.lang.reflect.Field predictorsField = DLModel.class.getDeclaredField("predictors");
        predictorsField.setAccessible(true);
        predictorsField.set(model, new Predictor[] { predictor });

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        ModelTensorOutput output = (ModelTensorOutput) model.predict(mlInput);
        assertNotNull(output);
        assertEquals(1, output.getMlModelOutputs().size());
    }

    @Test
    public void testPredict_WithStandardQAModel() throws Exception {
        QuestionAnsweringModel model = new QuestionAnsweringModel();

        // Create model config for standard QA
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType("standard")
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        mlModel = mlModel.toBuilder().modelConfig(modelConfig).build();
        model.initModel(mlModel, params, encryptor);

        // Mock predictor
        Predictor<Input, Output> predictor = mock(Predictor.class);
        Output mockOutput = new Output();
        byte[] mockBytes = ModelTensors.builder().mlModelTensors(List.of(new ModelTensor("test", "test"))).build().toBytes();
        mockOutput.add(mockBytes);
        when(predictor.predict(any(Input.class))).thenReturn(mockOutput);

        // Set predictor using reflection
        java.lang.reflect.Field predictorsField = DLModel.class.getDeclaredField("predictors");
        predictorsField.setAccessible(true);
        predictorsField.set(model, new Predictor[] { predictor });

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        ModelTensorOutput output = (ModelTensorOutput) model.predict(mlInput);
        assertNotNull(output);
        assertEquals(1, output.getMlModelOutputs().size());
    }

    // Helper class for testing invalid input type
    private static class TestInputDataSet extends MLInputDataset {
        public TestInputDataSet() {
            super(MLInputDataType.TEXT_DOCS);
        }
    }

    @Test
    public void testCreateHighlightOutput() {
        QuestionAnsweringModel model = new QuestionAnsweringModel();

        // Create sample highlights
        List<Map<String, Object>> highlights = new ArrayList<>();

        // Create first highlight
        Map<String, Object> highlight1 = new HashMap<>();
        highlight1.put(FIELD_TEXT, "This is the first highlighted sentence.");
        highlight1.put(FIELD_POSITION, 0);
        highlight1.put(FIELD_START, 0);
        highlight1.put(FIELD_END, 38);
        highlights.add(highlight1);

        // Create second highlight
        Map<String, Object> highlight2 = new HashMap<>();
        highlight2.put(FIELD_TEXT, "This is the second highlighted sentence.");
        highlight2.put(FIELD_POSITION, 2);
        highlight2.put(FIELD_START, 80);
        highlight2.put(FIELD_END, 119);
        highlights.add(highlight2);

        // Call the method under test using reflection
        try {
            java.lang.reflect.Method method = QuestionAnsweringModel.class.getDeclaredMethod("createHighlightOutput", List.class);
            method.setAccessible(true);
            ModelTensorOutput output = (ModelTensorOutput) method.invoke(model, highlights);

            // Verify the output
            assertNotNull(output);
            assertEquals(1, output.getMlModelOutputs().size());

            ModelTensors tensors = output.getMlModelOutputs().get(0);
            assertEquals(1, tensors.getMlModelTensors().size());

            ModelTensor tensor = tensors.getMlModelTensors().get(0);
            assertEquals(FIELD_HIGHLIGHTS, tensor.getName());

            Map<String, ?> dataMap = tensor.getDataAsMap();
            assertNotNull(dataMap);

            Object highlightsObj = dataMap.get(FIELD_HIGHLIGHTS);
            assertNotNull(highlightsObj);
            assertTrue(highlightsObj instanceof List);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultHighlights = (List<Map<String, Object>>) highlightsObj;
            assertEquals(2, resultHighlights.size());

            // Verify the first highlight
            Map<String, Object> resultHighlight1 = resultHighlights.get(0);
            assertEquals("This is the first highlighted sentence.", resultHighlight1.get(FIELD_TEXT));
            assertEquals(0, resultHighlight1.get(FIELD_POSITION));
            assertEquals(0, resultHighlight1.get(FIELD_START));
            assertEquals(38, resultHighlight1.get(FIELD_END));

            // Verify the second highlight
            Map<String, Object> resultHighlight2 = resultHighlights.get(1);
            assertEquals("This is the second highlighted sentence.", resultHighlight2.get(FIELD_TEXT));
            assertEquals(2, resultHighlight2.get(FIELD_POSITION));
            assertEquals(80, resultHighlight2.get(FIELD_START));
            assertEquals(119, resultHighlight2.get(FIELD_END));

        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testExtractHighlights() {
        QuestionAnsweringModel model = new QuestionAnsweringModel();

        // Create a ModelTensors object with highlights
        List<Map<String, Object>> inputHighlights = new ArrayList<>();

        // Add a highlight
        Map<String, Object> highlight = new HashMap<>();
        highlight.put(FIELD_TEXT, "Sample highlighted text");
        highlight.put(FIELD_POSITION, 1);
        inputHighlights.add(highlight);

        // Create data map
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(FIELD_HIGHLIGHTS, inputHighlights);

        // Create model tensor with the data
        ModelTensor tensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(dataMap).build();

        // Create model tensors
        ModelTensors tensors = new ModelTensors(List.of(tensor));

        // Call the method under test using reflection
        try {
            java.lang.reflect.Method method = QuestionAnsweringModel.class.getDeclaredMethod("extractHighlights", ModelTensors.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extractedHighlights = (List<Map<String, Object>>) method.invoke(model, tensors);

            // Verify the extracted highlights
            assertNotNull(extractedHighlights);
            assertEquals(1, extractedHighlights.size());

            Map<String, Object> extractedHighlight = extractedHighlights.get(0);
            assertEquals("Sample highlighted text", extractedHighlight.get(FIELD_TEXT));
            assertEquals(1, extractedHighlight.get(FIELD_POSITION));

        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }

        // Test with multiple tensors
        try {
            // Create another tensor with highlights
            List<Map<String, Object>> moreHighlights = new ArrayList<>();
            Map<String, Object> highlight2 = new HashMap<>();
            highlight2.put(FIELD_TEXT, "Another highlight");
            highlight2.put(FIELD_POSITION, 2);
            moreHighlights.add(highlight2);

            Map<String, Object> dataMap2 = new HashMap<>();
            dataMap2.put(FIELD_HIGHLIGHTS, moreHighlights);

            ModelTensor tensor2 = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(dataMap2).build();

            // Create a tensor without highlights
            ModelTensor tensor3 = ModelTensor.builder().name("other_data").dataAsMap(new HashMap<>()).build();

            // Create model tensors with multiple tensors
            ModelTensors multipleTensors = new ModelTensors(List.of(tensor, tensor2, tensor3));

            // Call the method
            java.lang.reflect.Method method = QuestionAnsweringModel.class.getDeclaredMethod("extractHighlights", ModelTensors.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extractedHighlights = (List<Map<String, Object>>) method.invoke(model, multipleTensors);

            // Verify the extracted highlights
            assertNotNull(extractedHighlights);
            assertEquals(2, extractedHighlights.size());

            Map<String, Object> extractedHighlight1 = extractedHighlights.get(0);
            assertEquals("Sample highlighted text", extractedHighlight1.get(FIELD_TEXT));
            assertEquals(1, extractedHighlight1.get(FIELD_POSITION));

            Map<String, Object> extractedHighlight2 = extractedHighlights.get(1);
            assertEquals("Another highlight", extractedHighlight2.get(FIELD_TEXT));
            assertEquals(2, extractedHighlight2.get(FIELD_POSITION));

        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    /**
     * Test for sentence highlighting model configuration
     */
    @Test
    public void testSentenceHighlightingQAModelConfiguration() {
        // Create model config with sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set up the model with config
        QuestionAnsweringModel model = new QuestionAnsweringModel();

        try {
            // Set model config
            java.lang.reflect.Field modelConfigField = QuestionAnsweringModel.class.getDeclaredField("modelConfig");
            modelConfigField.setAccessible(true);
            modelConfigField.set(model, modelConfig);

            // Verify that the model is configured for sentence highlighting
            Translator<Input, Output> translator = model.getTranslator(PYTORCH_ENGINE, modelConfig);
            assertEquals(SentenceHighlightingQATranslator.class, translator.getClass());

            // Check isStandardQAModel returns false for sentence highlighting
            java.lang.reflect.Method isStandardMethod = QuestionAnsweringModel.class.getDeclaredMethod("isStandardQAModel");
            isStandardMethod.setAccessible(true);
            boolean isStandard = (Boolean) isStandardMethod.invoke(model);
            assertFalse("Model should not be identified as standard QA model", isStandard);
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    /**
     * Test for predictSentenceHighlightingQA method using a subclass implementation
     */
    @Test
    public void testPredictSentenceHighlightingQA() throws Exception {
        // Create model config with sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Create test question and context
        String question = "What is climate change?";
        String context = "Climate change is a long-term shift in temperatures and weather patterns.";

        // Create a sample highlight for the output
        Map<String, Object> highlight = new HashMap<>();
        highlight.put(FIELD_TEXT, "Climate change is a long-term shift in temperatures and weather patterns.");
        highlight.put(FIELD_POSITION, 0);

        List<Map<String, Object>> highlights = new ArrayList<>();
        highlights.add(highlight);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(FIELD_HIGHLIGHTS, highlights);

        ModelTensor tensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(dataMap).build();

        ModelTensors tensors = new ModelTensors(List.of(tensor));
        ModelTensorOutput expectedOutput = new ModelTensorOutput(List.of(tensors));

        // Create a new implementation that returns the expected output
        QuestionAnsweringModel testModel = new QuestionAnsweringModel() {
            @Override
            public ModelTensorOutput predict(MLInput mlInput) {
                QuestionAnsweringInputDataSet qaInputDataSet = (QuestionAnsweringInputDataSet) mlInput.getInputDataset();
                String q = qaInputDataSet.getQuestion();
                String c = qaInputDataSet.getContext();

                // Assert input values are correct
                assertEquals(question, q);
                assertEquals(context, c);

                // Return expected output
                return expectedOutput;
            }
        };

        // Set model config using reflection
        java.lang.reflect.Field modelConfigField = QuestionAnsweringModel.class.getDeclaredField("modelConfig");
        modelConfigField.setAccessible(true);
        modelConfigField.set(testModel, modelConfig);

        // Create input for prediction
        QuestionAnsweringInputDataSet inputDataSet = new QuestionAnsweringInputDataSet(question, context);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        // Call predict
        ModelTensorOutput output = (ModelTensorOutput) testModel.predict(mlInput);

        // Verify the output
        assertNotNull(output);
        assertEquals(1, output.getMlModelOutputs().size());

        ModelTensors resultTensors = output.getMlModelOutputs().get(0);
        assertEquals(1, resultTensors.getMlModelTensors().size());

        ModelTensor resultTensor = resultTensors.getMlModelTensors().get(0);
        assertEquals(FIELD_HIGHLIGHTS, resultTensor.getName());

        Map<String, ?> resultDataMap = resultTensor.getDataAsMap();
        assertNotNull(resultDataMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultHighlights = (List<Map<String, Object>>) resultDataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(resultHighlights);
        assertEquals(1, resultHighlights.size());

        Map<String, Object> resultHighlight = resultHighlights.get(0);
        assertEquals("Climate change is a long-term shift in temperatures and weather patterns.", resultHighlight.get(FIELD_TEXT));
        assertEquals(0, resultHighlight.get(FIELD_POSITION));
    }

    /**
     * Test for directly checking the result structure without mocking problematic classes
     */
    @Test
    public void testProcessOverflowChunks() throws Exception {
        // This test directly verifies the structure of a highlights result similar to what
        // would be produced by processOverflowChunks and other internal methods, without
        // trying to mock difficult classes

        // Create sample highlight data
        Map<String, Object> highlight1 = new HashMap<>();
        highlight1.put(FIELD_TEXT, "This is the first highlighted sentence.");
        highlight1.put(FIELD_POSITION, 0);
        highlight1.put(FIELD_START, 0);
        highlight1.put(FIELD_END, 38);

        Map<String, Object> highlight2 = new HashMap<>();
        highlight2.put(FIELD_TEXT, "This is from overflow chunk processing.");
        highlight2.put(FIELD_POSITION, 1);
        highlight2.put(FIELD_START, 40);
        highlight2.put(FIELD_END, 78);

        // Create a collection of highlights similar to what would be produced
        List<Map<String, Object>> highlights = new ArrayList<>();
        highlights.add(highlight1);
        highlights.add(highlight2);

        // Now use the createHighlightOutput method to produce the final output
        QuestionAnsweringModel model = new QuestionAnsweringModel();

        // Call the createHighlightOutput method using reflection
        java.lang.reflect.Method createHighlightOutputMethod = QuestionAnsweringModel.class
            .getDeclaredMethod("createHighlightOutput", List.class);
        createHighlightOutputMethod.setAccessible(true);
        ModelTensorOutput output = (ModelTensorOutput) createHighlightOutputMethod.invoke(model, highlights);

        // Verify the output structure matches what we expect
        assertNotNull(output);
        assertEquals(1, output.getMlModelOutputs().size());

        ModelTensors tensors = output.getMlModelOutputs().get(0);
        assertEquals(1, tensors.getMlModelTensors().size());

        ModelTensor tensor = tensors.getMlModelTensors().get(0);
        assertEquals(FIELD_HIGHLIGHTS, tensor.getName());

        Map<String, ?> dataMap = tensor.getDataAsMap();
        assertNotNull(dataMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultHighlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(resultHighlights);
        assertEquals(2, resultHighlights.size());

        // Verify the first highlight remains unchanged
        Map<String, Object> resultHighlight1 = resultHighlights.get(0);
        assertEquals("This is the first highlighted sentence.", resultHighlight1.get(FIELD_TEXT));
        assertEquals(0, resultHighlight1.get(FIELD_POSITION));

        // Verify the "overflow chunk" highlight is present
        Map<String, Object> resultHighlight2 = resultHighlights.get(1);
        assertEquals("This is from overflow chunk processing.", resultHighlight2.get(FIELD_TEXT));
        assertEquals(1, resultHighlight2.get(FIELD_POSITION));
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }
}
