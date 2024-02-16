package org.opensearch.ml.engine.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.text_embedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.engine.algorithms.text_embedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;

public abstract class TextEmbeddingModel extends DLModel {

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        MLAlgoParams mlParams = mlInput.getParameters();
        if (mlParams != null && mlParams instanceof AsymmetricTextEmbeddingParameters) {
            addPrefixesToData((AsymmetricTextEmbeddingParameters) mlParams, (TextDocsInputDataSet) inputDataSet);
        } else if (((TextEmbeddingModelConfig) modelConfig).getPassagePrefix() != null) {
            throw new IllegalArgumentException(
                "The embedding model chosen is asymmetric. To use it, you must declare whether the input is a query or a passage."
            );
        }
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

    private void addPrefixesToData(AsymmetricTextEmbeddingParameters mlParams, TextDocsInputDataSet inputDataSet) {
        // Asymmetric embedding models typically work with "mini-prompts" that prime the model to embed a text
        // as a query or as a passage. Here we apply the prompt as defined in the model configuration. We default
        // to passage embedding.
        TextEmbeddingModelConfig modelConfig = (TextEmbeddingModelConfig) this.modelConfig;
        String prefix = mlParams.getEmbeddingContentType() == EmbeddingContentType.QUERY
            ? modelConfig.getQueryPrefix()
            : modelConfig.getPassagePrefix();
        if (prefix != null) {
            inputDataSet.getDocs().replaceAll(doc -> prefix + doc);
        }
    }

    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        String warmUpSentence = "warm up sentence";
        if (modelConfig != null) {
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

    public Map<String, Object> getArguments(MLModelConfig modelConfig) {
        Map<String, Object> arguments = new HashMap<>();
        if (modelConfig == null) {
            return arguments;
        }
        TextEmbeddingModelConfig textEmbeddingModelConfig = (TextEmbeddingModelConfig) modelConfig;
        Integer modelMaxLength = textEmbeddingModelConfig.getModelMaxLength();

        if (modelMaxLength != null) {
            arguments.put("modelMaxLength", modelMaxLength);
        }
        return arguments;
    }

}
