/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;


import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.ml.common.parameter.*;

/**
 * A client to provide interfaces for machine learning jobs. This will be used by other plugins.
 */
public interface MachineLearningClient {

    /**
     * Do prediction machine learning job
     * @param modelId the trained model id
     * @param mlInput ML input
     * @return ActionFuture of MLOutput
     */
    default ActionFuture<MLOutput> predict(String modelId, MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        predict(modelId, mlInput, actionFuture);
        return actionFuture;
    }

    /**
     * Do prediction machine learning job
     * @param modelId the trained model id
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     * Train model then predict with the same data set.
     * @param mlInput ML input
     * @return ActionFuture of MLOutput
     */
    default ActionFuture<MLOutput> trainAndPredict(MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        trainAndPredict(mlInput, actionFuture);
        return actionFuture;
    }

    /**
     * Train model then predict with the same data set.
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void trainAndPredict(MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     *  Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     * @param mlInput ML input
     * @param asyncTask is async task or not
     * @return ActionFuture of MLOutput
     */
    default ActionFuture<MLOutput> train(MLInput mlInput, boolean asyncTask) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        train(mlInput, asyncTask, actionFuture);
        return actionFuture;
    }


    /**
     * Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     * @param mlInput ML input
     * @param asyncTask is async task or not
     * @param listener a listener to be notified of the result
     */
    void train(MLInput mlInput, boolean asyncTask, ActionListener<MLOutput> listener);

    /**
     * Execute function and return ActionFuture.
     * @param input input data
     * @return ActionFuture of output
     */
    default ActionFuture<Output> execute(Input input) {
        PlainActionFuture<Output> actionFuture = PlainActionFuture.newFuture();
        execute(input, actionFuture);
        return actionFuture;
    }

    /**
     * Execute function and return output in listener
     * @param input input data
     * @param listener action listener
     */
    void execute(Input input, ActionListener<Output> listener);

    /**
     * Get MLModel and return ActionFuture.
     * @param modelId id of the model
     * @return ActionFuture of ml model
     */
    default ActionFuture<MLModel> getModel(String modelId) {
        PlainActionFuture<MLModel> actionFuture = PlainActionFuture.newFuture();
        getModel(modelId, actionFuture);
        return actionFuture;
    }

    /**
     * Get MLModel and return model in listener
     * @param modelId id of the model
     * @param listener action listener
     */
    void getModel(String modelId, ActionListener<MLModel> listener);

    /**
     * Get MLTask and return ActionFuture.
     * @param taskId id of the task
     * @return ActionFuture of ml task
     */
    default ActionFuture<MLTask> getTask(String taskId) {
        PlainActionFuture<MLTask> actionFuture = PlainActionFuture.newFuture();
        getTask(taskId, actionFuture);
        return actionFuture;
    }

    /**
     * Get MLTask and return task in listener
     * @param taskId id of the model
     * @param listener action listener
     */
    void getTask(String taskId, ActionListener<MLTask> listener);

    /**
     *  Delete the model with modelId.
     * @param modelId ML model id
     * @return the result future
     */
    default ActionFuture<DeleteResponse> deleteModel(String modelId) {
        PlainActionFuture<DeleteResponse> actionFuture = PlainActionFuture.newFuture();
        deleteModel(modelId, actionFuture);
        return actionFuture;
    }

    /**
     * Delete MLModel
     * @param modelId id of the model
     * @param listener action listener
     */
    void deleteModel(String modelId, ActionListener<DeleteResponse> listener);

    /**
     *  Delete the task with taskId.
     * @param taskId ML task id
     * @return the result future
     */
    default ActionFuture<DeleteResponse> deleteTask(String taskId) {
        PlainActionFuture<DeleteResponse> actionFuture = PlainActionFuture.newFuture();
        deleteModel(taskId, actionFuture);
        return actionFuture;
    }

    /**
     * Delete MLTask
     * @param taskId id of the task
     * @param listener action listener
     */
    void deleteTask(String taskId, ActionListener<DeleteResponse> listener);

    /**
     *
     * @param searchRequest searchRequest to search the ML Model
     * @return Action future of search response
     */
    default ActionFuture<SearchResponse> searchModel(SearchRequest searchRequest) {
        PlainActionFuture<SearchResponse> actionFuture = PlainActionFuture.newFuture();
        searchModel(searchRequest, actionFuture);
        return actionFuture;
    }

    /**
     *
     * @param searchRequest searchRequest to search the ML Model
     * @param listener action listener
     */
    void searchModel(SearchRequest searchRequest, ActionListener<SearchResponse> listener);

    /**
     *
     * @param searchRequest searchRequest to search the ML Task
     * @return Action future of search response
     */
    default ActionFuture<SearchResponse> searchTask(SearchRequest searchRequest) {
        PlainActionFuture<SearchResponse> actionFuture = PlainActionFuture.newFuture();
        searchTask(searchRequest, actionFuture);
        return actionFuture;
    }

    /**
     *
     * @param searchRequest searchRequest to search the ML Task
     * @param listener action listener
     */
    void searchTask(SearchRequest searchRequest, ActionListener<SearchResponse> listener);
}
