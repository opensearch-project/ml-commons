/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.training;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.MLModel;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class TrainingITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_prediction_it";
        loadIrisData(irisIndexName);
    }

    public void testTrainingWithSearchInput_Async() throws InterruptedException {
        String taskId = trainKmeansWithIrisData(irisIndexName, true);
        assertNotNull(taskId);

        AtomicReference<String> modelId = new AtomicReference<>();
        waitUntil(() -> {
            String id = getTask(taskId).getModelId();
            modelId.set(id);
            return id != null;
        }, 10, TimeUnit.SECONDS);
        MLModel model = getModel(modelId.get());
        assertNotNull(model);
    }

    public void testTrainingWithSearchInput_Sync() {
        String modelId = trainKmeansWithIrisData(irisIndexName, false);
        assertNotNull(modelId);
        MLModel model = getModel(modelId);
        assertNotNull(model);
    }

    public void testTrainingWithoutDataset() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), null, false);
    }

    public void testTrainingWithEmptyDataset() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), emptySearchInputDataset, false);
    }
}
