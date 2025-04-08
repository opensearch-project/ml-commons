/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.ATTENTION_MASK;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.CONTEXT_START_DEFAULT_INDEX;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_PADDING;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_TOKEN_MAX_LENGTH;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_TOKEN_OVERLAP_STRIDE_LENGTH;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WITH_OVERFLOWING_TOKENS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_END;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_ERROR;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_HIGHLIGHTS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_POSITION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_START;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.FIELD_TEXT;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.IGNORE_TOKEN_ID;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.INPUT_IDS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.KEY_SENTENCES;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.PADDING_KEY;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.SENTENCE_IDS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.TOKENIZER_FILE_NAME;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.TOKEN_MAX_LENGTH_KEY;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.TOKEN_OVERLAP_STRIDE_LENGTH_KEY;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.TOKEN_TYPE_IDS;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.WITH_OVERFLOWING_TOKENS_KEY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
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
 * This translator processes input for semantic sentence highlighting models that identify
 * relevant sentences within a context document based on a user query or question.
 * 
 * The translator performs the following key functions:
 * 1. Tokenizes the question and context using Hugging Face tokenizer
 * 2. Segments the context into sentences 
 * 3. Maps tokens to their corresponding sentence IDs
 * 4. Handles chunking for long contexts that exceed the model's maximum token length
 * 5. Processes model outputs to identify and highlight sentences that answer the question
 * 
 * The highlighted sentences are returned with their text and position information within
 * the original context, which allows for easy visualization and extraction of relevant
 * information from the document.
 */
@Log4j2
@Getter
@Builder
public class SentenceHighlightingQATranslator implements ServingTranslator {
    /**
     * This translator works with the semantic sentence highlighting model, which returns
     * sentence indices directly rather than binary relevance scores.
     */

    @Builder.Default
    private final SentenceSegmenter segmenter = new DefaultSentenceSegmenter();

    private HuggingFaceTokenizer tokenizer;

    private final MLModelConfig modelConfig;

    /**
     * Helper method to read a value from allConfig with a default fallback
     * @param <T> The type of value to read (String, Integer, Boolean)
     * @param key The config key to read
     * @param defaultValue The default value to use if key not found or parsing fails
     * @param valueType The class of the value type for type safety
     * @return The value from config or default if not found
     */
    <T> T readFromModelAllConfig(String key, T defaultValue, Class<T> valueType) {
        if (modelConfig == null || modelConfig.getAllConfig() == null) {
            return defaultValue;
        }

        try (XContentParser parser = JsonXContent.jsonXContent.createParser(null, null, modelConfig.getAllConfig())) {
            Map<String, Object> configMap = parser.map();
            Object value = configMap.get(key);

            if (value == null) {
                return defaultValue;
            }

            try {
                if (valueType == Boolean.class) {
                    return valueType.cast(Boolean.valueOf(value.toString()));
                } else if (valueType == Integer.class) {
                    return valueType.cast(Integer.valueOf(value.toString()));
                } else {
                    return valueType.cast(value.toString());
                }
            } catch (Exception e) {
                log.warn("Failed to parse value for key {}: {}", key, value);
                return defaultValue;
            }
        } catch (Exception e) {
            log.warn("Failed to read {} from config, using default value", key, e);
            return defaultValue;
        }
    }

    /**
     * Creates a new translator with the given model configuration.
     *
     * @param modelConfig The model configuration
     * @return A new SentenceHighlightingQATranslator instance
     */
    public static SentenceHighlightingQATranslator create(MLModelConfig modelConfig) {
        return builder().modelConfig(modelConfig).build();
    }

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();

        // read max_seq_len from model config using helper method
        int tokenMaxLength = readFromModelAllConfig(TOKEN_MAX_LENGTH_KEY, DEFAULT_TOKEN_MAX_LENGTH, Integer.class);
        int tokenOverlapStride = readFromModelAllConfig(
            TOKEN_OVERLAP_STRIDE_LENGTH_KEY,
            DEFAULT_TOKEN_OVERLAP_STRIDE_LENGTH,
            Integer.class
        );
        boolean withOverflowingTokens = readFromModelAllConfig(WITH_OVERFLOWING_TOKENS_KEY, DEFAULT_WITH_OVERFLOWING_TOKENS, Boolean.class);
        boolean padding = readFromModelAllConfig(PADDING_KEY, DEFAULT_PADDING, Boolean.class);

