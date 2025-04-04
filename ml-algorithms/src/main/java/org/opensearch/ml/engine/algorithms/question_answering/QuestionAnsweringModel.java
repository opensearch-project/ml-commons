/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_CONTEXT;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_QUESTION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_HIGHLIGHTS;
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

        // Initialize model type from config if not set
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

            // Process initial chunk
            processInitialChunk(question, context, allHighlights);

            // Process overflow chunks if any
            processOverflowChunks(question, context, translator, allHighlights);

            return createHighlightOutput(allHighlights);
        } catch (Exception e) {
            log.error("Error processing sentence highlighting model prediction", e);
            throw new TranslateException("Failed to process chunks for sentence highlighting", e);
        }
    }

    private void processInitialChunk(String question, String context, List<Map<String, Object>> allHighlights) throws TranslateException {
        Input initialInput = new Input();
        initialInput.add(MLInput.QUESTION_FIELD, question);
        initialInput.add(MLInput.CONTEXT_FIELD, context);
        initialInput.add(HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY, HIGHLIGHTING_MODEL_INITIAL_CHUNK_NUMBER_STRING);

        List<Output> initialOutputs = getPredictor().batchPredict(List.of(initialInput));
        for (Output output : initialOutputs) {
            ModelTensors tensors = parseModelTensorOutput(output, null);
            allHighlights.addAll(extractHighlights(tensors));
        }
    }

    private void processOverflowChunks(
        String question,
        String context,
        SentenceHighlightingQATranslator translator,
        List<Map<String, Object>> allHighlights
    ) throws TranslateException {
        Encoding encodings = translator.getTokenizer().encode(question, context);
        Encoding[] overflowEncodings = encodings.getOverflowing();

        if (overflowEncodings == null || overflowEncodings.length == 0) {
            return;
        }

        List<Input> overflowInputs = createOverflowInputs(question, context, overflowEncodings.length);
        processOverflowInputs(overflowInputs, allHighlights);
    }

    private List<Input> createOverflowInputs(String question, String context, int numOverflowChunks) {
        List<Input> overflowInputs = new ArrayList<>();
        for (int i = 0; i < numOverflowChunks; i++) {
            Input chunkInput = new Input();
            chunkInput.add(MLInput.QUESTION_FIELD, question);
            chunkInput.add(MLInput.CONTEXT_FIELD, context);
            chunkInput.add(HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY, String.valueOf(i + 1));

            overflowInputs.add(chunkInput);
        }
        return overflowInputs;
    }

    private void processOverflowInputs(List<Input> overflowInputs, List<Map<String, Object>> allHighlights) throws TranslateException {
        try {
            processOverflowInputsBatch(overflowInputs, allHighlights);
        } catch (IllegalArgumentException e) {
            log.info("Batch processing of chunks failed. Processing chunks individually: {}", e.getMessage());
            processOverflowInputsIndividually(overflowInputs, allHighlights);
        }
    }

    private void processOverflowInputsBatch(List<Input> overflowInputs, List<Map<String, Object>> allHighlights) throws TranslateException {
        List<Output> overflowOutputs = getPredictor().batchPredict(overflowInputs);
        for (Output output : overflowOutputs) {
            try {
                ModelTensors tensors = parseModelTensorOutput(output, null);
                allHighlights.addAll(extractHighlights(tensors));
            } catch (Exception e) {
                log.warn("Error processing output from chunk", e);
            }
        }
    }

    private void processOverflowInputsIndividually(List<Input> overflowInputs, List<Map<String, Object>> allHighlights)
        throws TranslateException {
        for (int i = 0; i < overflowInputs.size(); i++) {
            processOverflowChunkWithBatch(i + 1, overflowInputs.get(i), allHighlights);
        }
    }

    /**
     * Process a single overflow chunk using batchPredict and add any extracted highlights
     * 
     * @param chunkIndex The index of the overflow chunk (1-based)
     * @param chunkInput The prepared input for this chunk
     * @param highlights Collection to add extracted highlights to
     */
    private void processOverflowChunkWithBatch(int chunkIndex, Input chunkInput, List<Map<String, Object>> highlights)
        throws TranslateException {
        try {
            // Use batchPredict instead of predict to avoid the bug
            List<Output> outputs = getPredictor().batchPredict(List.of(chunkInput));
            if (outputs.isEmpty()) {
                log.warn("No output returned for chunk {}", chunkIndex);
                return;
            }

            // Process all outputs from this chunk
            for (Output output : outputs) {
                ModelTensors tensors = parseModelTensorOutput(output, null);
                List<Map<String, Object>> chunkHighlights = extractHighlights(tensors);
                highlights.addAll(chunkHighlights);
            }
        } catch (Exception e) {
            log.error("Error processing overflow chunk {}", chunkIndex, e);
            throw new TranslateException("Failed to process overflow chunk " + chunkIndex, e);
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
        combinedData.put(FIELD_HIGHLIGHTS, highlights);

        ModelTensor combinedTensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(combinedData).build();

        return new ModelTensorOutput(List.of(new ModelTensors(List.of(combinedTensor))));
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
