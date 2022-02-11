/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.rcf;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.parameter.Model;
import org.opensearch.ml.common.parameter.RCFParams;

import java.util.concurrent.ThreadLocalRandom;

public class FixedInTimeRandomCutForestTest {

    private RCFParams parameters;
    private FixedInTimeRandomCutForest forest;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;
    private int dataSize = 500;

    @Before
    public void setUp() {
        parameters = RCFParams.builder()
                .numberOfTrees(10)
                .shingleSize(8)
                .sampleSize(100)
                .timeField("timestamp")
                .build();

        forest = new FixedInTimeRandomCutForest(parameters);
        trainDataFrame = constructRCFDataFrame(false);
        predictionDataFrame = constructRCFDataFrame(true);
    }

    @Test
    public void predict() {
        Model model = forest.train(trainDataFrame);
        MLPredictionOutput output = (MLPredictionOutput) forest.predict(predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(dataSize, predictions.size());
        int anomalyCount = 0;
        for (int i = 0 ;i<dataSize; i++) {
            if (i % 100 == 0) {
                System.out.println(predictions.getRow(i).getValue(1).doubleValue());
                if (predictions.getRow(i).getValue(1).doubleValue() > 0.01) {
                    anomalyCount++;
                }
            } else {
                Assert.assertEquals(0.0, predictions.getRow(i).getValue(1).doubleValue(), 0.1);
            }
        }
        Assert.assertTrue(anomalyCount > 3);// total anomalies 5
    }

    @Test
    public void train() {
        Model model = forest.train(trainDataFrame);
        Assert.assertEquals(FunctionName.FIT_RCF.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    private DataFrame constructRCFDataFrame(boolean predict) {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("timestamp", ColumnType.LONG), new ColumnMeta("value", ColumnType.INTEGER)};
        DataFrame dataFrame = new DefaultDataFrame(columnMetas);
        long startTime = 1643677200000l;
        for (int i = 0; i < dataSize; i++) {
            long time = startTime + i * 1000 * 60;//1 minute interval
            if (predict && i % 100 == 0) {
                dataFrame.appendRow(new Object[]{time, ThreadLocalRandom.current().nextInt(100, 1000)});
            } else {
                dataFrame.appendRow(new Object[]{time, ThreadLocalRandom.current().nextInt(1, 10)});
            }
        }
        return dataFrame;
    }
}
