/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.utils.TestData.IRIS_DATA_SIZE;
import static org.opensearch.ml.utils.TestData.TIME_FIELD;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.DoubleValue;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.utils.TestData;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class PredictionITTests extends MLCommonsIntegTestCase {
    private String irisIndexName;
    private String kMeansModelId;
    private String batchRcfModelId;
    private String fitRcfModelId;
    private String linearRegressionModelId;
    private String logisticRegressionModelId;
    private int batchRcfDataSize = 100;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        irisIndexName = "iris_data_for_prediction_it";
        loadIrisData(irisIndexName);

        kMeansModelId = trainKmeansWithIrisData(irisIndexName, false);
        MLModel kMeansModel = getModel(kMeansModelId);
        assertNotNull(kMeansModel);

        batchRcfModelId = trainBatchRCFWithDataFrame(500, false);
        fitRcfModelId = trainFitRCFWithDataFrame(500, false);
        linearRegressionModelId = trainLinearRegressionWithDataFrame(100, false);
        logisticRegressionModelId = trainLogisticRegressionWithIrisData(irisIndexName, false);
        MLModel batchRcfModel = getModel(batchRcfModelId);
        assertNotNull(batchRcfModel);
    }

    public void testPredictionWithSearchInput_KMeans() {
        MLInputDataset inputDataset = new SearchQueryInputDataset(List.of(irisIndexName), irisDataQuery());
        predictAndVerify(kMeansModelId, inputDataset, FunctionName.KMEANS, null, IRIS_DATA_SIZE);
    }

    public void testPredictionWithDataInput_KMeans() {
        MLInputDataset inputDataset = new DataFrameInputDataset(irisDataFrame());
        predictAndVerify(kMeansModelId, inputDataset, FunctionName.KMEANS, null, IRIS_DATA_SIZE);
    }

    public void testPredictionWithoutDataset_KMeans() {
        exceptionRule.expect(ActionRequestValidationException.class);
        exceptionRule.expectMessage("input data can't be null");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(kMeansModelId, mlInput, null);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    public void testPredictionWithEmptyDataset_KMeans() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No document found");
        MLInputDataset emptySearchInputDataset = emptyQueryInputDataSet(irisIndexName);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(emptySearchInputDataset).build();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(kMeansModelId, mlInput, null);
        ActionFuture<MLTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        predictionFuture.actionGet();
    }

    public void testPredictionWithSearchInput_LogisticRegression() {
        MLInputDataset inputDataset = new SearchQueryInputDataset(List.of(irisIndexName), irisDataQueryPredictLogisticRegression());
        predictAndVerify(logisticRegressionModelId, inputDataset, FunctionName.LOGISTIC_REGRESSION, null, IRIS_DATA_SIZE);
    }

    public void testPredictionWithDataFrame_BatchRCF() {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(batchRcfDataSize));
        predictAndVerify(batchRcfModelId, inputDataset, FunctionName.BATCH_RCF, null, batchRcfDataSize);
    }

    public void testPredictionWithDataFrame_FitRCF() {
        MLInputDataset inputDataset = new DataFrameInputDataset(TestData.constructTestDataFrame(batchRcfDataSize, true));
        DataFrame dataFrame = predictAndVerify(
            fitRcfModelId,
            inputDataset,
            FunctionName.FIT_RCF,
            FitRCFParams.builder().timeField(TIME_FIELD).build(),
            batchRcfDataSize
        );
    }

    public void testPredictionWithDataFrame_LinearRegression() {
        int size = 1;
        int feet = 20;
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("feet", ColumnType.DOUBLE) };
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new ColumnValue[] { new DoubleValue(feet) }));
        DataFrame inputDataFrame = new DefaultDataFrame(columnMetas, rows);
        MLInputDataset inputDataset = new DataFrameInputDataset(inputDataFrame);
        DataFrame dataFrame = predictAndVerify(
            linearRegressionModelId,
            inputDataset,
            FunctionName.LINEAR_REGRESSION,
            getLinearRegressionParams(),
            size
        );
        ColumnValue value = dataFrame.getRow(0).getValue(0);
        assertNotNull(value);
    }
}
