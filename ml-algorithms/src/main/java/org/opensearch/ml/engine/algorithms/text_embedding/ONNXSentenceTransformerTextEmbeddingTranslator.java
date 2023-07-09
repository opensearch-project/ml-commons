/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.ServingTranslator;
import ai.djl.translate.TranslatorContext;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingModel.SENTENCE_EMBEDDING;

public class ONNXSentenceTransformerTextEmbeddingTranslator implements ServingTranslator {
    private static final int[] AXIS = {0};
    private HuggingFaceTokenizer tokenizer;
    private TextEmbeddingModelConfig.PoolingMode poolingMode;
    private boolean normalizeResult;
    private String modelType;

    public ONNXSentenceTransformerTextEmbeddingTranslator(TextEmbeddingModelConfig.PoolingMode poolingMode, boolean normalizeResult, String modelType) {
        this.poolingMode = poolingMode == null ? TextEmbeddingModelConfig.PoolingMode.MEAN : poolingMode;
        this.normalizeResult = normalizeResult;
        this.modelType = modelType;
    }

    @Override
    public Batchifier getBatchifier() {
        return null;

    }
    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        Path path = ctx.getModel().getModelPath();
        tokenizer = HuggingFaceTokenizer.builder().optPadding(true).optTokenizerPath(path.resolve("tokenizer.json")).build();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Input input) {
        NDManager manager = ctx.getNDManager();
        String sentence = input.getAsString(0);
        NDList ndList = new NDList();

        Encoding encode = tokenizer.encode(sentence);
        ctx.setAttachment("encoding", encode);
        long[] indices = encode.getIds();
        long[] attentionMask = encode.getAttentionMask();

        NDArray indicesArray = manager.create(indices).expandDims(0);
        indicesArray.setName("input_ids");
        NDArray attentionMaskArray = manager.create(attentionMask).expandDims(0);
        attentionMaskArray.setName("attention_mask");
        ndList.add(indicesArray);
        ndList.add(attentionMaskArray);
        if ("bert".equalsIgnoreCase(modelType) || "albert".equalsIgnoreCase(modelType)) {
            long[] tokenTypeIds = encode.getTypeIds();
            NDArray tokenTypeIdsArray = manager.create(tokenTypeIds).expandDims(0);
            tokenTypeIdsArray.setName("token_type_ids");
            ndList.add(tokenTypeIdsArray);
        }
        return ndList;
    }

    /** {@inheritDoc} */
    @Override
    public Output processOutput(TranslatorContext ctx, NDList list) {
        NDArray embeddings = list.get(0);
        int shapeLength = embeddings.getShape().getShape().length;
        if (shapeLength == 3) {
            embeddings = embeddings.get(0);
        }
        Encoding encoding = (Encoding) ctx.getAttachment("encoding");
        long[] attentionMask = encoding.getAttentionMask();
        NDManager manager = ctx.getNDManager();
        NDArray inputAttentionMask = manager.create(attentionMask);
        switch (this.poolingMode) {
            case MEAN:
                embeddings = meanPool(embeddings, inputAttentionMask, false);
                break;
            case MEAN_SQRT_LEN:
                embeddings = meanPool(embeddings, inputAttentionMask, true);
                break;
            case MAX:
                embeddings = maxPool(embeddings, inputAttentionMask);
                break;
            case WEIGHTED_MEAN:
                embeddings = weightedMeanPool(embeddings, inputAttentionMask);
                break;
            case CLS:
                embeddings = embeddings.get(0);
                break;
            default:
                throw new IllegalArgumentException("Unsupported pooling method");
        }

        if (normalizeResult) {
            embeddings = embeddings.normalize(2, 0);
        }

        Number[] data = embeddings.toArray();
        List<ModelTensor> outputs = new ArrayList<>();
        long[] shape = embeddings.getShape().getShape();
        ModelTensor modelTensor = ModelTensor.builder()
                .name(SENTENCE_EMBEDDING)
                .data(data)
                .shape(shape)
                .dataType(MLResultDataType.FLOAT32)
                .build();
        outputs.add(modelTensor);

        Output output = new Output();
        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    private NDArray meanPool(NDArray embeddings, NDArray inputAttentionMask, boolean sqrt) {
        long[] shape = embeddings.getShape().getShape();
        inputAttentionMask = inputAttentionMask.expandDims(-1).broadcast(shape);
        NDArray inputAttentionMaskSum = inputAttentionMask.sum(AXIS);
        NDArray clamp = inputAttentionMaskSum.clip(1e-9, 1e12);
        NDArray prod = embeddings.mul(inputAttentionMask);
        NDArray sum = prod.sum(AXIS);
        if (sqrt) {
            return sum.div(clamp.sqrt());
        }
        return sum.div(clamp);
    }

    private NDArray maxPool(NDArray embeddings, NDArray inputAttentionMask) {
        long[] shape = embeddings.getShape().getShape();
        inputAttentionMask = inputAttentionMask.expandDims(-1).broadcast(shape);
        inputAttentionMask = inputAttentionMask.eq(0);
        embeddings = embeddings.duplicate();
        embeddings.set(inputAttentionMask, -1e9); // Set padding tokens to large negative value

        return embeddings.max(AXIS, true);
    }

    private NDArray weightedMeanPool(NDArray embeddings, NDArray attentionMask) {
        long[] shape = embeddings.getShape().getShape();
        NDArray weight = embeddings.getManager().arange(1, shape[0] + 1);
        weight = weight.expandDims(-1).broadcast(shape);

        attentionMask = attentionMask.expandDims(-1).broadcast(shape).mul(weight);
        NDArray maskSum = attentionMask.sum(AXIS);
        NDArray embeddingSum = embeddings.mul(attentionMask).sum(AXIS);
        return embeddingSum.div(maskSum);
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
