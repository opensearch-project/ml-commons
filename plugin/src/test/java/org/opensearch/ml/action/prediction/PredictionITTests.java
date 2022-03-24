/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.utils.TestData.IRIS_DATA_SIZE;

import org.apache.lucene.util.LuceneTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.utils.TestData;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableList;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class PredictionITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;
    private String kMeansModelId;
    private String batchRcfModelId;
    private int batchRcfDataSize = 100;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_prediction_it";
        loadIrisData(irisIndexName);

        // TODO: open these lines when this bug fix merged https://github.com/oracle/tribuo/issues/223
        // modelId = trainKmeansWithIrisData(irisIndexName, false);
        // MLModel kMeansModel = getModel(kMeansModelId);
        // assertNotNull(kMeansModel);

        batchRcfModelId = trainBatchRCFWithDataFrame(500, false);
        MLModel batchRcfModel = getModel(batchRcfModelId);
        assertNotNull(batchRcfModel);
    }

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testPredictionWithSearchInput_KMeans() {
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQuery());
        predictAndVerify(kMeansModelId, inputDataset, FunctionName.KMEANS, IRIS_DATA_SIZE);
    }

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testPredictionWithDataInput_KMeans() {
        MLInputDataset inputDataset = new DataFrameInputDataset(irisDataFrame());
        predictAndVerify(kMeansModelId, inputDataset, FunctionName.KMEANS, IRIS_DATA_SIZE);
    }

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testPredictionWithoutDataset_KMeans() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(kMeansModelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    @LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/oracle/tribuo/issues/223")
    public void testPredictionWithEmptyDataset_KMeans() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(emptySearchInputDataset).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(kMeansModelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    public void testPredictionWithDataFrame_BatchRCF() {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(batchRcfDataSize));
        predictAndVerify(batchRcfModelId, inputDataset, FunctionName.BATCH_RCF, batchRcfDataSize);
    }
}
