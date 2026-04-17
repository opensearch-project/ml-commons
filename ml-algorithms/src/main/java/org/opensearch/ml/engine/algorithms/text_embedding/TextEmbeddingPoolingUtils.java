/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.types.DataType;

/**
 * Shared pooling utility methods for text embedding translators.
 */
final class TextEmbeddingPoolingUtils {

    private TextEmbeddingPoolingUtils() {}

    /**
     * Extracts the embedding of the last non-padding token. Used for decoder-only models
     * where the final token captures cumulative context through causal attention.
     *
     * Padding-side agnostic: works for right-padded ([real, real, pad, pad]), left-padded
     * ([pad, pad, real, real]), and unpadded inputs. Finds the largest index where the
     * attention mask is 1 rather than assuming real tokens occupy a prefix of the sequence.
     *
     * @param embeddings the token embeddings (sequence_length x hidden_size)
     * @param attentionMask the attention mask (sequence_length), 1 for real tokens, 0 for padding
     * @return the embedding at the last non-padding token position
     */
    static NDArray lastTokenPool(NDArray embeddings, NDArray attentionMask) {
        long seqLen = attentionMask.getShape().get(0);
        NDArray mask = attentionMask.toType(DataType.INT64, false);
        // positions[i] = i+1 for real tokens, 0 for padding; the max reveals the last real index
        NDArray indices = attentionMask.getManager().arange(1L, seqLen + 1L).toType(DataType.INT64, false);
        long lastTokenIdx = Math.max(indices.mul(mask).max().toLongArray()[0] - 1, 0L);
        return embeddings.get(lastTokenIdx);
    }
}
