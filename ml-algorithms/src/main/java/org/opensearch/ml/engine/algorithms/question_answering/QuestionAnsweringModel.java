/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_CONTEXT;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_QUESTION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_HIGHLIGHTS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_POSITION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.HIGHLIGHTING_MODEL_INITIAL_CHUNK_NUMBER_STRING;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.SENTENCE_HIGHLIGHTING_TYPE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Question answering model implementation that supports both standard QA and
 * highlighting sentence.
 */
@Log4j2
@Function(FunctionName.QUESTION_ANSWERING)
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class QuestionAnsweringModel extends DLModel {
    private MLModelConfig modelConfig;
    private Translator<Input, Output> translator;

    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        if (predictor == null) {
            throw new IllegalArgumentException("predictor is null");
        }
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }

        // Initialize model config from model if it exists, the model config field is required for sentence highlighting model.
        if (modelConfig != null) {
            this.modelConfig = modelConfig;
        }

        // Create input for the predictor
        Input input = new Input();

        if (isSentenceHighlightingModel()) {
            input.add(MLInput.QUESTION_FIELD, DEFAULT_WARMUP_QUESTION);
            input.add(MLInput.CONTEXT_FIELD, DEFAULT_WARMUP_CONTEXT);
            // Add initial chunk number key value pair which is needed for sentence highlighting model
            input.add(HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY, HIGHLIGHTING_MODEL_INITIAL_CHUNK_NUMBER_STRING);
        } else {
            input.add(DEFAULT_WARMUP_QUESTION);
            input.add(DEFAULT_WARMUP_CONTEXT);
        }

        // Run prediction to warm up the model
        predictor.predict(input);
    }

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        QuestionAnsweringInputDataSet qaInputDataSet = (QuestionAnsweringInputDataSet) inputDataSet;
        String question = qaInputDataSet.getQuestion();
        String context = qaInputDataSet.getContext();

        if (isSentenceHighlightingModel()) {
            return predictSentenceHighlightingQA(question, context);
        }

        return predictStandardQA(question, context);
    }

    private boolean isSentenceHighlightingModel() {
        return modelConfig != null && SENTENCE_HIGHLIGHTING_TYPE.equalsIgnoreCase(modelConfig.getModelType());
    }

    private ModelTensorOutput predictStandardQA(String question, String context) throws TranslateException {
        Input input = new Input();
        input.add(question);
        input.add(context);

        try {
            Output output = getPredictor().predict(input);
            ModelTensors tensors = parseModelTensorOutput(output, null);
            return new ModelTensorOutput(List.of(tensors));
        } catch (Exception e) {
            log.error("Error processing standard QA model prediction", e);
            throw new TranslateException("Failed to process standard QA model prediction", e);
        }
    }

    private ModelTensorOutput predictSentenceHighlightingQA(String question, String context) throws TranslateException {
        SentenceHighlightingQATranslator translator = (SentenceHighlightingQATranslator) getTranslator(PYTORCH_ENGINE, this.modelConfig);

        try {
            List<Map<String, Object>> allHighlights = new ArrayList<>();

            // We need to process initial chunk first to get the overflow encodings
            processChunk(question, context, HIGHLIGHTING_MODEL_INITIAL_CHUNK_NUMBER_STRING, allHighlights);

            Encoding encodings = translator.getTokenizer().encode(question, context);
            Encoding[] overflowEncodings = encodings.getOverflowing();

            // Process overflow chunks if overflow encodings are present
            if (overflowEncodings != null && overflowEncodings.length > 0) {
                for (int i = 0; i < overflowEncodings.length; i++) {
                    processChunk(question, context, String.valueOf(i + 1), allHighlights);
                }
            }

            return createHighlightOutput(allHighlights);
        } catch (Exception e) {
            log.error("Error processing sentence highlighting model prediction", e);
            throw new TranslateException("Failed to process chunks for sentence highlighting", e);
        }
    }

    private void processChunk(String question, String context, String chunkNumber, List<Map<String, Object>> allHighlights)
        throws TranslateException {
        Input chunkInput = new Input();
        chunkInput.add(MLInput.QUESTION_FIELD, question);
        chunkInput.add(MLInput.CONTEXT_FIELD, context);
        chunkInput.add(HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY, chunkNumber);

        // Use batchPredict to process the chunk for complete results, predict only return the first result which can cause loss of relevant
        // results
        List<Output> outputs = getPredictor().batchPredict(List.of(chunkInput));

        if (outputs.isEmpty()) {
            return;
        }

        for (Output output : outputs) {
            ModelTensors tensors = parseModelTensorOutput(output, null);
            allHighlights.addAll(extractHighlights(tensors));
        }
    }

    /**
     * Extract highlights from model tensors output
     * 
     * @param tensors The model tensors to extract highlights from
     * @return List of highlight data maps
     */
    private List<Map<String, Object>> extractHighlights(ModelTensors tensors) throws TranslateException {
        List<Map<String, Object>> highlights = new ArrayList<>();

        for (ModelTensor tensor : tensors.getMlModelTensors()) {
            Map<String, ?> dataAsMap = tensor.getDataAsMap();
            if (dataAsMap != null && dataAsMap.containsKey(FIELD_HIGHLIGHTS)) {
                try {
                    List<Map<String, Object>> tensorHighlights = (List<Map<String, Object>>) dataAsMap.get(FIELD_HIGHLIGHTS);
                    highlights.addAll(tensorHighlights);
                } catch (ClassCastException e) {
                    log.error("Failed to cast highlights data to expected format", e);
                    throw new TranslateException("Failed to cast highlights data to expected format", e);
                }
            }
        }

        return highlights;
    }

    /**
     * Create a model tensor output for highlights
     * 
     * @param highlights The list of highlights to include
     * @return ModelTensorOutput containing highlights
     */
    private ModelTensorOutput createHighlightOutput(List<Map<String, Object>> highlights) {
        Map<String, Object> combinedData = new HashMap<>();

        // Remove duplicates and sort by position
        List<Map<String, Object>> uniqueSortedHighlights = removeDuplicatesAndSort(highlights);

        combinedData.put(FIELD_HIGHLIGHTS, uniqueSortedHighlights);

        ModelTensor combinedTensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(combinedData).build();

        return new ModelTensorOutput(List.of(new ModelTensors(List.of(combinedTensor))));
    }

    /**
     * Removes duplicate sentences and sorts them by position
     * 
     * @param highlights The list of highlights to process
     * @return List of unique highlights sorted by position
     */
    private List<Map<String, Object>> removeDuplicatesAndSort(List<Map<String, Object>> highlights) {
        // Use a map to detect duplicates by position
        Map<Number, Map<String, Object>> uniqueMap = new HashMap<>();

        // Add each highlight to the map, using position as the key
        for (Map<String, Object> highlight : highlights) {
            Number position = (Number) highlight.get(FIELD_POSITION);
            if (!uniqueMap.containsKey(position)) {
                uniqueMap.put(position, highlight);
            }
        }

        // Convert back to list
        List<Map<String, Object>> uniqueHighlights = new ArrayList<>(uniqueMap.values());

        // Sort by position
        uniqueHighlights.sort((a, b) -> {
            Number posA = (Number) a.get(FIELD_POSITION);
            Number posB = (Number) b.get(FIELD_POSITION);
            return Double.compare(posA.doubleValue(), posB.doubleValue());
        });

        return uniqueHighlights;
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) throws IllegalArgumentException {
        if (translator == null) {
            if (modelConfig != null && SENTENCE_HIGHLIGHTING_TYPE.equalsIgnoreCase(modelConfig.getModelType())) {
                translator = SentenceHighlightingQATranslator.create(modelConfig);
            } else {
                translator = new QuestionAnsweringTranslator();
            }
        }
        return translator;
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }
}
