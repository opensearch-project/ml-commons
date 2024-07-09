/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.image_embedding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.ImageEmbeddingInputDataSet;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Function(FunctionName.IMAGE_EMBEDDING)
public class ImageEmbeddingModel extends DLModel {
    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        Input input = new Input();

        try {
            String base64Image = new String(
                Base64.getEncoder().encode(Files.readAllBytes(Path.of(getClass().getResource("opensearch_logo.jpg").toURI())))
            );
            input.add(base64Image);
        } catch (Throwable e) {
            throw new MLException("Failed while encoding image when warming up image embedding model", e);
        }

        // First request takes longer time. Predict once to warm up model.
        predictor.predict(input);
    }

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Output output;
        ImageEmbeddingInputDataSet qaInputDataSet = (ImageEmbeddingInputDataSet) inputDataSet;
        String base64Image = qaInputDataSet.getBase64Image();
        Input input = new Input();
        input.add(base64Image);
        output = getPredictor().predict(input);
        tensorOutputs.add(parseModelTensorOutput(output, null));
        return new ModelTensorOutput(tensorOutputs);
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) throws IllegalArgumentException {
        return new ImageEmbeddingTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }
}
