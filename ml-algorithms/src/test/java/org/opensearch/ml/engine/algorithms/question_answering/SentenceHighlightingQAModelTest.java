/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import com.google.gson.Gson;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
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
            .allConfig("{\"token_max_length\":64,\"token_overlap_stride\":16,\"with_overflowing_tokens\":true,\"padding\":false}")
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
            + "Rising sea levels threaten coastal infrastructure and communities. "
            + "Farmers are experiencing unpredictable growing seasons and crop failures. "
            + "Droughts are becoming more frequent and severe in many regions. "
            + "Scientists predict these environmental shifts will continue to accelerate. "
            + "Global temperatures have risen significantly over the past century. "
            + "Polar ice caps are melting at an alarming rate. "
            + "Extreme weather events are becoming more frequent and intense. "
            + "Biodiversity is declining as ecosystems struggle to adapt. "
            + "Mountain glaciers are retreating worldwide at unprecedented rates. ";

        inputDataSet = QuestionAnsweringInputDataSet.builder().question(question).context(context).build();

        // Create model instance
        questionAnsweringModel = new QuestionAnsweringModel();
    }

    @Test
    public void testSentenceHighlightingPrediction() throws TranslateException, NoSuchFieldException, IllegalAccessException {
        // Initialize the model
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        // Mock the predictor to return specific output
        Predictor<Input, Output> mockPredictor = mock(Predictor.class);
        Output mockOutput = new Output();

        // Create mock output tensor with sentence indices [3, 0]
        NDArray mockTensor = mock(NDArray.class);
        when(mockTensor.toLongArray()).thenReturn(new long[] { 3, 0 });
        NDList mockNDList = new NDList(mockTensor);

        // Create mock output with the tensor
        ModelTensor tensor = ModelTensor
            .builder()
            .name(FIELD_HIGHLIGHTS)
            .dataAsMap(
                Map
                    .of(
                        FIELD_HIGHLIGHTS,
                        List
                            .of(
                                Map
                                    .of(
                                        FIELD_POSITION,
                                        3.0,
                                        FIELD_TEXT,
                                        "Global temperatures have risen significantly over the past century.",
                                        FIELD_START,
                                        208.0,
                                        FIELD_END,
                                        275.0
                                    ),
                                Map
                                    .of(
                                        FIELD_POSITION,
                                        0.0,
                                        FIELD_TEXT,
                                        "Many coastal cities face increased flooding during storms.",
                                        FIELD_START,
                                        0.0,
                                        FIELD_END,
                                        58.0
                                    )
                            )
                    )
            )
            .build();

        ModelTensors tensors = new ModelTensors(List.of(tensor));
        mockOutput.add(tensors.toBytes());

        // Set up the mock predictor
        when(mockPredictor.batchPredict(any())).thenReturn(List.of(mockOutput));

        // Use reflection to set the mock predictor
        java.lang.reflect.Field predictorsField = DLModel.class.getDeclaredField("predictors");
        predictorsField.setAccessible(true);
        predictorsField.set(questionAnsweringModel, new Predictor[] { mockPredictor });

        // Get prediction
        ModelTensorOutput output = questionAnsweringModel.predict("test_model_id", mlInput);
        assertNotNull(output);
        assertFalse(output.getMlModelOutputs().isEmpty());

        // Get the first tensor output
        ModelTensors resultTensors = output.getMlModelOutputs().get(0);
        assertNotNull(resultTensors);
        assertFalse(resultTensors.getMlModelTensors().isEmpty());

        // Get the highlights tensor
        ModelTensor resultTensor = resultTensors.getMlModelTensors().get(0);
        assertNotNull(resultTensor);
        assertEquals(FIELD_HIGHLIGHTS, resultTensor.getName());

        // Get the highlights from the dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) resultTensor.getDataAsMap();
        assertNotNull("DataAsMap should not be null", dataMap);

        // Should have no error
        assertFalse("Should not have error", dataMap.containsKey(FIELD_ERROR));

        // Should have highlights
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull("Highlights should not be null", highlights);
        // After deduplication, we expect only unique highlights based on position
        assertEquals("Should have unique highlights after deduplication", 2, highlights.size());

        // Verify first highlight
        Map<String, Object> firstHighlight = highlights.get(0);
        assertEquals(0.0, firstHighlight.get(FIELD_POSITION));
        assertEquals("Many coastal cities face increased flooding during storms.", firstHighlight.get(FIELD_TEXT));
        assertEquals(0.0, firstHighlight.get(FIELD_START));
        assertEquals(58.0, firstHighlight.get(FIELD_END));

        // Verify second highlight
        Map<String, Object> secondHighlight = highlights.get(1);
        assertEquals(3.0, secondHighlight.get(FIELD_POSITION));
        assertEquals("Global temperatures have risen significantly over the past century.", secondHighlight.get(FIELD_TEXT));
        assertEquals(208.0, secondHighlight.get(FIELD_START));
        assertEquals(275.0, secondHighlight.get(FIELD_END));

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

        // Verify that the model is set up for sentence highlighting
        assertEquals(SENTENCE_HIGHLIGHTING_TYPE, modelConfig.getModelType());
        assertEquals(SentenceHighlightingQATranslator.class, questionAnsweringModel.getTranslator("pytorch", modelConfig).getClass());
    }
}
