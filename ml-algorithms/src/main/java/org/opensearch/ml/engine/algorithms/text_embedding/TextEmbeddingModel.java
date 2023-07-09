/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.annotation.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS;
import static org.opensearch.ml.engine.ModelHelper.ONNX_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;

@Log4j2
@Function(FunctionName.TEXT_EMBEDDING)
public class TextEmbeddingModel extends DLModel {

    public static final String SENTENCE_EMBEDDING = "sentence_embedding";

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Output output;
        TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
        ModelResultFilter resultFilter = textDocsInput.getResultFilter();
        for (String doc : textDocsInput.getDocs()) {
            Input input = new Input();
            input.add(doc);
            output = getPredictor().predict(input);
            tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        TextEmbeddingModelConfig.FrameworkType transformersType = textEmbeddingModelConfig.getFrameworkType();
        String modelType = textEmbeddingModelConfig.getModelType();
        TextEmbeddingModelConfig.PoolingMode poolingMode = textEmbeddingModelConfig.getPoolingMode();
        boolean normalizeResult = textEmbeddingModelConfig.isNormalizeResult();

        if (ONNX_ENGINE.equals(engine)) { //ONNX
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

        if (PYTORCH_ENGINE.equals(engine) && transformersType != SENTENCE_TRANSFORMERS) { //pytorch
            boolean neuron = false;
            if (transformersType.name().endsWith("_NEURON")) {
                neuron = true;
            }
            return new HuggingfaceTextEmbeddingTranslatorFactory(poolingMode, normalizeResult, modelType, neuron);
        }
        return null;
    }

    @Override
    public Map<String, Object> getArguments(MLModelConfig modelConfig) {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        Integer modelMaxLength = textEmbeddingModelConfig.getModelMaxLength();
        Map<String, Object> arguments = new HashMap<>();
        if (modelMaxLength != null) {
            arguments.put("modelMaxLength", modelMaxLength);
        }
        return arguments;
    }

    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        Integer modelMaxLength = textEmbeddingModelConfig.getModelMaxLength();
        String warmUpSentence = "warm up sentence";
        if (modelMaxLength != null) {
            warmUpSentence = "sentence ".repeat(modelMaxLength);
        }
        // First request takes longer time. Predict once to warm up model.
        Input input = new Input();
        input.add(warmUpSentence);
        predictor.predict(input);
    }

}
