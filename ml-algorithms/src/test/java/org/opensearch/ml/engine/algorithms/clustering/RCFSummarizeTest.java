/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.clustering;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.input.parameter.clustering.RCFSummarizeParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.Model;

import static org.opensearch.ml.engine.helper.MLTestHelper.constructTestDataFrame;


public class RCFSummarizeTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RCFSummarizeParams parameters;
    private RCFSummarize rcfSummarize;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;
    private int trainSize = 100;
    private int predictionSize = 10;

    @Before
    public void setUp() {
        parameters = RCFSummarizeParams.builder()
            .distanceType(RCFSummarizeParams.DistanceType.L2)
            .maxK(2)
            .initialK(20)
            .phase1Reassign(true)
            .parallel(false)
            .build();

        rcfSummarize = new RCFSummarize(parameters);

        constructRCFSummarizePredictionDataFrame();
        constructRCFSummarizeTrainDataFrame();
    }

    @Test
    public void predictWithTrivalModelExpectBoNorminalOutput() {
        Model model = rcfSummarize.train(trainDataFrame);
        MLPredictionOutput output = (MLPredictionOutput) rcfSummarize.predict(predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(predictionSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void trainAndPredictWithRegularInputExpectNotNullOutput() {
        RCFSummarizeParams parameters = RCFSummarizeParams.builder()
                .distanceType(RCFSummarizeParams.DistanceType.L1)
                .maxK(2).initialK(10)
                .build();
        RCFSummarize rcfSummarize = new RCFSummarize(parameters);
        MLPredictionOutput output = (MLPredictionOutput) rcfSummarize.trainAndPredict(trainDataFrame);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));

        parameters = parameters.toBuilder().distanceType(RCFSummarizeParams.DistanceType.L2).build();
        rcfSummarize = new RCFSummarize(parameters);
        output = (MLPredictionOutput) rcfSummarize.trainAndPredict(trainDataFrame);
        predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());

        parameters = parameters.toBuilder().distanceType(RCFSummarizeParams.DistanceType.LInfinity).build();
        rcfSummarize = new RCFSummarize(parameters);
        output = (MLPredictionOutput) rcfSummarize.trainAndPredict(trainDataFrame);
        predictions = output.getPredictionResult();
        Assert.assertEquals(trainSize, predictions.size());
    }

    @Test
    public void constructorWithNegtiveMaxK() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max K should be positive");
        new RCFSummarize(RCFSummarizeParams.builder().maxK(-1).build());
    }

    @Test
    public void constructorWithNegtiveInitialK() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("initial K should be positive");
        new RCFSummarize(RCFSummarizeParams.builder().initialK(-1).build());
    }

    @Test
    public void predictWithNullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for RCFSummarize prediction.");
        rcfSummarize.predict(predictionDataFrame, null);
    }

    @Test
    public void trainWithRegularInputExpectNotNullOutput() {
        Model model = rcfSummarize.train(trainDataFrame);
        Assert.assertEquals(FunctionName.RCF_SUMMARIZE.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    private void constructRCFSummarizePredictionDataFrame() {
        predictionDataFrame = constructTestDataFrame(predictionSize);
    }

    private void constructRCFSummarizeTrainDataFrame() {
        trainDataFrame = constructTestDataFrame(trainSize);
    }
}
