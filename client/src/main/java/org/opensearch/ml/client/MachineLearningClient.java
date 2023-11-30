/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import java.util.List;
import java.util.Map;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;

/**
 * A client to provide interfaces for machine learning jobs. This will be used by other plugins.
 */
public interface MachineLearningClient {

    /**
     * Do prediction machine learning job
     * For additional info on Predict, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#predict
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
     * For additional info on Predict, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#predict
     * @param modelId the trained model id
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     * Train model then predict with the same data set.
     * For additional info on train and predict, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#train-and-predict
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
     * For additional info on train and predict, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#train-and-predict
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void trainAndPredict(MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     *  Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     * For more info on train model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#train-model
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
     * For more info on train model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#train-model
     * @param mlInput ML input
     * @param asyncTask is async task or not
     * @param listener a listener to be notified of the result
     */
    void train(MLInput mlInput, boolean asyncTask, ActionListener<MLOutput> listener);

    /**
     * Execute train/predict/trainandpredict.
     * @param mlInput ML input
     * @param args algorithm parameters
     * @return ActionFuture of MLOutput
     */
    default ActionFuture<MLOutput> run(MLInput mlInput, Map<String, Object> args) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        run(mlInput, args, actionFuture);
        return actionFuture;
    }

    /**
     * Execute train/predict/trainandpredict.
     * @param mlInput ML input
     * @param args algorithm parameters
     * @param listener a listener to be notified of the result
     */
    void run(MLInput mlInput, Map<String, Object> args, ActionListener<MLOutput> listener);

    /**
     * Get MLModel and return ActionFuture.
     * For more info on get model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-model-information
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
     * For more info on get model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-model-information
     * @param modelId id of the model
     * @param listener action listener
     */
    void getModel(String modelId, ActionListener<MLModel> listener);

    /**
     * Get MLTask and return ActionFuture.
     * For more info on get task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-task-information
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
     * For more info on get task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-task-information
     * @param taskId id of the model
     * @param listener action listener
     */
    void getTask(String taskId, ActionListener<MLTask> listener);

    /**
     *  Delete the model with modelId.
     *  For more info on delete model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#delete-model
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
     * For more info on delete model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#delete-model
     * @param modelId id of the model
     * @param listener action listener
     */
    void deleteModel(String modelId, ActionListener<DeleteResponse> listener);

    /**
     *  Delete the task with taskId.
     *  For more info on delete task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#delete-task
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
     * For more info on delete task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#delete-task
     * @param taskId id of the task
     * @param listener action listener
     */
    void deleteTask(String taskId, ActionListener<DeleteResponse> listener);

    /**
     * For more info on search model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#search-model
     * @param searchRequest searchRequest to search the ML Model
     * @return Action future of search response
     */
    default ActionFuture<SearchResponse> searchModel(SearchRequest searchRequest) {
        PlainActionFuture<SearchResponse> actionFuture = PlainActionFuture.newFuture();
        searchModel(searchRequest, actionFuture);
        return actionFuture;
    }

    /**
     * For more info on search model, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#search-model
     * @param searchRequest searchRequest to search the ML Model
     * @param listener action listener
     */
    void searchModel(SearchRequest searchRequest, ActionListener<SearchResponse> listener);

    /**
     * For more info on search task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#search-task
     * @param searchRequest searchRequest to search the ML Task
     * @return Action future of search response
     */
    default ActionFuture<SearchResponse> searchTask(SearchRequest searchRequest) {
        PlainActionFuture<SearchResponse> actionFuture = PlainActionFuture.newFuture();
        searchTask(searchRequest, actionFuture);
        return actionFuture;
    }

    /**
     * For more info on search task, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#search-task
     * @param searchRequest searchRequest to search the ML Task
     * @param listener action listener
     */
    void searchTask(SearchRequest searchRequest, ActionListener<SearchResponse> listener);

    /**
     * Register model
     * For additional info on register, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#registering-a-model
     * @param mlInput ML input
     */
    default ActionFuture<MLRegisterModelResponse> register(MLRegisterModelInput mlInput) {
        PlainActionFuture<MLRegisterModelResponse> actionFuture = PlainActionFuture.newFuture();
        register(mlInput, actionFuture);
        return actionFuture;
    }

    /**
     * Register model
     * For additional info on register, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#registering-a-model
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void register(MLRegisterModelInput mlInput, ActionListener<MLRegisterModelResponse> listener);

    /**
     * Deploy model
     * For additional info on deploy, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#deploying-a-model
     * @param modelId the model id
     */
    default ActionFuture<MLDeployModelResponse> deploy(String modelId) {
        PlainActionFuture<MLDeployModelResponse> actionFuture = PlainActionFuture.newFuture();
        deploy(modelId, actionFuture);
        return actionFuture;
    }

    /**
     * Deploy model
     * For additional info on deploy, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#deploying-a-model
     * @param modelId the model id
     * @param listener a listener to be notified of the result
     */
    void deploy(String modelId, ActionListener<MLDeployModelResponse> listener);

    /**
     * Create connector for remote model
     * @param mlCreateConnectorInput Create Connector Input, refer: https://opensearch.org/docs/latest/ml-commons-plugin/extensibility/connectors/
     * @return the result future
     */
    default ActionFuture<MLCreateConnectorResponse> createConnector(MLCreateConnectorInput mlCreateConnectorInput) {
        PlainActionFuture<MLCreateConnectorResponse> actionFuture = PlainActionFuture.newFuture();
        createConnector(mlCreateConnectorInput, actionFuture);
        return actionFuture;
    }

    void createConnector(MLCreateConnectorInput mlCreateConnectorInput, ActionListener<MLCreateConnectorResponse> listener);

    /**
     * Delete connector for remote model
     * @param connectorId The id of the connector to delete
     * @return the result future
     */
    default ActionFuture<DeleteResponse> deleteConnector(String connectorId) {
        PlainActionFuture<DeleteResponse> actionFuture = PlainActionFuture.newFuture();
        deleteConnector(connectorId, actionFuture);
        return actionFuture;
    }

    void deleteConnector(String connectorId, ActionListener<DeleteResponse> listener);

    /**
     * Register model group
     * For additional info on model group, refer: https://opensearch.org/docs/latest/ml-commons-plugin/model-access-control#registering-a-model-group
     * @param mlRegisterModelGroupInput model group input
     */
    default ActionFuture<MLRegisterModelGroupResponse> registerModelGroup(MLRegisterModelGroupInput mlRegisterModelGroupInput) {
        PlainActionFuture<MLRegisterModelGroupResponse> actionFuture = PlainActionFuture.newFuture();
        registerModelGroup(mlRegisterModelGroupInput, actionFuture);
        return actionFuture;
    }

    /**
     * Register model group
     * For additional info on model group, refer: https://opensearch.org/docs/latest/ml-commons-plugin/model-access-control#registering-a-model-group
     * @param mlRegisterModelGroupInput model group input
     * @param listener a listener to be notified of the result
     */
    void registerModelGroup(MLRegisterModelGroupInput mlRegisterModelGroupInput, ActionListener<MLRegisterModelGroupResponse> listener);

    /**
     * Get a list of ToolMetadata and return ActionFuture.
     * For more info on list tools, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#list-tools
     * @return ActionFuture of a list of tool metadata
     */
    default ActionFuture<List<ToolMetadata>> listTools() {
        PlainActionFuture<List<ToolMetadata>> actionFuture = PlainActionFuture.newFuture();
        listTools(actionFuture);
        return actionFuture;
    }

    /**
     * List ToolMetadata and return a list of ToolMetadata in listener
     * For more info on get tools, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#list-tools
     * @param listener action listener
     */
    void listTools(ActionListener<List<ToolMetadata>> listener);

    /**
     * Get ToolMetadata and return ActionFuture.
     * For more info on get tool, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-tool
     * @return ActionFuture of tool metadata
     */
    default ActionFuture<ToolMetadata> getTool(String toolName) {
        PlainActionFuture<ToolMetadata> actionFuture = PlainActionFuture.newFuture();
        getTool(toolName, actionFuture);
        return actionFuture;
    }

    /**
     * Get ToolMetadata and return ToolMetadata in listener
     * For more info on get tool, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#get-tool
     * @param listener action listener
     */
    void getTool(String toolName, ActionListener<ToolMetadata> listener);

    /**
     * Registers new agent and returns ActionFuture.
     * @param mlAgent Register agent input, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#register-agent
     * @return the result future
     */
    default ActionFuture<MLRegisterAgentResponse> registerAgent(MLAgent mlAgent) {
        PlainActionFuture<MLRegisterAgentResponse> actionFuture = PlainActionFuture.newFuture();
        registerAgent(mlAgent, actionFuture);
        return actionFuture;
    }

    /**
     * Registers new agent and returns agent ID in response
     * @param mlAgent Register agent input, refer: https://opensearch.org/docs/latest/ml-commons-plugin/api/#register-agent
     */
    void registerAgent(MLAgent mlAgent, ActionListener<MLRegisterAgentResponse> listener);
}
