/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.ModelHelper.ONNX_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.engine.algorithms.TextEmbeddingModel;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Function(FunctionName.TEXT_EMBEDDING)
public class TextEmbeddingDenseModel extends TextEmbeddingModel {

    public static final String SENTENCE_EMBEDDING = "sentence_embedding";

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        TextEmbeddingModelConfig.FrameworkType transformersType = textEmbeddingModelConfig.getFrameworkType();
        String modelType = textEmbeddingModelConfig.getModelType();
        TextEmbeddingModelConfig.PoolingMode poolingMode = textEmbeddingModelConfig.getPoolingMode();
        boolean normalizeResult = textEmbeddingModelConfig.isNormalizeResult();

        if (ONNX_ENGINE.equals(engine)) { // ONNX
            return new ONNXSentenceTransformerTextEmbeddingTranslator(poolingMode, normalizeResult, modelType);
        } else if (transformersType == SENTENCE_TRANSFORMERS) {// pytorch sentence_transformer
            return new SentenceTransformerTextEmbeddingTranslator();
        }
        return null;
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        TextEmbeddingModelConfig.FrameworkType transformersType = textEmbeddingModelConfig.getFrameworkType();
        String modelType = textEmbeddingModelConfig.getModelType();
        TextEmbeddingModelConfig.PoolingMode poolingMode = textEmbeddingModelConfig.getPoolingMode();
        boolean normalizeResult = textEmbeddingModelConfig.isNormalizeResult();

        if (PYTORCH_ENGINE.equals(engine) && transformersType != SENTENCE_TRANSFORMERS) { // pytorch
            boolean neuron = false;
            if (transformersType.name().endsWith("_NEURON")) {
                neuron = true;
            }
            return new HuggingfaceTextEmbeddingTranslatorFactory(poolingMode, normalizeResult, modelType, neuron);
        }
        return null;
    }

}
