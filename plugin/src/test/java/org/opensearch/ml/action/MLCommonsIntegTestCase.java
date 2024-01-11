/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action;

import static org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams.ObjectiveType.LOGMULTICLASS;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;
import static org.opensearch.ml.utils.TestData.TARGET_FIELD;
import static org.opensearch.ml.utils.TestData.TIME_FIELD;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.action.profile.MLProfileResponse;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetRequest;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.utils.TestData;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.gson.Gson;

public class MLCommonsIntegTestCase extends OpenSearchIntegTestCase {
    private Gson gson = new Gson();

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    public Set<String> getAllDataNodeIds() {
        DiscoveryNodes nodes = clusterService().state().getNodes();
        Set<String> nodeIds = new HashSet<>();
        for (DiscoveryNode node : nodes) {
            if (node.isDataNode()) {
                nodeIds.add(node.getId());
            }
        }
        return nodeIds;
    }

    public void loadIrisData(String indexName) {
        BulkRequest bulkRequest = new BulkRequest();
        String[] rows = TestData.IRIS_DATA.split("\n");
        for (int i = 1; i < rows.length; i += 2) {
            IndexRequest indexRequest = new IndexRequest(indexName).id(i + "");
            indexRequest.source(rows[i], XContentType.JSON);
            bulkRequest.add(indexRequest);
        }
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client().bulk(bulkRequest).actionGet(5000);
    }

    public DataFrame irisDataFrame() {
        DataFrame dataFrame = new DefaultDataFrame(
            new ColumnMeta[] {
                new ColumnMeta("petal_length_in_cm", ColumnType.DOUBLE),
                new ColumnMeta("petal_width_in_cm", ColumnType.DOUBLE) }
        );
        String[] rows = TestData.IRIS_DATA.split("\n");

        for (int i = 1; i < rows.length; i += 2) {
            Map<String, Object> map = gson.fromJson(rows[i], Map.class);
            dataFrame.appendRow(new Object[] { map.get("petal_length_in_cm"), map.get("petal_width_in_cm") });
        }
        return dataFrame;
    }

