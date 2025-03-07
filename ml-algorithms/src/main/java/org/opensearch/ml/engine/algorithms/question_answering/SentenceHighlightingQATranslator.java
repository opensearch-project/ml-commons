/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.ATTENTION_MASK;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_END;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_ERROR;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_HIGHLIGHTS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_POSITION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_START;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_TEXT;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.INPUT_IDS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.KEY_SENTENCES;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.TOKEN_TYPE_IDS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.DefaultSentenceSegmenter;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.Sentence;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.SentenceSegmenter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Translator for sentence highlighting question answering model.
 *
 * <p>Expected model output format:
 * The model should output binary predictions for each sentence, where:
 * - 1 indicates a relevant sentence (that answers the question)
 * - 0 indicates a non-relevant sentence
 * 
 * This format can be customized by overriding the isRelevantPrediction method.
 */
@Log4j2
@Getter
@Builder
public class SentenceHighlightingQATranslator implements ServingTranslator {
    /**
     * Default relevance value that indicates a sentence is relevant.
     * By default, 1 means relevant and 0 means not relevant.
     * The method specifically checks for equality with RELEVANT_VALUE (1) to determine relevance.
     */
    private static final long RELEVANT_VALUE = 1L;

    /**
     * Determines if a prediction value indicates a relevant sentence.
     * 
     * @param predictionValue The prediction value from the model
     * @return true if the prediction indicates a relevant sentence, false otherwise
     */
    protected boolean isRelevantPrediction(long predictionValue) {
        return predictionValue == RELEVANT_VALUE;
    }

    @Builder.Default
    private final SentenceSegmenter segmenter = new DefaultSentenceSegmenter();

    private HuggingFaceTokenizer tokenizer;

    /**
     * Creates a new translator with default settings.
     *
     * @return A new SentenceHighlightingQATranslator instance
     */
    public static SentenceHighlightingQATranslator createDefault() {
        return builder().build();
    }

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(path.resolve("tokenizer.json")).build();
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
        // No arguments needed for this translator
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        try {
            NDManager manager = ctx.getNDManager();
            String question = input.getAsString(0);
            String context = input.getAsString(1);

            List<Sentence> sentences = segmenter.segment(context);
            ctx.setAttachment(KEY_SENTENCES, sentences);
            ctx.setAttachment(MLInput.QUESTION_FIELD, question);

            Encoding encodings = tokenizer.encode(question, context);

            NDArray indicesArray = manager.create(encodings.getIds());
            indicesArray.setName(INPUT_IDS);

            NDArray attentionMaskArray = manager.create(encodings.getAttentionMask());
            if (attentionMaskArray.isEmpty()) {
                throw new IllegalArgumentException("Attention mask is empty in sentence highlighting QA model input");
            }
            attentionMaskArray.setName(ATTENTION_MASK);

            NDArray tokenTypeIdsArray = manager.create(encodings.getTypeIds());
            tokenTypeIdsArray.setName(TOKEN_TYPE_IDS);

            return new NDList(indicesArray, attentionMaskArray, tokenTypeIdsArray);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Error processing input: %s", e.getMessage()), e);
        }
    }

    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        try {
            Output output = new Output(200, "OK");

            @SuppressWarnings("unchecked")
            List<Sentence> sentences = (List<Sentence>) ctx.getAttachment(KEY_SENTENCES);
            boolean[] isRelevant = new boolean[sentences.size()];

            // Check if we have valid output from the model
            if (list == null || list.isEmpty()) {
                return createErrorOutput("Model returned empty or null output");
            }

            // The model returns a tensor where 1 means relevant, 0 means not relevant
            NDArray binaryPreds = list.getFirst();

            // Validate prediction shape
            if (binaryPreds.getShape().dimension() == 0 || binaryPreds.getShape().get(0) == 0) {
                return createErrorOutput(String.format("Invalid prediction shape: %s", binaryPreds.getShape()));
            }

            // Convert to boolean array
            for (int i = 0; i < Math.min(sentences.size(), binaryPreds.getShape().get(0)); i++) {
                try {
                    long predValue = binaryPreds.getLong(i);
                    isRelevant[i] = isRelevantPrediction(predValue);
                } catch (Exception e) {
                    log.warn(String.format("Error processing prediction for sentence %d: %s", i, e.getMessage()));
                    isRelevant[i] = false;
                }
            }

            // Create sentence data objects
            List<SentenceData> sentenceDataList = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                Sentence sentence = sentences.get(i);
                boolean relevant = isRelevant[i];
                sentenceDataList.add(new SentenceData(sentence.getText(), relevant, sentence.getPosition()));
            }

            // Prepare output list for relevant sentences
            List<Map<String, Object>> relevantSentenceDetails = getRelevantSentenceDetails(sentenceDataList, sentences);
            log.info("Relevant sentence details: {}", relevantSentenceDetails);

            // Create a map to hold our data
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put(FIELD_HIGHLIGHTS, relevantSentenceDetails);

            // Create the ModelTensor using the builder pattern
            ModelTensor tensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(dataMap).build();

            // Wrap in ModelTensors and convert to bytes
            ModelTensors modelTensorOutput = new ModelTensors(List.of(tensor));
            output.add(modelTensorOutput.toBytes());
            return output;
        } catch (Exception e) {
            return createErrorOutput(String.format("Error processing output: %s", e.getMessage()));
        }
    }

    private static @NotNull List<Map<String, Object>> getRelevantSentenceDetails(
        List<SentenceData> sentenceDataList,
        List<Sentence> sentences
    ) {
        List<Map<String, Object>> relevantSentenceDetails = new ArrayList<>();

        for (SentenceData data : sentenceDataList) {
            if (data.isRelevant) {
                // Find the corresponding sentence to get start and end indices
                for (Sentence sentence : sentences) {
                    if (sentence.getPosition() == data.position) {
                        Map<String, Object> sentenceDetail = new HashMap<>();
                        sentenceDetail.put(FIELD_TEXT, data.text);
                        sentenceDetail.put(FIELD_POSITION, data.position);
                        sentenceDetail.put(FIELD_START, sentence.getStartIndex());
                        sentenceDetail.put(FIELD_END, sentence.getEndIndex());
                        relevantSentenceDetails.add(sentenceDetail);
                        break;
                    }
                }
            }
        }
        return relevantSentenceDetails;
    }

    private Output createErrorOutput(String errorMessage) {
        Output output = new Output(400, "Bad Request");

        // Create a map to hold our error data
        Map<String, Object> errorData = new HashMap<>();
        errorData.put(FIELD_ERROR, errorMessage);
        errorData.put(FIELD_HIGHLIGHTS, new ArrayList<>());

        // Create the ModelTensor using the builder pattern
        ModelTensor tensor = ModelTensor.builder().name(FIELD_ERROR).dataAsMap(errorData).build();

        // Wrap in ModelTensors and convert to bytes
        ModelTensors modelTensorOutput = new ModelTensors(List.of(tensor));
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    // Helper class to store sentence data
    private record SentenceData(String text, boolean isRelevant, int position) {
    }
}
