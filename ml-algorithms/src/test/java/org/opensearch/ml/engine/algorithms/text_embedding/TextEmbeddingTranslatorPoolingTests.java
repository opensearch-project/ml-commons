/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslatorContext;

public class TextEmbeddingTranslatorPoolingTests {

    private static final float DELTA = 1e-6f;

    // Three tokens with hidden size 2; the last token is padding and must be ignored by max pooling.
    private static final float[] TOKEN_EMBEDDINGS = { 3f, -5f, 1f, -4f, 100f, 100f };
    private static final long[] ATTENTION_MASK = { 1, 1, 0 };

    private NDManager manager;
    private TranslatorContext ctx;

    @Before
    public void setUp() {
        manager = NDManager.newBaseManager();
        Encoding encoding = mock(Encoding.class);
        when(encoding.getAttentionMask()).thenReturn(ATTENTION_MASK);
        ctx = mock(TranslatorContext.class);
        when(ctx.getAttachment("encoding")).thenReturn(encoding);
        when(ctx.getNDManager()).thenReturn(manager);
    }

    @After
    public void tearDown() {
        manager.close();
    }

    @Test
    public void onnxMaxPool_withNormalize_returnsL2NormalizedVector() {
        ModelTensor tensor = onnxEmbedding(true);

        assertArrayEquals(new long[] { 2 }, tensor.getShape());
        assertArrayEquals(new float[] { 0.6f, -0.8f }, toFloats(tensor.getData()), DELTA);
    }

    @Test
    public void onnxMaxPool_withoutNormalize_returnsMaxOfRealTokens() {
        ModelTensor tensor = onnxEmbedding(false);

        assertArrayEquals(new long[] { 2 }, tensor.getShape());
        assertArrayEquals(new float[] { 3f, -4f }, toFloats(tensor.getData()), DELTA);
    }

    @Test
    public void huggingfaceMaxPool_withNormalize_returnsL2NormalizedVector() {
        HuggingfaceTextEmbeddingTranslator translator = new HuggingfaceTextEmbeddingTranslator(null, null, "max", true, false);

        float[] result = translator.processOutput(ctx, new NDList(tokenEmbeddings()));

        assertArrayEquals(new float[] { 0.6f, -0.8f }, result, DELTA);
    }

    @Test
    public void huggingfaceMaxPool_withoutNormalize_returnsMaxOfRealTokens() {
        HuggingfaceTextEmbeddingTranslator translator = new HuggingfaceTextEmbeddingTranslator(null, null, "max", false, false);

        float[] result = translator.processOutput(ctx, new NDList(tokenEmbeddings()));

        assertArrayEquals(new float[] { 3f, -4f }, result, DELTA);
    }

    private ModelTensor onnxEmbedding(boolean normalize) {
        ONNXSentenceTransformerTextEmbeddingTranslator translator = new ONNXSentenceTransformerTextEmbeddingTranslator(
            TextEmbeddingModelConfig.PoolingMode.MAX,
            normalize,
            "bert"
        );
        Output output = translator.processOutput(ctx, new NDList(tokenEmbeddings()));
        return ModelTensors.fromBytes(output.getData().getAsBytes()).getMlModelTensors().get(0);
    }

    private NDArray tokenEmbeddings() {
        return manager.create(TOKEN_EMBEDDINGS, new Shape(3, 2));
    }

    private static float[] toFloats(Number[] data) {
        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i].floatValue();
        }
        return result;
    }
}
