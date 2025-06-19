/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering.sentence;

import java.util.List;

/**
 * Interface for sentence segmentation functionality.
 * Implementations should handle various text segmentation scenarios and edge cases.
 */
public interface SentenceSegmenter {
    /**
     * Segments text into sentences.
     *
     * @param text The input text to segment
     * @return List of Sentence objects containing text and metadata
     */
    List<Sentence> segment(String text);

    /**
     * Segments text into sentences with custom configuration.
     *
     * @param text The input text to segment
     * @param config Configuration for sentence segmentation
     * @return List of Sentence objects containing text and metadata
     */
    List<Sentence> segment(String text, SentenceSegmentationConfig config);
}