        tokenizer = HuggingFaceTokenizer
            .builder()
            .optTokenizerPath(path.resolve(TOKENIZER_FILE_NAME))
            .optMaxLength(tokenMaxLength)
            .optStride(tokenOverlapStride)
            .optWithOverflowingTokens(withOverflowingTokens)
            .optTruncateSecondOnly()
            .optPadding(padding)
            .build();
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
        // No arguments needed for this translator
    }

    /**
     * Prepares the translator by initializing the tokenizer with the appropriate configuration.
     *
     * The tokenizer is configured to handle chunking for long contexts that exceed the model's
     * maximum token length. Even when processing individual chunks, the full context is always 
     * passed to the model in the input stage, ensuring that sentence segmentation and token-to-sentence
     * mapping is consistent across all chunks.
     *
     * @param ctx The translator context which provides access to the model path
     * @throws IOException If there is an error loading the tokenizer
     */
    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        try {
            NDManager manager = ctx.getNDManager();
            String question = input.getAsString(MLInput.QUESTION_FIELD);
            String context = input.getAsString(MLInput.CONTEXT_FIELD);
            int chunkNumber = Integer.parseInt(input.getAsString(HIGHLIGHTING_MODEL_CHUNK_NUMBER_KEY));

            // Store the full context and question for reference
            ctx.setAttachment(MLInput.QUESTION_FIELD, question);
            ctx.setAttachment(MLInput.CONTEXT_FIELD, context);

            // Step 1: Split context into sentences (using full context)
            List<Sentence> sentences = segmenter.segment(context);
            ctx.setAttachment(KEY_SENTENCES, sentences);

            // Step 2: Create word-level sentence IDs from full context
            int[] wordLevelSentenceIds = createWordLevelSentenceIds(sentences, context);

            // Step 3: Get the target chunk's encoding
            Encoding targetEncoding = getChunkEncoding(question, context, chunkNumber);

            // Step 4: Create sentence IDs array for this chunk
            int[] sentenceIdsArray = createSentenceIdsArray(targetEncoding, wordLevelSentenceIds, chunkNumber);

            // Step 5: Create NDArrays for model input
            return createModelInputs(manager, targetEncoding, sentenceIdsArray);

        } catch (Exception e) {
            log.error("Error processing input", e);
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Error processing input: %s", e.getMessage()), e);
        }
    }

    /**
     * Get the encoding for a specific chunk
     */
    private Encoding getChunkEncoding(String question, String context, int chunkNumber) {
        Encoding fullEncoding = tokenizer.encode(question, context);

        if (chunkNumber == 0) {
            return fullEncoding;
        } else {
            Encoding[] overflowEncodings = fullEncoding.getOverflowing();
            if (overflowEncodings != null && chunkNumber <= overflowEncodings.length) {
                return overflowEncodings[chunkNumber - 1];
            } else {
                throw new IllegalArgumentException("Invalid chunk number: " + chunkNumber);
            }
        }
    }

    /**
     * Create sentence IDs array for the given chunk
     */
    private int[] createSentenceIdsArray(Encoding encoding, int[] wordLevelSentenceIds, int chunkNumber) {
        long[] wordIds = encoding.getWordIds();
        int[] sentenceIdsArray = new int[wordIds.length];
        Arrays.fill(sentenceIdsArray, IGNORE_TOKEN_ID); // Initialize with ignore token

        // Find where the context starts in this chunk
        long[] typeIds = encoding.getTypeIds();
        int contextStartIndex = findContextStartIndex(typeIds);

        // Map word IDs to sentence IDs
        for (int i = contextStartIndex; i < wordIds.length; i++) {
            long wordId = wordIds[i];
            if (wordId != -1 && wordId < wordLevelSentenceIds.length) {
                sentenceIdsArray[i] = wordLevelSentenceIds[(int) wordId];
            }
        }

        return sentenceIdsArray;
    }

    /**
     * Find where the context starts in the token sequence
     */
    private int findContextStartIndex(long[] typeIds) {
        for (int i = 0; i < typeIds.length; i++) {
            if (typeIds[i] == 1) {
                return i;
            }
        }
        return CONTEXT_START_DEFAULT_INDEX;  // Default to 0 if not found
    }

    /**
     * Create model inputs from encodings and sentence IDs
     */
    private NDList createModelInputs(NDManager manager, Encoding encoding, int[] sentenceIdsArray) {
        NDArray sentenceIdsNDArray = manager.create(sentenceIdsArray);
        NDArray inputIds = manager.create(encoding.getIds());
        NDArray attentionMask = manager.create(encoding.getAttentionMask());
        NDArray tokenTypeIds = manager.create(encoding.getTypeIds());

        sentenceIdsNDArray.setName(SENTENCE_IDS);
        inputIds.setName(INPUT_IDS);
        attentionMask.setName(ATTENTION_MASK);
        tokenTypeIds.setName(TOKEN_TYPE_IDS);

        return new NDList(inputIds, attentionMask, tokenTypeIds, sentenceIdsNDArray);
    }

    /**
     * Creates an array mapping each word in the context to its sentence ID
     */
    private int[] createWordLevelSentenceIds(List<Sentence> sentences, String context) {
        String[] contextWords = context.split("\\s+");
        int[] wordSentenceIds = new int[contextWords.length];

        // For each sentence
        for (int sentIdx = 0; sentIdx < sentences.size(); sentIdx++) {
            Sentence sentence = sentences.get(sentIdx);
            int startIndex = sentence.getStartIndex();
            int endIndex = sentence.getEndIndex();

            // Find all words within the sentence's start and end indices
            for (int wordIdx = 0; wordIdx < contextWords.length; wordIdx++) {
                int wordStart = 0;
                for (int i = 0; i < wordIdx; i++) {
                    wordStart += contextWords[i].length() + 1; // +1 for space
                }
                int wordEnd = wordStart + contextWords[wordIdx].length();

                // If word is within sentence boundaries, assign it this sentence ID
                if (wordStart >= startIndex && wordEnd <= endIndex) {
                    wordSentenceIds[wordIdx] = sentIdx;
                }
            }
        }

        return wordSentenceIds;
    }

    /**
     * Processes the model's output to extract highlighted sentences. 
     *
     * @param ctx The translator context containing sentence information
     * @param list The model's output predictions
     * @return Formatted output with highlighted sentence details or error information
     */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        try {
            // Get the sentences we stored during input processing
            List<Sentence> sentences;
            try {
                sentences = (List<Sentence>) ctx.getAttachment(KEY_SENTENCES);
            } catch (ClassCastException e) {
                log.error("Failed to cast sentences data to expected format", e);
                return createErrorOutput("Failed to process sentences data");
            }

            if (sentences == null || sentences.isEmpty()) {
                return createErrorOutput("No sentences found in context");
            }

            // Process model output to get highlighted sentence indices
            Set<Integer> highlightedIndices = new HashSet<>();
            for (NDArray array : list) {
                long[] indices = array.toLongArray();
                for (long idx : indices) {
                    if (idx >= 0 && idx < sentences.size()) {
                        highlightedIndices.add((int) idx);
                    }
                }
            }

            if (highlightedIndices.isEmpty()) {
                log.warn("No relevant sentences found in model output");
                return createErrorOutput("No relevant sentences found");
            }

            // Convert indices to SentenceData objects
            List<SentenceData> sentenceDataList = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                Sentence sentence = sentences.get(i);
                boolean isRelevant = highlightedIndices.contains(i);
                sentenceDataList.add(new SentenceData(sentence.getText(), isRelevant, i));
            }

            // Get the relevant sentence details
            List<Map<String, Object>> highlights = getRelevantSentenceDetails(sentenceDataList, sentences);

            // Create output map
            Map<String, Object> outputMap = new HashMap<>();
            outputMap.put(FIELD_HIGHLIGHTS, highlights);

            // Create output tensor
            ModelTensor tensor = ModelTensor.builder().name(FIELD_HIGHLIGHTS).dataAsMap(outputMap).build();

            // Create final output
            Output output = new Output();
            output.add(new ModelTensors(List.of(tensor)).toBytes());

            return output;

        } catch (Exception e) {
            log.error("Error processing model output", e);
            return createErrorOutput("Error processing model output: " + e.getMessage());
        }
    }

    private static @NotNull List<Map<String, Object>> getRelevantSentenceDetails(
        List<SentenceData> sentenceDataList,
        List<Sentence> sentences
    ) {
        List<Map<String, Object>> relevantSentenceDetails = new ArrayList<>();

        // Process each sentence - use array index for position
        for (int i = 0; i < sentenceDataList.size(); i++) {
            SentenceData data = sentenceDataList.get(i);
            if (data.isRelevant) {
                // Get the corresponding sentence object
                Sentence sentence = sentences.get(i);

                // Create details map for this sentence
                Map<String, Object> sentenceDetail = new HashMap<>();
                sentenceDetail.put(FIELD_TEXT, data.text);
                sentenceDetail.put(FIELD_POSITION, i);
                sentenceDetail.put(FIELD_START, sentence.getStartIndex());
                sentenceDetail.put(FIELD_END, sentence.getEndIndex());
                relevantSentenceDetails.add(sentenceDetail);
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
