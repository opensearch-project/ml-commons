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

import java.util.List;
import java.util.Objects;

import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;

import org.opensearch.common.Strings;
import org.opensearch.ml.common.dataframe.DataFrame;

import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MachineLearningNodeClient implements MachineLearningClient {

    NodeClient client;

    @Override
    public void predict(String algorithm, List<MLParameter> parameters, MLInputDataset inputData, String modelId,
                        ActionListener<DataFrame> listener) {
        if(Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("algorithm name can't be null or empty");
        }
        if(Objects.isNull(inputData)) {
            throw new IllegalArgumentException("input data set can't be null");
        }

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder()
            .algorithm(algorithm)
            .modelId(modelId)
            .parameters(parameters)
            .inputDataset(inputData)
            .build();

        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            MLPredictionTaskResponse mlPredictionTaskResponse =
                    MLPredictionTaskResponse
                            .fromActionResponse(response);
            listener.onResponse(mlPredictionTaskResponse.getPredictionResult());
        }, listener::onFailure));

    }

    @Override
    public void train(String algorithm, List<MLParameter> parameters, MLInputDataset inputData, ActionListener<String> listener) {
        if(Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("algorithm name can't be null or empty");
        }
        if(Objects.isNull(inputData)) {
            throw new IllegalArgumentException("input data set can't be null");
        }

        MLTrainingTaskRequest trainingTaskRequest = MLTrainingTaskRequest.builder()
                .algorithm(algorithm)
                .inputDataset(inputData)
                .parameters(parameters)
                .build();

        client.execute(MLTrainingTaskAction.INSTANCE, trainingTaskRequest, ActionListener.wrap(response -> {
            listener.onResponse(MLTrainingTaskResponse.fromActionResponse(response).getTaskId());
        }, listener::onFailure));
    }

}
