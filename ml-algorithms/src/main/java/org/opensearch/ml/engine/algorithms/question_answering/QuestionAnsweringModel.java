/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.ModelHelper.*;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.model.ModelResultFilter;
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
@Function(FunctionName.QUESTION_ANSWERING)
public class QuestionAnsweringModel extends DLModel {

    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        String question = "How is the weather?";
        String context = "The weather is nice, it is beautiful day.";
        Input input = new Input();
        input.add(question);
        input.add(context);

        // First request takes longer time. Predict once to warm up model.
        predictor.predict(input);
    }

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Output output;
        TextDocsInputDataSet textDocsInput = (TextDocsInputDataSet) inputDataSet;
        ModelResultFilter resultFilter = textDocsInput.getResultFilter();
        String question = textDocsInput.getDocs().get(0);
        String context = textDocsInput.getDocs().get(1);
        Input input = new Input();
        input.add(question);
        input.add(context);
        output = getPredictor().predict(input);
        tensorOutputs.add(parseModelTensorOutput(output, resultFilter));
        return new ModelTensorOutput(tensorOutputs);
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) throws IllegalArgumentException {
        return new QuestionAnsweringTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }

}
