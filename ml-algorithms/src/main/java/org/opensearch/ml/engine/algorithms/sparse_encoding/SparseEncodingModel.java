/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sparse_encoding;

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
import org.opensearch.ml.engine.algorithms.sparse_encoding.SparseEncodingTranslator;
import org.opensearch.ml.engine.annotation.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Function(FunctionName.SPARSE_ENCODING)
public class SparseEncodingModel extends DLModel {

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
        return new SparseEncodingTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }

    @Override
    public Map<String, Object> getArguments(MLModelConfig modelConfig) {
        Map<String, Object> arguments = new HashMap<>();
        if (modelConfig == null){
            return arguments;
        }
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        Integer modelMaxLength = textEmbeddingModelConfig.getModelMaxLength();
        if (modelMaxLength != null) {
            arguments.put("modelMaxLength", modelMaxLength);
        }
        return arguments;
    }

    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        String warmUpSentence = "warm up sentence";
        if (modelConfig  != null) {
            Integer modelMaxLength = textEmbeddingModelConfig.getModelMaxLength();
            if (modelMaxLength != null) {
                warmUpSentence = "sentence ".repeat(modelMaxLength);
            }
        }
        // First request takes longer time. Predict once to warm up model.
        Input input = new Input();
        input.add(warmUpSentence);
        predictor.predict(input);
    }

}
