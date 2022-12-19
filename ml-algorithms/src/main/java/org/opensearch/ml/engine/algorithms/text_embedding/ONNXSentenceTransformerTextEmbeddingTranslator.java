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
    private TextEmbeddingModelConfig.PoolingMethod poolingMethod;
    private boolean normalizeResult;
    private String modelType;

    public ONNXSentenceTransformerTextEmbeddingTranslator(TextEmbeddingModelConfig.PoolingMethod poolingMethod, boolean normalizeResult, String modelType) {
        this.poolingMethod = poolingMethod;
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
        NDArray embeddings = null;
        switch (this.poolingMethod) {
            case MEAN:
                embeddings = meanPooling(ctx, list);
                break;
            case CLS:
                embeddings = list.get(0).get(0).get(0);
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
        outputs.add(new ModelTensor(SENTENCE_EMBEDDING, data, shape, MLResultDataType.FLOAT32, null));

        Output output = new Output();
        ModelTensors modelTensorOutput = new ModelTensors(outputs);
        output.add(modelTensorOutput.toBytes());
        return output;
    }

    private static NDArray meanPooling(TranslatorContext ctx, NDList list) {
        NDArray embeddings = list.get(0);
        int shapeLength = embeddings.getShape().getShape().length;
        if (shapeLength == 3) {
            embeddings = embeddings.get(0);
        }
        Encoding encoding = (Encoding) ctx.getAttachment("encoding");
        long[] attentionMask = encoding.getAttentionMask();
        NDManager manager = ctx.getNDManager();
        NDArray inputAttentionMask = manager.create(attentionMask);
        long[] shape = embeddings.getShape().getShape();
        inputAttentionMask = inputAttentionMask.expandDims(-1).broadcast(shape);
        NDArray inputAttentionMaskSum = inputAttentionMask.sum(AXIS);
        NDArray clamp = inputAttentionMaskSum.clip(1e-9, 1e12);
        NDArray prod = embeddings.mul(inputAttentionMask);
        NDArray sum = prod.sum(AXIS);
        embeddings = sum.div(clamp);
        return embeddings;
    }

    @Override
    public void setArguments(Map<String, ?> arguments) {
    }
}
