/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering.sentence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;

/**
 * Default implementation of SentenceSegmenter.
 * Handles common sentence segmentation cases including abbreviations and quotes.
 */
@Log4j2
public class DefaultSentenceSegmenter implements SentenceSegmenter {
    /**
     * Regex pattern for splitting text into sentences.
     * This pattern handles common sentence boundaries while avoiding false positives
     * with abbreviations and other special cases.
     * <p>
     * Pattern explanation:
     * - (?<!\\w\\.\\w.) - Negative lookbehind to avoid splitting on abbreviations like "e.g."
     * - (?<!\\w[A-Z]\\.) - Negative lookbehind to avoid splitting on initials like "J. Smith"
     * - (?<=[.!?]) - Positive lookbehind to match after sentence-ending punctuation
     * - (?<!\\w[A-Z]\\.[A-Z]\\.) - Negative lookbehind to avoid splitting on acronyms like "U.S.A."
     * - \\s+ - Match one or more whitespace characters
     */
    private static final Pattern SENTENCE_PATTERN = Pattern
        .compile("(?<!\\w\\.\\w.)(?<!\\w[A-Z]\\.)(?<=[.!?])(?<!\\w[A-Z]\\.[A-Z]\\.)\\s+");

    /**
     * Pre-compiled patterns for common abbreviations to improve performance.
     */
    private static final Map<String, Pattern> ABBREVIATION_PATTERNS = new HashMap<>();

    private final SentenceSegmentationConfig defaultConfig;

    static {
        // Pre-compile abbreviation patterns for better performance
        SentenceSegmentationConfig defaultConfig = SentenceSegmentationConfig.getDefault();
        for (String abbr : defaultConfig.getCommonAbbreviations()) {
            ABBREVIATION_PATTERNS.put(abbr, Pattern.compile("\\b" + Pattern.quote(abbr) + "\\s+"));
        }
    }

    /**
     * Creates a new DefaultSentenceSegmenter with default configuration.
     */
    public DefaultSentenceSegmenter() {
        this.defaultConfig = SentenceSegmentationConfig.getDefault();
    }

    /**
     * Creates a new DefaultSentenceSegmenter with custom configuration.
     *
     * @param config The configuration to use
     */
    public DefaultSentenceSegmenter(SentenceSegmentationConfig config) {
        this.defaultConfig = config != null ? config : SentenceSegmentationConfig.getDefault();
    }

    @Override
    public List<Sentence> segment(String text) {
        return segment(text, defaultConfig);
    }

    @Override
    public List<Sentence> segment(String text, SentenceSegmentationConfig config) {
        return segmentSentences(text, config);
    }

    /**
     * Segments text into sentences using the specified configuration.
     *
     * @param text The text to segment
     * @param config The configuration to use
     * @return List of Sentence objects
     * @throws IllegalArgumentException if the input text is null or empty
     */
    private List<Sentence> segmentSentences(String text, SentenceSegmentationConfig config) {
        List<Sentence> sentences = new ArrayList<>();
        String[] rawSentences = splitIntoSentences(text, config);

        log.debug("Split into {} raw sentences", rawSentences.length);

        if (rawSentences.length == 0) {
            throw new IllegalArgumentException("Failed to split text into sentences");
        }

        int currentPosition = 0;
        int currentIndex = 0;

        for (String rawSentence : rawSentences) {
            String trimmedSentence = config.isPreserveWhitespace() ? rawSentence : rawSentence.trim();

            // Include all sentences regardless of length
            int startIndex = text.indexOf(rawSentence, currentIndex);
            if (startIndex == -1) {
                // Fallback to a more robust search if exact match fails
                startIndex = findApproximatePosition(text, rawSentence, currentIndex);
            }
            int endIndex = startIndex + rawSentence.length();

            Sentence.SentenceType type = determineSentenceType(trimmedSentence);

            sentences
                .add(
                    Sentence
                        .builder()
                        .text(trimmedSentence)
                        .startIndex(startIndex)
                        .endIndex(endIndex)
                        .position(currentPosition)
                        .type(type)
                        .build()
                );

            currentPosition++;
            currentIndex = endIndex;
        }

        if (sentences.isEmpty()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Failed to segment text into sentences: %s", text));
        }

        return sentences;
    }

