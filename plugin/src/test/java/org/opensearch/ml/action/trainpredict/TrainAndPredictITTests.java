/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.trainpredict;

import static org.opensearch.ml.utils.TestData.IRIS_DATA_SIZE;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class TrainAndPredictITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_train_and_predict_it";
        loadIrisData(irisIndexName);
    }

    public void testTrainAndPredict() {
        MLPredictionOutput mlPredictionOutput = trainAndPredictKmeansWithIrisData(irisIndexName);
        assertNotNull(mlPredictionOutput);
        DataFrame predictionResult = mlPredictionOutput.getPredictionResult();
        assertNotNull(predictionResult);
        assertEquals(IRIS_DATA_SIZE, predictionResult.size());
    }

    public void testTrainAndPredictWithoutDataset() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        trainAndPredict(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), null);
    }

    public void testTrainAndPredictWithEmptyDataset() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        trainAndPredict(FunctionName.KMEANS, KMeansParams.builder().centroids(3).build(), emptySearchInputDataset);
    }
}
