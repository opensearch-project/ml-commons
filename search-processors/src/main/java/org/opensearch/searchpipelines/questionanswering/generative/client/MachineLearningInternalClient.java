/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.searchpipelines.questionanswering.generative.client;

import java.util.function.Function;

import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * An internal facing ML client adapted from org.opensearch.ml.client.MachineLearningNodeClient.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Log4j2
public class MachineLearningInternalClient {

    Client client;

    public ActionFuture<MLOutput> predict(String modelId, MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        predict(modelId, mlInput, actionFuture);
        return actionFuture;
    }

    // VisibleForTesting
    void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
            .builder()
            .mlInput(mlInput)
            .modelId(modelId)
            .dispatchTask(true)
            .build();
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, getMlPredictionTaskResponseActionListener(listener));
    }

    private ActionListener<MLTaskResponse> getMlPredictionTaskResponseActionListener(ActionListener<MLOutput> listener) {
        ActionListener<MLTaskResponse> internalListener = ActionListener.wrap(predictionResponse -> {
            listener.onResponse(predictionResponse.getOutput());
        }, listener::onFailure);
        ActionListener<MLTaskResponse> actionListener = wrapActionListener(internalListener, res -> {
            MLTaskResponse predictionResponse = MLTaskResponse.fromActionResponse(res);
            return predictionResponse;
        });
        return actionListener;
    }

    private <T extends ActionResponse> ActionListener<T> wrapActionListener(
        final ActionListener<T> listener,
        final Function<ActionResponse, T> recreate
    ) {
        ActionListener<T> actionListener = ActionListener.wrap(r -> {
            listener.onResponse(recreate.apply(r));
            ;
        }, e -> { listener.onFailure(e); });
        return actionListener;
    }

    private void validateMLInput(MLInput mlInput, boolean requireInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("ML Input can't be null");
        }
        if (requireInput && mlInput.getInputDataset() == null) {
            throw new IllegalArgumentException("input data set can't be null");
        }
    }
}
