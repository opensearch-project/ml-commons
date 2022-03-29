/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.training;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
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

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testTrainingWithSearchInput_Async_KMenas() throws InterruptedException {
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

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testTrainingWithSearchInput_Sync_KMenas() {
        String modelId = trainKmeansWithIrisData(irisIndexName, false);
        assertNotNull(modelId);
        MLModel model = getModel(modelId);
        assertNotNull(model);
    }

    public void testTrainingWithDataFrame_Async_BatchRCF() throws InterruptedException {
        String taskId = trainBatchRCFWithDataFrame(500, true);
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

    public void testTrainingWithDataFrame_Sync_BatchRCF() {
        String modelId = trainBatchRCFWithDataFrame(500, false);
        assertNotNull(modelId);
        MLModel model = getModel(modelId);
        assertNotNull(model);
    }

    public void testTrainingWithoutDataset_KMenas() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), null, false);
    }

    public void testTrainingWithEmptyDataset_KMenas() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        trainModel(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), emptySearchInputDataset, false);
    }
}
