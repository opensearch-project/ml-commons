/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

/**
 * Constants for Question Answering models and related functionality.
 */
public final class QAConstants {
    // Model types
    public static final String SENTENCE_HIGHLIGHTING_TYPE = "sentence_highlighting";

    // Output field names
    public static final String FIELD_HIGHLIGHTS = "highlights";
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_POSITION = "position";
    public static final String FIELD_START = "start";
    public static final String FIELD_END = "end";

    // Context keys
    public static final String KEY_SENTENCES = "sentences";

    // Model input names
    public static final String INPUT_IDS = "input_ids";
    public static final String ATTENTION_MASK = "attention_mask";
    public static final String TOKEN_TYPE_IDS = "token_type_ids";
    public static final String SENTENCE_IDS = "sentence_ids";

    // Default values for warm-up
    public static final String DEFAULT_WARMUP_QUESTION = "How is the weather?";
    public static final String DEFAULT_WARMUP_CONTEXT = "The weather is nice, it is beautiful day. The sun is shining. The sky is blue.";
}
