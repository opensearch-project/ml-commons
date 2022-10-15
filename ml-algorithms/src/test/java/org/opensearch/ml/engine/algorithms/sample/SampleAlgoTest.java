/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

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
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.sample.SampleAlgoOutput;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;

public class SampleAlgoTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLAlgoParams parameters;
    private SampleAlgo sampleAlgo;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataSet;

    @Before
    public void setUp() {
        parameters = SampleAlgoParams.builder().sampleParam(2).build();
        sampleAlgo = new SampleAlgo(parameters);
        trainDataFrame = constructDataFrame(10);
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        predictionDataFrame = constructDataFrame(3);
        predictionDataFrameInputDataSet = new DataFrameInputDataset(predictionDataFrame);
    }

    @Test
    public void predict() {
        MLModel model = sampleAlgo.train(trainDataFrameInputDataSet);
        SampleAlgoOutput output = (SampleAlgoOutput)sampleAlgo.predict(predictionDataFrameInputDataSet, model);
        Assert.assertEquals(3.0, output.getSampleResult().doubleValue(), 1e-5);
    }

    @Test
    public void predictWithNullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for sample algo");
        sampleAlgo.predict(predictionDataFrameInputDataSet, null);
    }

    private DataFrame constructDataFrame(int dataSize) {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("value", ColumnType.INTEGER)};
        DataFrame dataFrame = new DefaultDataFrame(columnMetas);
        for (int i = 0; i < dataSize; i++) {
            dataFrame.appendRow(new Object[]{i});
        }
        return dataFrame;
    }
}
