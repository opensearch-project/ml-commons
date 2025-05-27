/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering.sentence;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a sentence with its metadata.
 */
@Getter
@Builder
@ToString
public class Sentence {
    private final String text;
    private final int startIndex;
    private final int endIndex;
    private final int position;
    private final SentenceType type;

    /**
     * Enum representing different types of sentences.
     */
    public enum SentenceType {
        STATEMENT,
        QUESTION,
        EXCLAMATION,
        INCOMPLETE
    }

    /**
     * Creates a new Sentence instance.
     *
     * @param text The sentence text
     * @param startIndex Start index in original text
     * @param endIndex End index in original text
     * @param position Position in the sequence of sentences
     * @param type Type of sentence
     */
    public Sentence(String text, int startIndex, int endIndex, int position, SentenceType type) {
        if (text == null) {
            throw new IllegalArgumentException("Sentence text cannot be null");
        }
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("Invalid sentence indices");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Invalid sentence position");
        }

        this.text = text;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.position = position;
        this.type = type != null ? type : SentenceType.STATEMENT;
    }

    /**
     * Gets the length of the sentence.
     *
     * @return The length of the sentence text
     */
    public int length() {
        return text.length();
    }

    /**
     * Checks if the sentence is empty or consists only of whitespace.
     *
     * @return true if the sentence is empty or whitespace-only
     */
    public boolean isEmpty() {
        return text.trim().isEmpty();
    }
}
