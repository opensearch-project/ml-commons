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
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.task.*;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.ml.client.MLConstants.ACTION;
import static org.opensearch.ml.client.MLConstants.AD_ANOMALY_RATE;
import static org.opensearch.ml.client.MLConstants.AD_ANOMALY_SCORE_THRESHOLD;
import static org.opensearch.ml.client.MLConstants.AD_DATE_FORMAT;
import static org.opensearch.ml.client.MLConstants.AD_NUMBER_OF_TREES;
import static org.opensearch.ml.client.MLConstants.AD_OUTPUT_AFTER;
import static org.opensearch.ml.client.MLConstants.AD_SAMPLE_SIZE;
import static org.opensearch.ml.client.MLConstants.AD_SHINGLE_SIZE;
import static org.opensearch.ml.client.MLConstants.AD_TIME_DECAY;
import static org.opensearch.ml.client.MLConstants.AD_TIME_FIELD;
import static org.opensearch.ml.client.MLConstants.AD_TIME_ZONE;
import static org.opensearch.ml.client.MLConstants.AD_TRAINING_DATA_SIZE;
import static org.opensearch.ml.client.MLConstants.ALGORITHM;
import static org.opensearch.ml.client.MLConstants.ASYNC;
import static org.opensearch.ml.client.MLConstants.KM_CENTROIDS;
import static org.opensearch.ml.client.MLConstants.KM_DISTANCE_TYPE;
import static org.opensearch.ml.client.MLConstants.KM_ITERATIONS;
import static org.opensearch.ml.client.MLConstants.MODELID;
import static org.opensearch.ml.client.MLConstants.PREDICT;
import static org.opensearch.ml.client.MLConstants.TRAIN;
import static org.opensearch.ml.client.MLConstants.TRAINANDPREDICT;
import static org.opensearch.ml.common.FunctionName.BATCH_RCF;
import static org.opensearch.ml.common.FunctionName.FIT_RCF;
import static org.opensearch.ml.common.FunctionName.KMEANS;

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
                .build();
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void trainAndPredict(MLInput mlInput, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
                .mlInput(mlInput)
                .build();

        client.execute(MLTrainAndPredictionTaskAction.INSTANCE, request, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void train(MLInput mlInput, boolean asyncTask, ActionListener<MLOutput> listener) {
        validateMLInput(mlInput, true);
        MLTrainingTaskRequest trainingTaskRequest = MLTrainingTaskRequest.builder()
                .mlInput(mlInput)
                .async(asyncTask)
                .build();

        client.execute(MLTrainingTaskAction.INSTANCE, trainingTaskRequest, getMlPredictionTaskResponseActionListener(listener));
    }

    @Override
    public void execute(MLInput mlInput, Map<String, Object> args, ActionListener<MLOutput> listener) {
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

    private String getAction(Map<String, Object> arguments) {
        return (String) arguments.get(ACTION);
    }

    private FunctionName getFunctionName(Map<String, Object> arguments) {
        String algo = (String) arguments.get(ALGORITHM);
        if (algo == null) {
            throw new  IllegalArgumentException("The parameter algorithm is required.");
        }
        switch (algo.toLowerCase(Locale.ROOT)) {
            case MLConstants.KMEANS:
                return KMEANS;
            case MLConstants.RCF:
                return arguments.get(AD_TIME_FIELD) == null ?
                        BATCH_RCF : FIT_RCF;
            default:
                throw new IllegalArgumentException(
                        String.format("unsupported algorithm: %s.", algo));
        }
    }

    private MLAlgoParams convertArgumentToMLParameter(Map<String, Object> arguments,
                                                        FunctionName func) {
        switch (func) {
            case KMEANS:
                return buildKMeansParameters(arguments);
            case BATCH_RCF:
                return buildBatchRCFParameters(arguments);
            case FIT_RCF:
                return buildFitRCFParameters(arguments);
            default:
                throw new IllegalArgumentException(
                        String.format("unsupported algorithm: %s.", func));
        }
    }

    private MLAlgoParams buildKMeansParameters(Map<String, Object> arguments) {
        return KMeansParams.builder()
                .centroids((Integer) arguments.get(KM_CENTROIDS))
                .iterations((Integer) arguments.get(KM_ITERATIONS))
                .distanceType(arguments.containsKey(KM_DISTANCE_TYPE)
                        ? KMeansParams.DistanceType.valueOf((
                        (String) arguments.get(KM_DISTANCE_TYPE)).toUpperCase(Locale.ROOT))
                        : null)
                .build();
    }

    private MLAlgoParams buildBatchRCFParameters(Map<String, Object> arguments) {
        return BatchRCFParams.builder()
                .numberOfTrees((Integer) arguments.get(AD_NUMBER_OF_TREES))
                .sampleSize((Integer) arguments.get(AD_SAMPLE_SIZE))
                .outputAfter((Integer) arguments.get(AD_OUTPUT_AFTER))
                .trainingDataSize((Integer) arguments.get(AD_TRAINING_DATA_SIZE))
                .anomalyScoreThreshold((Double) arguments.get(AD_ANOMALY_SCORE_THRESHOLD))
                .build();
    }

    private MLAlgoParams buildFitRCFParameters(Map<String, Object> arguments) {
        return FitRCFParams.builder()
                .numberOfTrees((Integer) arguments.get(AD_NUMBER_OF_TREES))
                .shingleSize((Integer) arguments.get(AD_SHINGLE_SIZE))
                .sampleSize((Integer) arguments.get(AD_SAMPLE_SIZE))
                .outputAfter((Integer) arguments.get(AD_OUTPUT_AFTER))
                .timeDecay((Double) arguments.get(AD_TIME_DECAY))
                .anomalyRate((Double) arguments.get(AD_ANOMALY_RATE))
                .timeField((String) arguments.get(AD_TIME_FIELD))
                .dateFormat(arguments.containsKey(AD_DATE_FORMAT)
                        ? ((String) arguments.get(AD_DATE_FORMAT))
                        : "yyyy-MM-dd HH:mm:ss")
                .timeZone((String) arguments.get(AD_TIME_ZONE))
                .build();
    }

}
