/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MachineLearningNodeClient implements MachineLearningClient {

    NodeClient client;

    @Override
    public void predict(String modelId, MLInput mlInput,
                        ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder()
            .mlInput(mlInput)
            .build();

        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            MLPredictionTaskResponse predictionResponse =
                    MLPredictionTaskResponse
                            .fromActionResponse(response);
            listener.onResponse(predictionResponse.getOutput());
        }, listener::onFailure));

    }

    @Override
    public void train(MLInput mlInput, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);
        MLTrainingTaskRequest trainingTaskRequest = MLTrainingTaskRequest.builder()
                .mlInput(mlInput)
                .build();

        client.execute(MLTrainingTaskAction.INSTANCE, trainingTaskRequest, ActionListener.wrap(response -> {
            listener.onResponse(MLTrainingTaskResponse.fromActionResponse(response).getOutput());
        }, listener::onFailure));
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener) {
        MLExecuteTaskRequest executeTaskRequest = MLExecuteTaskRequest.builder()
                .input(input)
                .build();

        client.execute(MLExecuteTaskAction.INSTANCE, executeTaskRequest, ActionListener.wrap(response -> {
            listener.onResponse(MLExecuteTaskResponse.fromActionResponse(response).getOutput());
        }, listener::onFailure));
    }

    private void validateMLInput(MLInput mlInput, boolean requireInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("ML Input can't be null");
        }
        if(mlInput.getAlgorithm() == null) {
            throw new IllegalArgumentException("algorithm name can't be null or empty");
        }
        if(requireInput && mlInput.getInputDataset() == null) {
            throw new IllegalArgumentException("input data set can't be null");
        }
    }

}
