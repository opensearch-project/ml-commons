/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.task.*;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;

import java.util.Map;
import java.util.function.Function;

import static org.opensearch.ml.common.input.Constants.ASYNC;
import static org.opensearch.ml.common.input.Constants.MODELID;
import static org.opensearch.ml.common.input.Constants.PREDICT;
import static org.opensearch.ml.common.input.Constants.TRAIN;
import static org.opensearch.ml.common.input.Constants.TRAINANDPREDICT;
import static org.opensearch.ml.common.input.InputHelper.convertArgumentToMLParameter;
import static org.opensearch.ml.common.input.InputHelper.getAction;
import static org.opensearch.ml.common.input.InputHelper.getFunctionName;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MachineLearningNodeClient implements MachineLearningClient {

    Client client;

    @Override
    public void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder()
                .mlInput(mlInput)
                .modelId(modelId)
                .dispatchTask(true)
                .build();
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void trainAndPredict(MLInput mlInput, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
                .mlInput(mlInput)
                .dispatchTask(true)
                .build();

        client.execute(MLTrainAndPredictionTaskAction.INSTANCE, request, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void train(MLInput mlInput, boolean asyncTask, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);
        MLTrainingTaskRequest trainingTaskRequest = MLTrainingTaskRequest.builder()
                .mlInput(mlInput)
                .async(asyncTask)
                .dispatchTask(true)
                .build();

        client.execute(MLTrainingTaskAction.INSTANCE, trainingTaskRequest, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void run(MLInput mlInput, Map<String, Object> args, ActionListener<MLOutput> listener) {
        String action = getAction(args);
        if (action == null) {
            throw new IllegalArgumentException("The parameter action is required.");
        }
        FunctionName functionName = getFunctionName(args);
        MLAlgoParams mlAlgoParams = convertArgumentToMLParameter(args, functionName);
        mlInput.setAlgorithm(functionName);
        mlInput.setParameters(mlAlgoParams);
        switch (action) {
            case TRAIN:
                boolean asyncTask = args.containsKey(ASYNC) ? (boolean) args.get(ASYNC) : false;
                train(mlInput, asyncTask, listener);
                break;
            case PREDICT:
                String modelId = (String) args.get(MODELID);
                if (modelId == null)
                    throw new IllegalArgumentException("The model ID is required for prediction.");
                predict(modelId, mlInput, listener);
                break;
            case TRAINANDPREDICT:
                trainAndPredict(mlInput, listener);
                break;
            default:
                throw new  IllegalArgumentException("Unsupported action.");
        }
    }

    @Override
    public void getModel(String modelId, ActionListener<MLModel> listener) {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.builder()
                .modelId(modelId)
                .build();

        client.execute(MLModelGetAction.INSTANCE, mlModelGetRequest, getMlGetModelResponseActionListener(listener));
    }

    private ActionListener<MLModelGetResponse> getMlGetModelResponseActionListener(ActionListener<MLModel> listener) {
        ActionListener<MLModelGetResponse> internalListener = ActionListener.wrap(predictionResponse -> {
            listener.onResponse(predictionResponse.getMlModel());
        }, listener::onFailure);
        ActionListener<MLModelGetResponse> actionListener = wrapActionListener(internalListener, res -> {
            MLModelGetResponse getResponse = MLModelGetResponse.fromActionResponse(res);
            return getResponse;
        });
        return actionListener;
    }

    @Override
    public void deleteModel(String modelId, ActionListener<DeleteResponse> listener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.builder()
                .modelId(modelId)
                .build();

        client.execute(MLModelDeleteAction.INSTANCE, mlModelDeleteRequest, ActionListener.wrap(deleteResponse -> {
            listener.onResponse(deleteResponse);
        }, listener::onFailure));
    }

    @Override
    public void searchModel(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
        client.execute(MLModelSearchAction.INSTANCE, searchRequest, ActionListener.wrap(searchResponse -> {
            listener.onResponse(searchResponse);
        }, listener::onFailure));
    }

    @Override
    public void searchModelGroup(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
        client.execute(MLModelGroupSearchAction.INSTANCE, searchRequest, ActionListener.wrap(searchResponse -> {
            listener.onResponse(searchResponse);
        }, listener::onFailure));
    }


    @Override
    public void getTask(String taskId, ActionListener<MLTask> listener) {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder()
                .taskId(taskId)
                .build();

        client.execute(MLTaskGetAction.INSTANCE, mlTaskGetRequest, ActionListener.wrap(response -> {
            listener.onResponse(MLTaskGetResponse.fromActionResponse(response).getMlTask());
        }, listener::onFailure));
    }

    @Override
    public void deleteTask(String taskId, ActionListener<DeleteResponse> listener) {
        MLTaskDeleteRequest mlTaskDeleteRequest = MLTaskDeleteRequest.builder()
                .taskId(taskId)
                .build();

        client.execute(MLTaskDeleteAction.INSTANCE, mlTaskDeleteRequest, ActionListener.wrap(deleteResponse -> {
            listener.onResponse(deleteResponse);
        }, listener::onFailure));
    }

    @Override
    public void searchTask(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
        client.execute(MLTaskSearchAction.INSTANCE, searchRequest, ActionListener.wrap(searchResponse -> {
            listener.onResponse(searchResponse);
        }, listener::onFailure));
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

    private <T extends ActionResponse> ActionListener<T> wrapActionListener(final ActionListener<T> listener, final Function<ActionResponse, T> recreate) {
        ActionListener<T> actionListener = ActionListener.wrap(r-> {
            listener.onResponse(recreate.apply(r));;
        }, e->{
            listener.onFailure(e);
        });
        return actionListener;
    }

    private void validateMLInput(MLInput mlInput, boolean requireInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("ML Input can't be null");
        }
        if(requireInput && mlInput.getInputDataset() == null) {
            throw new IllegalArgumentException("input data set can't be null");
        }
    }
}
