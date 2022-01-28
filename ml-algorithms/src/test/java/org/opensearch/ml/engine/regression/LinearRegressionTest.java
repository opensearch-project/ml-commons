/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.regression;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.LinearRegressionParams;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;

import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;


public class LinearRegressionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private LinearRegressionParams parameters;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;

    @Before
    public void setUp() {
        parameters = LinearRegressionParams.builder()
                .objectiveType(LinearRegressionParams.ObjectiveType.HUBER)
                .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .learningRate(0.9)
                .epsilon(1e-6)
                .beta1(0.9)
                .beta2(0.99)
                .build();
        trainDataFrame = constructLinearRegressionTrainDataFrame();
        predictionDataFrame = constructLinearRegressionPredictionDataFrame();
    }

    @Test
    public void predict() {
        parameters.setTarget("price");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
        MLPredictionOutput output = (MLPredictionOutput)regression.predict(predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        parameters.setTarget( "price");
        LinearRegression regression = new LinearRegression(parameters);
        regression.predict(predictionDataFrame, null);
    }

    @Test
    public void train() {
        parameters.setTarget( "price");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
        Assert.assertEquals(FunctionName.LINEAR_REGRESSION.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainExceptionWithoutTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Empty target when generating dataset from data frame.");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
    }

    @Test
    public void trainExceptionUnmatchedTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No matched target when generating dataset from data frame.");
        parameters.setTarget("not found");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
    }

}