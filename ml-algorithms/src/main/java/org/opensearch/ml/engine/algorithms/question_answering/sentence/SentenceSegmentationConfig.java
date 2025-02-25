/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering.sentence;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for sentence segmentation.
 * This class provides various options to customize how text is segmented into sentences.
 */
@Getter
@Builder
public class SentenceSegmentationConfig {
    /**
     * Whether to preserve whitespace in sentences.
     * If false, sentences will be trimmed.
     */
    @Builder.Default
    private final boolean preserveWhitespace = false;

    /**
     * Whether to handle abbreviations to prevent false sentence boundaries.
     */
    @Builder.Default
    private final boolean handleAbbreviations = true;

    /**
     * Whether to handle quotes to ensure proper sentence boundaries.
     */
    @Builder.Default
    private final boolean handleQuotes = true;

    /**
     * Custom regex pattern for additional sentence delimiters.
     * This will be added to the default pattern.
     */
    @Builder.Default
    private final String customSentenceDelimiters = "";

    /**
     * List of common abbreviations to handle.
     * These are primarily English abbreviations. For other languages,
     * provide a custom list appropriate for that language.
     */
    @Builder.Default
    private final String[] commonAbbreviations = {
        "Mr.",
        "Mrs.",
        "Ms.",
        "Dr.",
        "Prof.",
        "Sr.",
        "Jr.",
        "Ltd.",
        "Co.",
        "Inc.",
        "i.e.",
        "e.g.",
        "etc.",
        "vs.",
        "fig." };

    /**
     * Creates a default configuration.
     * The default configuration preserves whitespace and uses English abbreviations.
     *
     * @return Default SentenceSegmentationConfig
     */
    public static SentenceSegmentationConfig getDefault() {
        return SentenceSegmentationConfig.builder().build();
    }
}
