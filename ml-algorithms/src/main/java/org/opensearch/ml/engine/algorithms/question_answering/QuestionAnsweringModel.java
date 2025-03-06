/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.question_answering;

import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_CONTEXT;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.DEFAULT_WARMUP_QUESTION;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.SENTENCE_HIGHLIGHTING_TYPE;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
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

/**
 * Question answering model implementation that supports both standard QA and
 * highlighting sentence.
 */
@Log4j2
@Function(FunctionName.QUESTION_ANSWERING)
public class QuestionAnsweringModel extends DLModel {

    @Override
    public void warmUp(Predictor predictor, String modelId, MLModelConfig modelConfig) throws TranslateException {
        if (predictor == null) {
            throw new IllegalArgumentException("predictor is null");
        }
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }

        // Create input for the predictor
        Input input = new Input();
        input.add(DEFAULT_WARMUP_QUESTION);
        input.add(DEFAULT_WARMUP_CONTEXT);

        // Run prediction to warm up the model
        predictor.predict(input);
    }

    /**
     * Checks if the model is configured for sentence highlighting.
     *
     * @param modelConfig The model configuration
     * @return true if the model is configured for sentence highlighting, false otherwise
     */
    private boolean isSentenceHighlightingType(MLModelConfig modelConfig) {
        if (modelConfig != null) {
            return SENTENCE_HIGHLIGHTING_TYPE.equalsIgnoreCase(modelConfig.getModelType());
        }
        return false;
    }

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Output output;
        QuestionAnsweringInputDataSet qaInputDataSet = (QuestionAnsweringInputDataSet) inputDataSet;
        String question = qaInputDataSet.getQuestion();
        String context = qaInputDataSet.getContext();
        Input input = new Input();
        input.add(question);
        input.add(context);
        output = getPredictor().predict(input);
        tensorOutputs.add(parseModelTensorOutput(output, null));
        return new ModelTensorOutput(tensorOutputs);
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) throws IllegalArgumentException {
        if (isSentenceHighlightingType(modelConfig)) {
            return SentenceHighlightingQATranslator.createDefault();
        }
        return new QuestionAnsweringTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }
}
