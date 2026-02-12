/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.engine.algorithms.text_similarity;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.engine.algorithms.DLModel;
import org.opensearch.ml.engine.annotation.Function;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;

@Function(FunctionName.TEXT_SIMILARITY)
public class TextSimilarityCrossEncoderModel extends DLModel {

    @Override
    public ModelTensorOutput predict(String modelId, MLInput mlInput) throws TranslateException {
        MLInputDataset inputDataSet = mlInput.getInputDataset();
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        TextSimilarityInputDataSet textSimInput = (TextSimilarityInputDataSet) inputDataSet;
        String queryText = textSimInput.getQueryText();
        List<String> textDocs = textSimInput.getTextDocs();

        Settings clusterSettings = getClusterSettings();
        final int batchSize = MLCommonsSettings.ML_COMMONS_TEXT_SIMILARITY_BATCH_SIZE.get(clusterSettings);

        for (int i = 0; i < textDocs.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, textDocs.size());
            List<String> batchDocs = textDocs.subList(i, endIndex);
            List<Input> batchInputs = new ArrayList<>(batchDocs.size());

            for (String doc : batchDocs) {
                Input input = new Input();
                input.add(queryText);
                input.add(doc);
                batchInputs.add(input);
            }

            List<Output> batchOutputs = getPredictor().batchPredict(batchInputs);

            for (Output output : batchOutputs) {
                ModelTensors outputTensors = ModelTensors.fromBytes(output.getData().getAsBytes());
                tensorOutputs.add(outputTensors);
            }
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    @Override
    public Translator<Input, Output> getTranslator(String engine, MLModelConfig modelConfig) throws IllegalArgumentException {
        return new TextSimilarityTranslator();
    }

    @Override
    public TranslatorFactory getTranslatorFactory(String engine, MLModelConfig modelConfig) {
        return null;
    }
}
