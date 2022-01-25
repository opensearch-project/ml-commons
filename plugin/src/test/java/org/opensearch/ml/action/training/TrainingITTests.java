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

import static org.opensearch.ml.utils.IntegTestUtils.DATA_FRAME_INPUT_DATASET;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_DATA;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_INDEX_NAME;
import static org.opensearch.ml.utils.IntegTestUtils.generateMLTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.generateSearchSourceBuilder;
import static org.opensearch.ml.utils.IntegTestUtils.trainModel;
import static org.opensearch.ml.utils.IntegTestUtils.verifyGeneratedTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.waitModelAvailable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(transportClientRatio = 0.9)
public class TrainingITTests extends OpenSearchIntegTestCase {
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

    @Ignore("This test case is flaky, something is off with waitModelAvailable(taskId) method."
        + " This issue will be tracked in an issue and will be fixed later")
    public void testTrainingWithSearchInput() throws ExecutionException, InterruptedException, IOException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);

        String taskId = trainModel(inputDataset);

        waitModelAvailable(taskId);
    }

    @Ignore("This test case is flaky, something is off with waitModelAvailable(taskId) method."
        + " This issue will be tracked in an issue and will be fixed later")
    public void testTrainingWithDataInput() throws ExecutionException, InterruptedException, IOException {
        String taskId = trainModel(DATA_FRAME_INPUT_DATASET);

        waitModelAvailable(taskId);
    }

    // Train a model without dataset.
    public void testTrainingWithoutDataset() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest(mlInput, true);
        expectThrows(ActionRequestValidationException.class, () -> {
            ActionFuture<MLTaskResponse> trainingFuture = client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest);
            trainingFuture.actionGet();
        });
    }

    // Train a model with empty dataset.
    public void testTrainingWithEmptyDataset() throws InterruptedException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("noSuchName", ""));
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLTrainingTaskRequest trainingRequest = new MLTrainingTaskRequest(mlInput, false);

        expectThrows(IllegalArgumentException.class, () -> client().execute(MLTrainingTaskAction.INSTANCE, trainingRequest).actionGet());
    }
}