    public SearchSourceBuilder irisDataQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1000);
        searchSourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        return searchSourceBuilder;
    }

    public SearchSourceBuilder irisDataQueryTrainLogisticRegression() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1000);
        searchSourceBuilder
            .fetchSource(
                new String[] { "sepal_length_in_cm", "sepal_width_in_cm", "petal_length_in_cm", "petal_width_in_cm", "class" },
                null
            );
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        return searchSourceBuilder;
    }

    public SearchSourceBuilder irisDataQueryPredictLogisticRegression() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1000);
        searchSourceBuilder
            .fetchSource(new String[] { "sepal_length_in_cm", "sepal_width_in_cm", "petal_length_in_cm", "petal_width_in_cm" }, null);
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        return searchSourceBuilder;
    }

    public MLInputDataset emptyQueryInputDataSet(String indexName) {
        SearchSourceBuilder searchSourceBuilder = irisDataQuery();
        searchSourceBuilder.query(QueryBuilders.matchQuery("class", "wrong_value"));
        return new SearchQueryInputDataset(Collections.singletonList(indexName), searchSourceBuilder);
    }

    public MLPredictionOutput trainAndPredictKmeansWithIrisData(String irisIndexName) {
        MLInputDataset inputDataset = new SearchQueryInputDataset(List.of(irisIndexName), irisDataQuery());
        return trainAndPredict(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), inputDataset);
    }

    public MLPredictionOutput trainAndPredictBatchRCFWithDataFrame(int dataSize) {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(dataSize));
        return trainAndPredict(FunctionName.BATCH_RCF, BatchRCFParams.builder().build(), inputDataset);
    }

    public MLPredictionOutput trainAndPredict(FunctionName functionName, MLAlgoParams params, MLInputDataset inputDataset) {
        MLInput mlInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputDataset).build();
        MLTrainingTaskRequest trainingRequest = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
        ActionFuture<MLTaskResponse> trainingFuture = client().execute(MLTrainAndPredictionTaskAction.INSTANCE, trainingRequest);
        MLTaskResponse trainingResponse = trainingFuture.actionGet();
        assertNotNull(trainingResponse);

        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) trainingResponse.getOutput();
        return mlPredictionOutput;
    }

    public String trainKmeansWithIrisData(String irisIndexName, boolean async) {
        MLInputDataset inputDataset = new SearchQueryInputDataset(List.of(irisIndexName), irisDataQuery());
        return trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), inputDataset, async);
    }

    public String trainLogisticRegressionWithIrisData(String irisIndexName, boolean async) {
        MLInputDataset inputDataset = new SearchQueryInputDataset(List.of(irisIndexName), irisDataQueryTrainLogisticRegression());
        return trainModel(
            FunctionName.LOGISTIC_REGRESSION,
            LogisticRegressionParams.builder().objectiveType(LOGMULTICLASS).target("class").build(),
            inputDataset,
            async
        );
    }

    public String trainBatchRCFWithDataFrame(int dataSize, boolean async) {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(dataSize));
        return trainModel(FunctionName.BATCH_RCF, BatchRCFParams.builder().build(), inputDataset, async);
    }

    public String trainFitRCFWithDataFrame(int dataSize, boolean async) {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(dataSize, true));
        return trainModel(FunctionName.FIT_RCF, FitRCFParams.builder().timeField(TIME_FIELD).build(), inputDataset, async);
    }

    public LinearRegressionParams getLinearRegressionParams() {
        return LinearRegressionParams
            .builder()
            .objectiveType(LinearRegressionParams.ObjectiveType.SQUARED_LOSS)
            .optimizerType(LinearRegressionParams.OptimizerType.LINEAR_DECAY_SGD)
            .learningRate(0.01)
            .epochs(10)
            .epsilon(1e-5)
            .beta1(0.9)
            .beta2(0.99)
            .target(TARGET_FIELD)
            .build();
    }

    public String trainLinearRegressionWithDataFrame(int dataSize, boolean async) {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrameForLinearRegression(dataSize));
        return trainModel(FunctionName.LINEAR_REGRESSION, getLinearRegressionParams(), inputDataset, async);
    }

    public String trainModel(FunctionName functionName, MLAlgoParams params, MLInputDataset inputDataset, boolean async) {
        MLInput mlInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputDataset).build();
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest(mlInput, async);
        ActionFuture<MLTaskResponse> trainingFuture = client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest);
        MLTaskResponse trainingResponse = trainingFuture.actionGet();
        assertNotNull(trainingResponse);

        MLTrainingOutput modelTrainingOutput = (MLTrainingOutput) trainingResponse.getOutput();
        String id = async ? modelTrainingOutput.getTaskId() : modelTrainingOutput.getModelId();
        String status = modelTrainingOutput.getStatus();
        assertNotNull(id);
        assertFalse(id.isEmpty());
        if (async) {
            assertEquals("CREATED", status);
        } else {
            assertEquals("COMPLETED", status);
        }
        return id;
    }

    public String registerModel(
        FunctionName functionName,
        String modelName,
        String version,
        MLModelFormat modelFormat,
        String modelType,
        TextEmbeddingModelConfig.FrameworkType frameworkType,
        int dimension,
        String allConfig,
        String url,
        boolean deployModel
    ) {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType(modelType)
            .frameworkType(frameworkType)
            .embeddingDimension(dimension)
            .allConfig(allConfig)
            .build();
        MLRegisterModelInput input = MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName(modelName)
            .version(version)
            .modelFormat(modelFormat)
            .modelConfig(modelConfig)
            .url(url)
            .deployModel(deployModel)
            .build();
        MLRegisterModelRequest registerModelRequest = MLRegisterModelRequest.builder().registerModelInput(input).build();
        ActionFuture<MLRegisterModelResponse> actionFuture = client().execute(MLRegisterModelAction.INSTANCE, registerModelRequest);
        MLRegisterModelResponse MLRegisterModelResponse = actionFuture.actionGet();
        String taskId = MLRegisterModelResponse.getTaskId();
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        return taskId;
    }

    public String deployModel(String modelId, String[] modelNodeIds) {
        MLDeployModelRequest deployModelRequest = MLDeployModelRequest
            .builder()
            .modelId(modelId)
            .modelNodeIds(modelNodeIds)
            .async(true)
            .dispatchTask(true)
            .build();
        ActionFuture<MLDeployModelResponse> actionFuture = client().execute(MLDeployModelAction.INSTANCE, deployModelRequest);
        MLDeployModelResponse MLDeployModelResponse = actionFuture.actionGet();
        String taskId = MLDeployModelResponse.getTaskId();
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        return taskId;
    }

    public MLProfileResponse getModelProfile(String modelId) {
        MLProfileInput profileInput = MLProfileInput.builder().modelIds(Set.of(modelId)).returnAllModels(true).returnAllTasks(true).build();
        return profile(profileInput);
    }

    public MLProfileResponse getAllProfile() {
        MLProfileInput profileInput = MLProfileInput.builder().returnAllModels(true).returnAllTasks(true).build();
        return profile(profileInput);
    }

    public MLProfileResponse profile(MLProfileInput profileInput) {
        String[] allNodes = getAllNodes(clusterService());
        MLProfileRequest profileRequest = new MLProfileRequest(allNodes, profileInput);
        ActionFuture<MLProfileResponse> actionFuture = client().execute(MLProfileAction.INSTANCE, profileRequest);
        MLProfileResponse response = actionFuture.actionGet();
        return response;
    }

    public DataFrame predictAndVerify(
        String modelId,
        MLInputDataset inputDataset,
        FunctionName functionName,
        MLAlgoParams parameters,
        int size
    ) {
        MLInput mlInput = MLInput.builder().algorithm(functionName).inputDataset(inputDataset).parameters(parameters).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput, null);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) predictionResponse.getOutput();
        DataFrame predictionResult = mlPredictionOutput.getPredictionResult();
        assertEquals(size, predictionResult.size());
        return predictionResult;
    }

    public MLTaskResponse predict(String modelId, FunctionName functionName, MLInputDataset inputDataset, MLAlgoParams parameters) {
        MLInput mlInput = MLInput.builder().algorithm(functionName).inputDataset(inputDataset).parameters(parameters).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput, null);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        return predictionResponse;
    }

    public MLUndeployModelNodesResponse undeployModel(String modelId) {
        String[] allNodes = getAllNodes(clusterService());
        MLUndeployModelNodesRequest undeployRequest = new MLUndeployModelNodesRequest(allNodes, new String[] { modelId });
        MLUndeployModelNodesResponse response = client().execute(MLUndeployModelAction.INSTANCE, undeployRequest).actionGet();
        return response;
    }

    public MLTask getTask(String taskId) {
        MLTaskGetRequest getRequest = new MLTaskGetRequest(taskId);
        MLTaskGetResponse response = client().execute(MLTaskGetAction.INSTANCE, getRequest).actionGet(5000);
        return response.getMlTask();
    }

    public MLModel getModel(String modelId) {
        MLModelGetRequest getRequest = new MLModelGetRequest(modelId, false, true);
        MLModelGetResponse response = client().execute(MLModelGetAction.INSTANCE, getRequest).actionGet(5000);
        return response.getMlModel();
    }

    public MLModelGroup getModelGroup(String modelGroupId) {
        MLModelGroupGetRequest getRequest = new MLModelGroupGetRequest(modelGroupId);
        MLModelGroupGetResponse response = client().execute(MLModelGroupGetAction.INSTANCE, getRequest).actionGet(5000);
        return response.getMlModelGroup();
    }

    public SearchResponse searchModelChunks(String modelId) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(null, new String[] { MLModel.OLD_MODEL_CONTENT_FIELD, MLModel.MODEL_CONTENT_FIELD });
        QueryBuilder queryBuilder = new TermQueryBuilder(MLModel.MODEL_ID_FIELD, modelId);
        searchSourceBuilder.query(queryBuilder);
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(CommonValue.ML_MODEL_INDEX);
        SearchResponse searchResponse = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet(5000);
        return searchResponse;
    }

    public MLSyncUpNodesResponse syncUp_RunningModelAndTask() {
        String[] allNodes = getAllNodes(clusterService());
        MLSyncUpInput gatherInfoInput = MLSyncUpInput.builder().getDeployedModels(true).build();
        MLSyncUpNodesRequest gatherInfoRequest = new MLSyncUpNodesRequest(allNodes, gatherInfoInput);
        // gather running model/tasks on nodes
        MLSyncUpNodesResponse syncUpResponse = client().execute(MLSyncUpAction.INSTANCE, gatherInfoRequest).actionGet(5000);
        return syncUpResponse;
    }

    public MLSyncUpNodesResponse syncUp_Clear() {
        String[] allNodes = getAllNodes(clusterService());
        MLSyncUpInput syncUpInput = MLSyncUpInput.builder().syncRunningDeployModelTasks(true).clearRoutingTable(true).build();
        MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
        MLSyncUpNodesResponse syncUpResponse = client().execute(MLSyncUpAction.INSTANCE, syncUpRequest).actionGet(5000);
        return syncUpResponse;
    }

    @Override
    protected Settings nodeSettings(int ordinal) {
        return Settings
            .builder()
            .put(super.nodeSettings(ordinal))
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), false)
            // Set native memory threshold as 100 to prevent IT failures
            .put(ML_COMMONS_NATIVE_MEM_THRESHOLD.getKey(), 100)
            .build();
    }
}
