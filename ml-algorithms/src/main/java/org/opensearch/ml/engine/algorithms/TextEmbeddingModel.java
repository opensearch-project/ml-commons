package org.opensearch.ml.engine.algorithms;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;

import java.util.ArrayList;
import java.util.List;

public class TextEmbeddingModel extends DLModel {
    @Override
    public ModelTensorOutput innerPredict(MLInput mlInput) throws TranslateException {
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

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) {
        return null;
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }
}
