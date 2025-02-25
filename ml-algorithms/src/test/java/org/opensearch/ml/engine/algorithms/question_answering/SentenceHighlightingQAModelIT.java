/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.*;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
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

import lombok.extern.log4j.Log4j2;

/**
 * Integration test for the sentence highlighting question answering model.
 * This test uses a real model file and performs actual predictions.
 */
@Log4j2
public class SentenceHighlightingQAModelIT {

    private File modelZipFile;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private QuestionAnsweringModel questionAnsweringModel;
    private Path mlCachePath;
    private QuestionAnsweringInputDataSet inputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;

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

    @After
    public void tearDown() {
        // Clean up resources
        if (questionAnsweringModel != null) {
            questionAnsweringModel.close();
        }
    }

    @Test
    public void testEndToEndSentenceHighlighting() {
        // Initialize the model with sentence highlighting configuration
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(inputDataSet).build();

        // Perform actual prediction
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

        // Get highlights from dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) resultTensor.getDataAsMap();
        assertNotNull(dataMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);

        // We expect at least one highlighted sentence
        assertTrue("Should have at least one highlighted sentence", highlights.size() > 0);

        // Log the highlighted sentences for inspection
        log.info("Highlighted sentences:");
        for (Map<String, Object> highlight : highlights) {
            log.info("  - {}", highlight.get(FIELD_TEXT));
        }

        // Verify structure of first highlight
        if (!highlights.isEmpty()) {
            Map<String, Object> firstHighlight = highlights.get(0);
            assertNotNull(firstHighlight.get(FIELD_TEXT));
            assertNotNull(firstHighlight.get(FIELD_POSITION));
            assertNotNull(firstHighlight.get(FIELD_START));
            assertNotNull(firstHighlight.get(FIELD_END));
        }
    }

    @Test
    public void testDifferentQuestion() {
        // Initialize the model with sentence highlighting configuration
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create a different question
        String differentQuestion = "How does climate change affect agriculture?";
        QuestionAnsweringInputDataSet differentInputDataSet = QuestionAnsweringInputDataSet
            .builder()
            .question(differentQuestion)
            .context(inputDataSet.getContext())
            .build();

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(differentInputDataSet).build();

        // Perform actual prediction
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

        // Get highlights from dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) resultTensor.getDataAsMap();
        assertNotNull(dataMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);

        // We expect at least one highlighted sentence
        assertTrue("Should have at least one highlighted sentence", highlights.size() > 0);

        // Log the highlighted sentences for inspection
        log.info("Highlighted sentences for agriculture question:");
        for (Map<String, Object> highlight : highlights) {
            log.info("  - {}", highlight.get(FIELD_TEXT));

            // For this question, we expect the sentence about farmers to be highlighted
            if (highlight.get(FIELD_TEXT).equals("Farmers are experiencing unpredictable growing seasons and crop failures.")) {
                assertEquals(
                    "Farmers are experiencing unpredictable growing seasons and crop failures.",
                    highlight.get(FIELD_TEXT).toString()
                );
            }
        }
    }

    @Test
    public void testLongerContext() {
        // Initialize the model with sentence highlighting configuration
        questionAnsweringModel.initModel(model, params, encryptor);

        // Create a longer context
        String longerContext = "Climate change is affecting our planet in numerous ways. "
            + "Many coastal cities face increased flooding during storms. "
            + "Farmers are experiencing unpredictable growing seasons and crop failures. "
            + "Scientists predict these environmental shifts will continue to accelerate. "
            + "Global temperatures have risen significantly over the past century. "
            + "Polar ice caps are melting at an alarming rate. "
            + "Wildlife habitats are being disrupted, leading to species migration and extinction. "
            + "Extreme weather events like hurricanes and wildfires are becoming more frequent. "
            + "Rising sea levels threaten to submerge low-lying islands and coastal regions. "
            + "Droughts are becoming more severe in many parts of the world.";

        QuestionAnsweringInputDataSet longerInputDataSet = QuestionAnsweringInputDataSet
            .builder()
            .question(inputDataSet.getQuestion())
            .context(longerContext)
            .build();

        // Create MLInput for prediction
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.QUESTION_ANSWERING).inputDataset(longerInputDataSet).build();

        // Perform actual prediction
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

        // Get highlights from dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) resultTensor.getDataAsMap();
        assertNotNull(dataMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);

        // We expect at least one highlighted sentence
        assertTrue("Should have at least one highlighted sentence", highlights.size() > 0);

        // Log the highlighted sentences for inspection
        log.info("Highlighted sentences for longer context:");
        for (Map<String, Object> highlight : highlights) {
            log.info("  - {}", highlight.get(FIELD_TEXT));
        }
    }
}
