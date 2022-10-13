/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.regression;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.output.MLPredictionOutput;

import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;


public class LinearRegressionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private LinearRegressionParams parameters;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataSet;

    @Before
    public void setUp() {
        parameters = LinearRegressionParams.builder()
                .objectiveType(LinearRegressionParams.ObjectiveType.HUBER)
                .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .learningRate(0.9)
                .epsilon(1e-6)
                .beta1(0.9)
                .beta2(0.99)
                .target("price")
                .build();
        trainDataFrame = constructLinearRegressionTrainDataFrame();
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        predictionDataFrameInputDataSet = new DataFrameInputDataset(predictionDataFrame);
    }

    @Test
    public void predict() {
        LinearRegression regression = new LinearRegression(parameters);
        MLModel model = regression.train(trainDataFrameInputDataSet);
        MLPredictionOutput output = (MLPredictionOutput)regression.predict(predictionDataFrameInputDataSet, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        LinearRegression regression = new LinearRegression(parameters);
        regression.predict(predictionDataFrameInputDataSet, null);
    }

    @Test
    public void train() {
        trainAndVerify(parameters);
    }

    @Test
    public void trainWithLinearDecaySGD() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.LINEAR_DECAY_SGD).build();
        trainAndVerify(newParams);
    }

    @Test
    public void trainWithSqrtDecaySGD() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.SQRT_DECAY_SGD).build();
        trainAndVerify(newParams);
    }

    @Test
    public void trainWithAdaGrad() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.ADA_GRAD).build();
        trainAndVerify(newParams);
    }

    @Test
    public void trainWithAdaDelta() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.ADA_DELTA).build();
        trainAndVerify(newParams);
    }

    @Test
    public void trainWithADAM() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .momentumType(LinearRegressionParams.MomentumType.NESTEROV)
                .objectiveType(LinearRegressionParams.ObjectiveType.ABSOLUTE_LOSS)
                .build();
        trainAndVerify(newParams);
    }

    @Test
    public void trainWithRmsProp() {
        LinearRegressionParams newParams = parameters.toBuilder().optimizerType(LinearRegressionParams.OptimizerType.RMS_PROP).build();
        trainAndVerify(newParams);
    }

    private void trainAndVerify(LinearRegressionParams params) {
        LinearRegression regression = new LinearRegression(params);
        MLModel model = regression.train(trainDataFrameInputDataSet);
        Assert.assertEquals(FunctionName.LINEAR_REGRESSION.name(), model.getName());
        Assert.assertEquals(1, model.getVersion().intValue());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainExceptionWithoutTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Empty target when generating dataset from data frame.");
        parameters.setTarget(null);
        LinearRegression regression = new LinearRegression(parameters);
        MLModel model = regression.train(trainDataFrameInputDataSet);
    }

    @Test
    public void trainExceptionUnmatchedTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No matched target when generating dataset from data frame.");
        parameters.setTarget("not found");
        LinearRegression regression = new LinearRegression(parameters);
        MLModel model = regression.train(trainDataFrameInputDataSet);
    }

    @Test
    public void constructorNegativeLearnRate() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Learning rate should not be negative");
        new LinearRegression(parameters.toBuilder().learningRate(-0.1).build());
    }

    @Test
    public void constructorNegativeMomentumFactor() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("MomentumFactor should not be negative");
        new LinearRegression(parameters.toBuilder().momentumFactor(-0.1).build());
    }

    @Test
    public void constructorNegativeEpsilon() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Epsilon should not be negative");
        new LinearRegression(parameters.toBuilder().epsilon(-1.0).build());
    }

    @Test
    public void constructorNegativeBeta1() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Beta1 should be in an open interval (0,1)");
        new LinearRegression(parameters.toBuilder().beta1(-0.1).build());
    }

    @Test
    public void constructorBigBeta1() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Beta1 should be in an open interval (0,1)");
        new LinearRegression(parameters.toBuilder().beta1(2.0).build());
    }

    @Test
    public void constructorNegativeBeta2() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Beta2 should be in an open interval (0,1)");
        new LinearRegression(parameters.toBuilder().beta2(-0.1).build());
    }

    @Test
    public void constructorBigBeta2() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Beta2 should be in an open interval (0,1)");
        new LinearRegression(parameters.toBuilder().beta2(2.0).build());
    }

    @Test
    public void constructorNegativeDecayRate() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("DecayRate should not be negative");
        new LinearRegression(parameters.toBuilder().decayRate(-0.1).build());
    }

    @Test
    public void constructorNegativeEpochs() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Epochs should not be negative");
        new LinearRegression(parameters.toBuilder().epochs(-1).build());
    }

    @Test
    public void constructorNegativeBatchSize() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("MiniBatchSize should not be negative");
        new LinearRegression(parameters.toBuilder().batchSize(-1).build());
    }
}
