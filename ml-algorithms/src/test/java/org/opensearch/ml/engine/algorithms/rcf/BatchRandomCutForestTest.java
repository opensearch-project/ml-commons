/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.rcf;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLPredictionOutput;

import java.util.concurrent.ThreadLocalRandom;

public class BatchRandomCutForestTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private BatchRCFParams parameters;
    private BatchRandomCutForest forest;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataSet;
    private int dataSize = 500;

    @Before
    public void setUp() {
        parameters = BatchRCFParams.builder()
                .trainingDataSize(dataSize)
                .numberOfTrees(10)
                .sampleSize(100)
                .anomalyScoreThreshold(0.01)
                .trainingDataSize(100)
                .outputAfter(100)
                .build();
        forest = new BatchRandomCutForest(parameters);
        trainDataFrame = constructRCFDataFrame(false);
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        predictionDataFrame = constructRCFDataFrame(true);
        predictionDataFrameInputDataSet = new DataFrameInputDataset(predictionDataFrame);
    }

    @Test
    public void constructorWithNullParams() {
        forest = new BatchRandomCutForest(null);
        predict();
    }

    @Test
    public void predict() {
        MLModel model = forest.train(trainDataFrameInputDataSet);
        MLPredictionOutput output = (MLPredictionOutput) forest.predict(predictionDataFrameInputDataSet, model);
        verifyPredictionResult(output);
    }

    @Test
    public void predictWithNullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for batch RCF prediction");
        MLPredictionOutput output = (MLPredictionOutput) forest.predict(predictionDataFrameInputDataSet, null);
        verifyPredictionResult(output);
    }

    @Test
    public void train() {
        MLModel model = forest.train(trainDataFrameInputDataSet);
        Assert.assertEquals(FunctionName.BATCH_RCF.name(), model.getName());
        Assert.assertEquals(1, model.getVersion().intValue());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainAndPredict() {
        MLPredictionOutput output = (MLPredictionOutput) forest.trainAndPredict(trainDataFrameInputDataSet);
        verifyPredictionResult(output);
    }

    private void verifyPredictionResult(MLPredictionOutput output) {
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(dataSize, predictions.size());
        int anomalyCount = 0;
        for (int i = 0 ;i<dataSize; i++) {
            Row row = predictions.getRow(i);
            if (i % 100 == 0) {
                if (row.getValue(0).doubleValue() > 0.01) {
                    anomalyCount++;
                }
            }
        }
        Assert.assertTrue("Fewer anomaly detected: " + anomalyCount, anomalyCount > 1);// total anomalies 5
    }

    private DataFrame constructRCFDataFrame(boolean predict) {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("value", ColumnType.INTEGER)};
        DataFrame dataFrame = new DefaultDataFrame(columnMetas);
        for (int i = 0; i < dataSize; i++) {
            if (predict && i % 100 == 0) {
                dataFrame.appendRow(new Object[]{ThreadLocalRandom.current().nextInt(100, 1000)});
            } else {
                dataFrame.appendRow(new Object[]{ThreadLocalRandom.current().nextInt(1, 10)});
            }
        }
        return dataFrame;
    }
}
