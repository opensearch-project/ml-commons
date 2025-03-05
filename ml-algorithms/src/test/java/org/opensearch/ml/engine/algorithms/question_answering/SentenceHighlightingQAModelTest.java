/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.*;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SentenceHighlightingQAModelTest {

    private File modelZipFile;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private QuestionAnsweringModel questionAnsweringModel;
    private Path mlCachePath;
    private QuestionAnsweringInputDataSet inputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;
    private Gson gson = new Gson();

    @Before
    public void setUp() throws URISyntaxException {
        // Set up paths and encryptor
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);

        // Create model config for sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Create model with config
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_sentence_highlighting_model")
            .modelId("test_model_id")
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .modelConfig(modelConfig)
            .build();

        // Set up model helper and parameters
        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("sentence_highlighting_qa_model_pt.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);

        // Create test input data
        String question = "What are the impacts of climate change?";
        String context = "Many coastal cities face increased flooding during storms. "
            + "Farmers are experiencing unpredictable growing seasons and crop failures. "
            + "Scientists predict these environmental shifts will continue to accelerate. "
            + "Global temperatures have risen significantly over the past century. "
            + "Polar ice caps are melting at an alarming rate.";

        inputDataSet = QuestionAnsweringInputDataSet.builder().question(question).context(context).build();

        // Create model instance
        questionAnsweringModel = new QuestionAnsweringModel();
    }

    @Test
    public void testSentenceHighlightingPrediction() throws TranslateException {
        // Initialize the model with sentence highlighting configuration
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        // Mock the predictor to return a sample output with highlights
        @SuppressWarnings("unchecked")
        Predictor<Input, Output> predictor = mock(Predictor.class);

        // Create sample output with highlighted sentences
        JsonArray highlightsArray = new JsonArray();

        // Add first highlight
        Map<String, Object> highlight1 = new HashMap<>();
        highlight1.put(FIELD_TEXT, "Many coastal cities face increased flooding during storms.");
        highlight1.put(FIELD_POSITION, 0);
        highlightsArray.add(JsonParser.parseString(gson.toJson(highlight1)));

        // Add second highlight
        Map<String, Object> highlight2 = new HashMap<>();
        highlight2.put(FIELD_TEXT, "Global temperatures have risen significantly over the past century.");
        highlight2.put(FIELD_POSITION, 3);
        highlightsArray.add(JsonParser.parseString(gson.toJson(highlight2)));

        // Create model tensor with highlights
        ModelTensor highlightsTensor = new ModelTensor(FIELD_HIGHLIGHTS, highlightsArray.toString());
        ModelTensors modelTensors = new ModelTensors(List.of(highlightsTensor));

        // Create output with the model tensors
        Output output = new Output();
        output.add(modelTensors.toBytes());

        when(predictor.predict(any(Input.class))).thenReturn(output);

        // Use reflection to set the predictor in the model
        try {
            java.lang.reflect.Field predictorsField = questionAnsweringModel.getClass().getSuperclass().getDeclaredField("predictors");
            predictorsField.setAccessible(true);
            Predictor<Input, Output>[] predictorsArray = new Predictor[1];
            predictorsArray[0] = predictor;
            predictorsField.set(questionAnsweringModel, predictorsArray);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set predictor field", e);
        }

        // Process input
        ModelTensorOutput modelOutput = (ModelTensorOutput) questionAnsweringModel.predict(mlInput);

        // Verify output
        assertNotNull(modelOutput);
        List<ModelTensors> tensorsList = modelOutput.getMlModelOutputs();

        assertEquals(1, tensorsList.size());
        ModelTensors outputTensors = tensorsList.get(0);
        List<ModelTensor> modelTensorsList = outputTensors.getMlModelTensors();

        assertEquals(1, modelTensorsList.size());
        ModelTensor resultTensor = modelTensorsList.get(0);
        assertEquals(FIELD_HIGHLIGHTS, resultTensor.getName());

        // Parse and verify highlights
        String highlightsJson = resultTensor.getResult();
        JsonArray highlights = JsonParser.parseString(highlightsJson).getAsJsonArray();

        assertEquals(2, highlights.size());

        // Verify first highlight
        JsonElement firstHighlight = highlights.get(0);
        assertEquals(0, firstHighlight.getAsJsonObject().get(FIELD_POSITION).getAsInt());
        assertEquals(
            "Many coastal cities face increased flooding during storms.",
            firstHighlight.getAsJsonObject().get(FIELD_TEXT).getAsString()
        );

        // Verify second highlight
        JsonElement secondHighlight = highlights.get(1);
        assertEquals(3, secondHighlight.getAsJsonObject().get(FIELD_POSITION).getAsInt());
        assertEquals(
            "Global temperatures have risen significantly over the past century.",
            secondHighlight.getAsJsonObject().get(FIELD_TEXT).getAsString()
        );

        // Clean up
        questionAnsweringModel.close();
    }

    @Test
    public void testModelConfigWithSentenceHighlighting() {
        // Create model config for sentence highlighting
        MLModelConfig modelConfig = QuestionAnsweringModelConfig
            .builder()
            .modelType(SENTENCE_HIGHLIGHTING_TYPE)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        // Set the model config to use sentence highlighting
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_sentence_highlighting_model")
            .modelId("test_model_id")
            .algorithm(FunctionName.QUESTION_ANSWERING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .modelConfig(modelConfig)
            .build();

        // Initialize the model
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        // Verify that the model is set up for sentence highlighting
        // This is an indirect test since we can't directly access the private field
        try {
            // Mock the predictor to avoid actual prediction
            @SuppressWarnings("unchecked")
            Predictor<Input, Output> predictor = mock(Predictor.class);
            Output mockOutput = mock(Output.class);
            when(predictor.predict(any(Input.class))).thenReturn(mockOutput);

            // Use reflection to set the predictor in the model
            java.lang.reflect.Field predictorsField = questionAnsweringModel.getClass().getSuperclass().getDeclaredField("predictors");
            predictorsField.setAccessible(true);
            Predictor<Input, Output>[] predictorsArray = new Predictor[1];
            predictorsArray[0] = predictor;
            predictorsField.set(questionAnsweringModel, predictorsArray);

            // This should not throw a ClassCastException if the model is properly configured for sentence highlighting
            questionAnsweringModel.predict(mlInput);
        } catch (Exception e) {
            // We expect a NullPointerException or MLException because we haven't fully mocked the prediction path
            // But we should not get a ClassCastException which would indicate incorrect setup
            assertTrue(
                "Expected NullPointerException or MLException but got: " + e.getClass().getName(),
                e instanceof NullPointerException
                    || e instanceof IllegalArgumentException
                    || e.getClass().getName().equals("org.opensearch.ml.common.exception.MLException")
            );
        } finally {
            // Clean up
            questionAnsweringModel.close();
        }
    }
}
