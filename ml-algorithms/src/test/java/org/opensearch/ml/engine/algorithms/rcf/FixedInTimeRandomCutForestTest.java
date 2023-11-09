/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.rcf;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;

public class FixedInTimeRandomCutForestTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private FitRCFParams parameters;
    private FixedInTimeRandomCutForest forest;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private MLInput trainDataFrameInput;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataSet;
    private MLInput predictionDataFrameInput;
    private int dataSize = 500;

    @Before
    public void setUp() {
        parameters = FitRCFParams.builder().numberOfTrees(10).shingleSize(8).sampleSize(100).timeField("timestamp").build();

        forest = new FixedInTimeRandomCutForest(parameters);
        trainDataFrame = constructRCFDataFrame(false);
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        trainDataFrameInput = MLInput.builder().algorithm(FunctionName.FIT_RCF).inputDataset(trainDataFrameInputDataSet).build();
        predictionDataFrame = constructRCFDataFrame(true);
        predictionDataFrameInputDataSet = new DataFrameInputDataset(predictionDataFrame);
        predictionDataFrameInput = MLInput.builder().algorithm(FunctionName.FIT_RCF).inputDataset(predictionDataFrameInputDataSet).build();
    }

    @Test
    public void predict() {
        MLModel model = forest.train(trainDataFrameInput);
        MLPredictionOutput output = (MLPredictionOutput) forest.predict(predictionDataFrameInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(dataSize, predictions.size());
        int anomalyCount = 0;
        for (int i = 0; i < dataSize; i++) {
            if (i % 100 == 0) {
                if (predictions.getRow(i).getValue(1).doubleValue() > 0.01) {
                    anomalyCount++;
                }
            }
        }
        Assert.assertTrue("Fewer anomaly detected: " + anomalyCount, anomalyCount > 1);// total anomalies 5
    }

    @Test
    public void train() {
        MLModel model = forest.train(trainDataFrameInput);
        Assert.assertEquals(FunctionName.FIT_RCF.name(), model.getName());
        Assert.assertEquals("1.0.0", model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainWithStringColumn() {
        exceptionRule.expect(MLValidationException.class);
        exceptionRule.expectMessage("Failed to parse timestamp 1643677200000");
        trainDataFrame = constructRCFDataFrameStringTimestamp(false);
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        trainDataFrameInput = MLInput.builder().algorithm(FunctionName.FIT_RCF).inputDataset(trainDataFrameInputDataSet).build();

        MLModel model = forest.train(trainDataFrameInput);
        Assert.assertEquals(FunctionName.FIT_RCF.name(), model.getName());
        Assert.assertEquals("1.0.0", model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainWithMLAlgoParams() {
        FitRCFParams params = FitRCFParams.builder().build();
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.FIT_RCF)
            .inputDataset(trainDataFrameInputDataSet)
            .parameters(params)
            .build();
        MLModel model = forest.train(mlInput);
        Assert.assertEquals(FunctionName.FIT_RCF.name(), model.getName());
        Assert.assertEquals("1.0.0", model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void testTrainAndPredict() {
        MLOutput mlOutput = forest.trainAndPredict(trainDataFrameInput);
        Assert.assertTrue(mlOutput instanceof MLPredictionOutput);
        Assert.assertEquals(((MLPredictionOutput) mlOutput).getPredictionResult().size(), 500);
    }

    private DataFrame constructRCFDataFrame(boolean predict) {
        ColumnMeta[] columnMetas = new ColumnMeta[] {
            new ColumnMeta("timestamp", ColumnType.LONG),
            new ColumnMeta("value", ColumnType.INTEGER) };
        DataFrame dataFrame = new DefaultDataFrame(columnMetas);
        long startTime = 1643677200000l;
        for (int i = 0; i < dataSize; i++) {
            long time = startTime + i * 1000 * 60;// 1 minute interval
            if (predict && i % 100 == 0) {
                dataFrame.appendRow(new Object[] { time, ThreadLocalRandom.current().nextInt(100, 1000) });
            } else {
                dataFrame.appendRow(new Object[] { time, ThreadLocalRandom.current().nextInt(1, 10) });
            }
        }
        return dataFrame;
    }

    private DataFrame constructRCFDataFrameStringTimestamp(boolean predict) {
        ColumnMeta[] columnMetas = new ColumnMeta[] {
            new ColumnMeta("timestamp", ColumnType.STRING),
            new ColumnMeta("value", ColumnType.INTEGER) };
        DataFrame dataFrame = new DefaultDataFrame(columnMetas);
        long startTime = 1643677200000l;
        for (int i = 0; i < dataSize; i++) {
            long time = startTime + i * 1000 * 60;// 1 minute interval
            if (predict && i % 100 == 0) {
                dataFrame.appendRow(new Object[] { String.valueOf(time), ThreadLocalRandom.current().nextInt(100, 1000) });
            } else {
                dataFrame.appendRow(new Object[] { String.valueOf(time), ThreadLocalRandom.current().nextInt(1, 10) });
            }
        }
        return dataFrame;
    }
}
