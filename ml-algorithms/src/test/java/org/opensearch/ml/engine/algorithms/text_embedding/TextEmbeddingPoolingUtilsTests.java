/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.junit.Assert.assertArrayEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;

public class TextEmbeddingPoolingUtilsTests {

    private static final float DELTA = 1e-6f;

    private NDManager manager;

    @Before
    public void setUp() {
        manager = NDManager.newBaseManager();
    }

    @After
    public void tearDown() {
        manager.close();
    }

    @Test
    public void lastTokenPool_rightPadded_picksLastRealToken() {
        NDArray embeddings = manager.create(embeddingRows(5, 2), new Shape(5, 2));
        NDArray mask = manager.create(new long[] { 1, 1, 1, 0, 0 });

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 3.0f, 3.0f }, result, DELTA);
    }

    @Test
    public void lastTokenPool_leftPadded_picksLastRealToken() {
        NDArray embeddings = manager.create(embeddingRows(5, 2), new Shape(5, 2));
        NDArray mask = manager.create(new long[] { 0, 0, 1, 1, 1 });

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 5.0f, 5.0f }, result, DELTA);
    }

    @Test
    public void lastTokenPool_noPadding_picksFinalToken() {
        NDArray embeddings = manager.create(embeddingRows(5, 2), new Shape(5, 2));
        NDArray mask = manager.create(new long[] { 1, 1, 1, 1, 1 });

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 5.0f, 5.0f }, result, DELTA);
    }

    @Test
    public void lastTokenPool_allPadding_fallsBackToFirstIndex() {
        NDArray embeddings = manager.create(embeddingRows(5, 2), new Shape(5, 2));
        NDArray mask = manager.create(new long[] { 0, 0, 0, 0, 0 });

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 1.0f, 1.0f }, result, DELTA);
    }

    @Test
    public void lastTokenPool_acceptsFloatMask() {
        // HuggingfaceTextEmbeddingTranslator converts the mask to FLOAT32 before calling this helper.
        // Guards against regressing into toLongArray() on a non-INT64 NDArray.
        NDArray embeddings = manager.create(embeddingRows(4, 2), new Shape(4, 2));
        NDArray mask = manager.create(new long[] { 0, 1, 1, 0 }).toType(DataType.FLOAT32, false);

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 3.0f, 3.0f }, result, DELTA);
    }

    @Test
    public void lastTokenPool_singleRealToken() {
        NDArray embeddings = manager.create(embeddingRows(1, 3), new Shape(1, 3));
        NDArray mask = manager.create(new long[] { 1 });

        float[] result = TextEmbeddingPoolingUtils.lastTokenPool(embeddings, mask).toFloatArray();

        assertArrayEquals(new float[] { 1.0f, 1.0f, 1.0f }, result, DELTA);
    }

    /** Row i (1-indexed) is filled with the value i, so the returned embedding identifies its position. */
    private static float[] embeddingRows(int rows, int hidden) {
        float[] data = new float[rows * hidden];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < hidden; j++) {
                data[i * hidden + j] = i + 1;
            }
        }
        return data;
    }
}
