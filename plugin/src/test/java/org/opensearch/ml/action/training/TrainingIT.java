/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.training;

import static org.opensearch.ml.indices.MLIndicesHandler.OS_ML_MODEL_RESULT;
import static org.opensearch.ml.utils.IntegTestUtils.DATA_FRAME_INPUT_DATASET;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_DATA;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_INDEX_NAME;
import static org.opensearch.ml.utils.IntegTestUtils.generateMLTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.generateSearchSourceBuilder;
import static org.opensearch.ml.utils.IntegTestUtils.trainModel;
import static org.opensearch.ml.utils.IntegTestUtils.verifyGeneratedTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.waitModelAvailable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(transportClientRatio = 0.9)
public class TrainingIT extends OpenSearchIntegTestCase {
    @Before
    public void initTestingData() throws ExecutionException, InterruptedException {
        generateMLTestingData();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    public void testGeneratedTestingData() throws ExecutionException, InterruptedException {
        verifyGeneratedTestingData(TESTING_DATA);
    }

    public void testTrainingWithSearchInput() throws ExecutionException, InterruptedException, IOException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);

        String taskId = trainModel(inputDataset);

        waitModelAvailable(taskId);
    }

    public void testTrainingWithDataInput() throws ExecutionException, InterruptedException, IOException {
        String taskId = trainModel(DATA_FRAME_INPUT_DATASET);

        waitModelAvailable(taskId);
    }

    // Train a model without algorithm.
    public void testTrainingWithoutAlgorithm() {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest("", new ArrayList<>(), inputDataset);
        expectThrows(ActionRequestValidationException.class, () -> {
            ActionFuture<MLTrainingTaskResponse> trainingFuture = client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest);
            trainingFuture.actionGet();
        });
    }

    // Train a model without dataset.
    public void testTrainingWithoutDataset() {
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest("kmeans", new ArrayList<>(), null);
        expectThrows(ActionRequestValidationException.class, () -> {
            ActionFuture<MLTrainingTaskResponse> trainingFuture = client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest);
            trainingFuture.actionGet();
        });
    }

    // Train a model with empty dataset.
    public void testTrainingWithEmptyDataset() throws InterruptedException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("noSuchName", ""));
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest("kmeans", new ArrayList<>(), inputDataset);

        ActionFuture<MLTrainingTaskResponse> trainingFuture = client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest);
        MLTrainingTaskResponse trainingResponse = trainingFuture.actionGet();

        // The training taskId and status will be response to the client.
        assertNotNull(trainingResponse);
        String taskId = trainingResponse.getTaskId();
        String status = trainingResponse.getStatus();
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
        assertEquals("CREATED", status);

        SearchSourceBuilder modelSearchSourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = QueryBuilders.termQuery("taskId", taskId);
        modelSearchSourceBuilder.query(queryBuilder);
        SearchRequest modelSearchRequest = new SearchRequest(new String[] { OS_ML_MODEL_RESULT }, modelSearchSourceBuilder);
        SearchResponse modelSearchResponse = null;
        int i = 0;
        while ((modelSearchResponse == null || modelSearchResponse.getHits().getTotalHits().value == 0) && i < 100) {
            try {
                ActionFuture<SearchResponse> searchFuture = client().execute(SearchAction.INSTANCE, modelSearchRequest);
                modelSearchResponse = searchFuture.actionGet();
            } catch (Exception e) {} finally {
                // Wait 100 ms until get valid search response or timeout.
                Thread.sleep(100);
            }
            i++;
        }
        // No model would be trained successfully with empty dataset.
        assertNull(modelSearchResponse);
    }
}
