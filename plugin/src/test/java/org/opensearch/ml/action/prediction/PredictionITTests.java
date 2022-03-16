/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.utils.TestData.IRIS_DATA_SIZE;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLModel;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableList;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class PredictionITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;
    private String modelId;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_prediction_it";
        loadIrisData(irisIndexName);

        modelId = trainKmeansWithIrisData(irisIndexName, false);
        MLModel model = getModel(modelId);
        assertNotNull(model);
    }

    public void testPredictionWithSearchInput() {
        MLInputDataset inputDataset = new SearchQueryInputDataset(ImmutableList.of(irisIndexName), irisDataQuery());
        predictAndVerify(inputDataset);
    }

    public void testPredictionWithDataInput() {
        MLInputDataset inputDataset = new DataFrameInputDataset(irisDataFrame());
        predictAndVerify(inputDataset);
    }

    public void testPredictionWithoutDataset() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    public void testPredictionWithEmptyDataset() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(emptySearchInputDataset).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    private void predictAndVerify(MLInputDataset inputDataset) {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(modelId, mlInput);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        MLTaskResponse predictionResponse = predictionFuture.actionGet();
        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) predictionResponse.getOutput();
        DataFrame predictionResult = mlPredictionOutput.getPredictionResult();
        assertEquals(IRIS_DATA_SIZE, predictionResult.size());
    }
}
