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
        return embeddings.get(findLastRealTokenIndex(attentionMask));
    }

    /**
     * Scans the attention mask from the end for the first non-zero entry. Done in Java
     * rather than via NDArray arithmetic because the DJL ONNX runtime engine does not
     * implement {@code arange}, {@code mul}, or {@code max}. Returns 0 as a safe fallback
     * when the mask is all padding.
     */
    private static long findLastRealTokenIndex(NDArray attentionMask) {
        if (attentionMask.getDataType() == DataType.FLOAT32) {
            float[] mask = attentionMask.toFloatArray();
            for (int i = mask.length - 1; i >= 0; i--) {
                if (mask[i] != 0f) {
                    return i;
                }
            }
        } else {
            long[] mask = attentionMask.toLongArray();
            for (int i = mask.length - 1; i >= 0; i--) {
                if (mask[i] != 0L) {
                    return i;
                }
            }
        }
        return 0L;
    }
}