    /**
     * Splits text into raw sentences based on the configuration.
     *
     * @param text The text to split
     * @param config The configuration to use
     * @return Array of raw sentence strings
     */
    private String[] splitIntoSentences(String text, SentenceSegmentationConfig config) {
        String processedText = text;

        if (config.isHandleAbbreviations()) {
            processedText = handleAbbreviations(processedText, config.getCommonAbbreviations());
        }

        if (config.isHandleQuotes()) {
            log.debug("Handling quotes");
            processedText = handleQuotes(processedText);
        }

        Pattern splitPattern = SENTENCE_PATTERN;
        if (!config.getCustomSentenceDelimiters().isEmpty()) {
            String pattern = SENTENCE_PATTERN.pattern() + "|" + config.getCustomSentenceDelimiters();
            splitPattern = Pattern.compile(pattern);
            log.debug("Using custom delimiters: {}", config.getCustomSentenceDelimiters());
        }

        String[] result = splitPattern.split(processedText);
        log.debug("Split result: {} sentences", result.length);
        return result;
    }

    /**
     * Handles abbreviations in text to prevent false sentence boundaries.
     * Uses pre-compiled patterns for better performance.
     *
     * @param text The text to process
     * @param abbreviations Array of abbreviations to handle
     * @return Processed text with abbreviations handled
     */
    private String handleAbbreviations(String text, String[] abbreviations) {
        if (text == null || text.isEmpty() || abbreviations == null || abbreviations.length == 0) {
            return text;
        }

        String processedText = text;
        for (String abbr : abbreviations) {
            // Use pre-compiled pattern from the map if available
            Pattern pattern = ABBREVIATION_PATTERNS.get(abbr);
            if (pattern == null) {
                // Only create a new pattern if not already in the map
                pattern = Pattern.compile("\\b" + Pattern.quote(abbr) + "\\s+");
            }

            Matcher matcher = pattern.matcher(processedText);
            if (matcher.find()) {
                // Replace periods with a special marker to prevent sentence splitting
                processedText = matcher.replaceAll(abbr.replace(".", "@@@") + " ");
                log.debug("Handled abbreviation: {}", abbr);
            }
        }
        return processedText;
    }

    /**
     * Handles quotes in text to ensure proper sentence boundaries.
     * Moves punctuation inside quotes to outside when appropriate.
     *
     * @param text The text to process
     * @return Processed text with quotes handled
     */
    private String handleQuotes(String text) {
        String before = text;
        String after = text.replaceAll("([.!?])\"", "\"$1");
        if (!before.equals(after)) {
            log.debug("Handled quotes in text");
        }
        return after;
    }

    /**
     * Determines the type of a sentence based on its ending punctuation.
     *
     * @param sentence The sentence text
     * @return The sentence type
     */
    private Sentence.SentenceType determineSentenceType(String sentence) {
        if (sentence.endsWith("?")) {
            return Sentence.SentenceType.QUESTION;
        } else if (sentence.endsWith("!")) {
            return Sentence.SentenceType.EXCLAMATION;
        } else if (sentence.endsWith(".")) {
            return Sentence.SentenceType.STATEMENT;
        } else {
            return Sentence.SentenceType.INCOMPLETE;
        }
    }

    /**
     * Finds the approximate position of a sentence in text when exact matching fails.
     * This is useful for handling cases where the sentence has been modified during processing.
     *
     * @param text The original text
     * @param sentence The sentence to find
     * @param startFrom The position to start searching from
     * @return The approximate start index of the sentence
     */
    private int findApproximatePosition(String text, String sentence, int startFrom) {
        // If sentence is empty, return the current position
        if (sentence.isEmpty()) {
            return startFrom;
        }

        // Try to find the first few characters of the sentence
        String prefix = sentence.substring(0, Math.min(10, sentence.length()));
        int prefixPos = text.indexOf(prefix, startFrom);

        if (prefixPos >= 0) {
            return prefixPos;
        }

        // If all else fails, just return the current position
        return startFrom;
    }
}
