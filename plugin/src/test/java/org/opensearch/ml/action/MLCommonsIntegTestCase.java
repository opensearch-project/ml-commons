/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action;

import static org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams.ObjectiveType.LOGMULTICLASS;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;
import static org.opensearch.ml.utils.TestData.TARGET_FIELD;
import static org.opensearch.ml.utils.TestData.TIME_FIELD;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.opensearch.action.ActionFuture;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
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
import org.opensearch.ml.common.transport.load.LoadModelResponse;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.common.transport.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesRequest;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesResponse;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.common.transport.upload.MLUploadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadModelRequest;
import org.opensearch.ml.common.transport.upload.UploadModelResponse;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.utils.TestData;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQuery());
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
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQuery());
        return trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), inputDataset, async);
    }

    public String trainLogisticRegressionWithIrisData(String irisIndexName, boolean async) {
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQueryTrainLogisticRegression());
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

    public String uploadModel(
        FunctionName functionName,
        String modelName,
        String version,
        MLModelFormat modelFormat,
        String modelType,
        TextEmbeddingModelConfig.FrameworkType frameworkType,
        int dimension,
        String allConfig,
        String url,
        boolean loadModel
    ) {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType(modelType)
            .frameworkType(frameworkType)
            .embeddingDimension(dimension)
            .allConfig(allConfig)
            .build();
        MLUploadInput input = MLUploadInput
            .builder()
            .functionName(functionName)
            .modelName(modelName)
            .version(version)
            .modelFormat(modelFormat)
            .modelConfig(modelConfig)
            .url(url)
            .loadModel(loadModel)
            .build();
        MLUploadModelRequest uploadRequest = MLUploadModelRequest.builder().mlUploadInput(input).build();
        ActionFuture<UploadModelResponse> actionFuture = client().execute(MLUploadModelAction.INSTANCE, uploadRequest);
        UploadModelResponse uploadModelResponse = actionFuture.actionGet();
        String taskId = uploadModelResponse.getTaskId();
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        return taskId;
    }

    public String loadModel(String modelId) {
        MLLoadModelRequest loadRequest = MLLoadModelRequest.builder().modelId(modelId).async(true).dispatchTask(true).build();
        ActionFuture<LoadModelResponse> actionFuture = client().execute(MLLoadModelAction.INSTANCE, loadRequest);
        LoadModelResponse loadModelResponse = actionFuture.actionGet();
        String taskId = loadModelResponse.getTaskId();
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        return taskId;
    }

    public MLProfileResponse getModelProfile(String modelId) {
        String[] allNodes = getAllNodes(clusterService());
        MLProfileInput profileInput = MLProfileInput
            .builder()
            .modelIds(ImmutableSet.of(modelId))
            .returnAllModels(true)
            .returnAllTasks(true)
            .build();
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
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) predictionResponse.getOutput();
        DataFrame predictionResult = mlPredictionOutput.getPredictionResult();
        assertEquals(size, predictionResult.size());
        return predictionResult;
    }

    public MLTaskResponse predict(String modelId, FunctionName functionName, MLInputDataset inputDataset, MLAlgoParams parameters) {
        MLInput mlInput = MLInput.builder().algorithm(functionName).inputDataset(inputDataset).parameters(parameters).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        return predictionResponse;
    }

    public UnloadModelNodesResponse unloadModel(String modelId) {
        String[] allNodes = getAllNodes(clusterService());
        UnloadModelNodesRequest unloadRequest = new UnloadModelNodesRequest(allNodes, new String[] { modelId });
        UnloadModelNodesResponse response = client().execute(MLUnloadModelAction.INSTANCE, unloadRequest).actionGet();
        return response;
    }

    public MLTask getTask(String taskId) {
        MLTaskGetRequest getRequest = new MLTaskGetRequest(taskId);
        MLTaskGetResponse response = client().execute(MLTaskGetAction.INSTANCE, getRequest).actionGet(5000);
        return response.getMlTask();
    }

    public MLModel getModel(String modelId) {
        MLModelGetRequest getRequest = new MLModelGetRequest(modelId, false);
        MLModelGetResponse response = client().execute(MLModelGetAction.INSTANCE, getRequest).actionGet(5000);
        return response.getMlModel();
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
}
