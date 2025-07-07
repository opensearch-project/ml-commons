package org.opensearch.ml.engine.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;

public abstract class TextEmbeddingModel extends DLModel {
    protected boolean isSparseModel = false;

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLAlgoParams mlParams = mlInput.getParameters();

        MLInputDataset inputDataSet = isAsymmetricModel(mlParams)
            ? addPrefixesToData((AsymmetricTextEmbeddingParameters) mlParams, (TextDocsInputDataSet) mlInput.getInputDataset())
            : mlInput.getInputDataset();

        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Output output;
        TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
        ModelResultFilter resultFilter = textDocsInput.getResultFilter();
        for (String doc : textDocsInput.getDocs()) {
            Input input = new Input();
            input.add(doc);
            if (mlParams instanceof AsymmetricTextEmbeddingParameters) {
                AsymmetricTextEmbeddingParameters params = (AsymmetricTextEmbeddingParameters) mlParams;
                input.add(AsymmetricTextEmbeddingParameters.SPARSE_EMBEDDING_FORMAT_FIELD, params.getSparseEmbeddingFormat().name());
            }

            output = getPredictor().predict(input);
            tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    protected boolean isAsymmetricModel(MLAlgoParams mlParams) {
        if (mlParams instanceof AsymmetricTextEmbeddingParameters) {
            // Check for the necessary prefixes in modelConfig
            if (modelConfig == null
                || ((TextEmbeddingModelConfig) modelConfig).getPassagePrefix() == null
                    && ((TextEmbeddingModelConfig) modelConfig).getQueryPrefix() == null) {
                throw new IllegalArgumentException(
                    "When passing AsymmetricTextEmbeddingParameters, the model requires to be "
                        + "registered with at least one of `query_prefix` or `passage_prefix`."
                );
            }
            // Passed all checks
            return true;
        }

        // no AsymmetricTextEmbeddingParameters passed, but the model is asymmetric.
        if (modelConfig != null
            && (((TextEmbeddingModelConfig) modelConfig).getPassagePrefix() != null
                || ((TextEmbeddingModelConfig) modelConfig).getQueryPrefix() != null)) {
            throw new IllegalArgumentException(
                "The embedding model chosen is asymmetric. To use it, you must declare whether the input is of type `QUERY` or of type `PASSAGE`."
            );
        }

        return false;
    }

    private TextDocsInputDataSet addPrefixesToData(AsymmetricTextEmbeddingParameters mlParams, TextDocsInputDataSet inputDataSet) {
        // Asymmetric embedding models typically work with "mini-prompts" that prime the model to embed a text
        // as a query or as a passage. Here we apply the prompt as defined in the model configuration. We default
        // to query embedding.
        TextEmbeddingModelConfig modelConfig = (TextEmbeddingModelConfig) this.modelConfig;
        String prefix = mlParams.getEmbeddingContentType() == EmbeddingContentType.PASSAGE
            ? modelConfig.getPassagePrefix()
            : modelConfig.getQueryPrefix();
        if (prefix != null) {
            List<String> prefixedDocs = inputDataSet.getDocs().stream().map(s -> prefix + s).collect(Collectors.toList());
            return TextDocsInputDataSet.builder().docs(prefixedDocs).resultFilter(inputDataSet.getResultFilter()).build();
        }
        return inputDataSet;
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
