/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.ndarray.NDArray;

/**
 * Shared pooling utility methods for text embedding translators.
 */
final class TextEmbeddingPoolingUtils {

    private TextEmbeddingPoolingUtils() {}

    /**
     * Extracts the embedding of the last non-padding token. Used for decoder-only models
     * where the final token captures cumulative context through causal attention.
     *
     * @param embeddings the token embeddings (sequence_length x hidden_size)
     * @param attentionMask the attention mask (sequence_length), 1 for real tokens, 0 for padding
     * @return the embedding at the last non-padding token position
     */
    static NDArray lastTokenPool(NDArray embeddings, NDArray attentionMask) {
        long tokenCount = attentionMask.sum().toLongArray()[0];
        long lastTokenIdx = Math.max(tokenCount - 1, 0);
        return embeddings.get(lastTokenIdx);
    }
}
