/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.ActionFuture;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.Strings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

public class IntegTestUtils extends OpenSearchIntegTestCase {
    public static final String TESTING_DATA = "{\n"
        + "\"k1\":1.1,\n"
        + "\"k2\":1.2,\n"
        + "\"k3\":1.3,\n"
        + "\"k4\":1.4,\n"
        + "\"k5\":1.5\n"
        + "}";
    public static final String TESTING_INDEX_NAME = "test_data";
    public static final DataFrameInputDataset DATA_FRAME_INPUT_DATASET = DataFrameInputDataset
        .builder()
        .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("k1", 1.1);
                put("k2", 1.2);
                put("k3", 1.3);
                put("k4", 1.4);
                put("k5", 1.5);
            }
        })))
        .build();

    // Generate testing data in the testing cluster.
    public static void generateMLTestingData() throws ExecutionException, InterruptedException {
        IndexRequest indexRequest = new IndexRequest(TESTING_INDEX_NAME).id("1").setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        indexRequest.source(TESTING_DATA, XContentType.JSON);

        ActionFuture<IndexResponse> future = client().execute(IndexAction.INSTANCE, indexRequest);
        IndexResponse response = future.actionGet();

        assertNotNull(response);
        assertEquals(RestStatus.CREATED.getStatus(), response.status().getStatus());
        assertEquals(DocWriteResponse.Result.CREATED.getLowercase(), response.getResult().getLowercase());

        verifyGeneratedTestingData(TESTING_DATA);
    }

    // Verify the testing data was generated in the testing cluster.
    public static void verifyGeneratedTestingData(String testingData) throws ExecutionException, InterruptedException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();

        SearchRequest searchRequest = new SearchRequest().indices(TESTING_INDEX_NAME).source(searchSourceBuilder);
        ActionFuture<SearchResponse> searchFuture = client().execute(SearchAction.INSTANCE, searchRequest);
        SearchResponse searchResponse = searchFuture.actionGet();

        assertNotNull(searchResponse);
        SearchHits hits = searchResponse.getHits();
        assertNotNull(hits);
        assertEquals(1, hits.getHits().length);
        SearchHit hit = hits.getHits()[0];
        assertNotNull(hit);
        assertEquals(testingData, hit.getSourceAsString());
    }

    public static SearchSourceBuilder generateSearchSourceBuilder() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(100);
        searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        return searchSourceBuilder;
    }

    /**
     * Train model and return model id if not async request or task id for async request.
     * @param inputDataset input data set
     * @param async async request or not
     * @return model id for sync request or task id for async request.
     */
    public static String trainModel(MLInputDataset inputDataset, boolean async) {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
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

    // Wait a while (20 seconds at most) for the model to be available in the ml index.
    public static SearchResponse waitModelAvailable1(String taskId) throws InterruptedException {
        SearchSourceBuilder modelSearchSourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = QueryBuilders.termQuery("taskId", taskId);
        modelSearchSourceBuilder.query(queryBuilder);
        SearchRequest modelSearchRequest = new SearchRequest(new String[] { ML_MODEL_INDEX }, modelSearchSourceBuilder);
        SearchResponse modelSearchResponse = null;
        int i = 0;
        while ((modelSearchResponse == null || modelSearchResponse.getHits().getTotalHits().value == 0) && i < 500) {
            try {
                ActionFuture<SearchResponse> searchFuture = client().execute(SearchAction.INSTANCE, modelSearchRequest);
                modelSearchResponse = searchFuture.actionGet();
            } catch (Exception e) {} finally {
                // Wait 100 ms until get valid search response or timeout.
                Thread.sleep(100);
            }
            i++;
        }
        assertNotNull(modelSearchResponse);
        assertTrue(modelSearchResponse.getHits().getTotalHits().value > 0);
        return modelSearchResponse;
    }

    public static MLTask waitModelAvailable(String taskId) throws InterruptedException {
        MLTaskGetRequest getTaskRequest = new MLTaskGetRequest(taskId);
        MLTask mlTask = null;
        int i = 0;
        while ((mlTask == null || mlTask.getModelId() == null) && i < 500) {
            try {
                MLTaskGetResponse taskGetResponse = client().execute(MLTaskGetAction.INSTANCE, getTaskRequest).actionGet(5000);
                mlTask = taskGetResponse.getMlTask();
            } catch (Exception e) {} finally {
                // Wait 100 ms until get valid search response or timeout.
                Thread.sleep(100);
            }
            i++;
        }
        assertNotNull(mlTask);
        assertNotNull(mlTask.getModelId());
        return mlTask;
    }

    // Predict with the model generated, and verify the prediction result.
    public static void predictAndVerifyResult(String taskId, MLInputDataset inputDataset) throws IOException {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(taskId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        builder.startObject();
        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) predictionResponse.getOutput();
        mlPredictionOutput.getPredictionResult().toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String jsonStr = Strings.toString(builder);
        String expectedStr1 = "{\"column_metas\":[{\"name\":\"ClusterID\",\"column_type\":\"INTEGER\"}],"
            + "\"rows\":[{\"values\":[{\"column_type\":\"INTEGER\",\"value\":0}]}]}";
        String expectedStr2 = "{\"column_metas\":[{\"name\":\"ClusterID\",\"column_type\":\"INTEGER\"}],"
            + "\"rows\":[{\"values\":[{\"column_type\":\"INTEGER\",\"value\":1}]}]}";
        // The prediction result would not be a fixed value.
        assertTrue(expectedStr1.equals(jsonStr) || expectedStr2.equals(jsonStr));
    }

    // Generate empty testing dataset.
    public static MLInputDataset generateEmptyDataset() {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("noSuchName", ""));
        return new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);
    }
}
