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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams;
import org.opensearch.ml.common.output.MLPredictionOutput;

import static org.opensearch.ml.engine.helper.LogisticRegressionHelper.constructLogisticRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LogisticRegressionHelper.constructLogisticRegressionTrainDataFrame;

public class LogisticRegressionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private LogisticRegressionParams parameters;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataSet;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataSet;

    @Before
    public void setUp() {
        parameters = LogisticRegressionParams.builder()
                .objectiveType(LogisticRegressionParams.ObjectiveType.LOGMULTICLASS)
                .optimizerType(LogisticRegressionParams.OptimizerType.ADA_GRAD)
                .learningRate(0.9)
                .epsilon(1e-6)
                .target("class")
                .build();
        trainDataFrame = constructLogisticRegressionTrainDataFrame();
        trainDataFrameInputDataSet = new DataFrameInputDataset(trainDataFrame);
        predictionDataFrame = constructLogisticRegressionPredictionDataFrame();
        predictionDataFrameInputDataSet = new DataFrameInputDataset(predictionDataFrame);
    }

    @Test
    public void train() {
        trainAndVerify(parameters);
    }

    @Test
    public void train_linear_decay_sgd() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.LINEAR_DECAY_SGD);
        trainAndVerify(parameters);
    }

    @Test
    public void train_sqrt_decay_sgd() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.SQRT_DECAY_SGD);
        trainAndVerify(parameters);
    }

    @Test
    public void train_sqrt_ada_delta() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.ADA_DELTA);
        trainAndVerify(parameters);
    }

    @Test
    public void train_adam() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.ADAM);
        trainAndVerify(parameters);
    }

    @Test
    public void train_rms_prop() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.SIMPLE_SGD);
        trainAndVerify(parameters);
    }

    @Test
    public void train_simple_sgd() {
        parameters.setOptimizerType(LogisticRegressionParams.OptimizerType.RMS_PROP);
        trainAndVerify(parameters);
    }

    @Test
    public void trainExceptionWithoutTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Empty target when generating dataset from data frame.");
        parameters.setTarget(null);
        LogisticRegression classification = new LogisticRegression(parameters);
        MLModel model = classification.train(trainDataFrameInputDataSet);
    }

    @Test
    public void predict() {
        LogisticRegression classification = new LogisticRegression(parameters);
        MLModel model = classification.train(trainDataFrameInputDataSet);
        MLPredictionOutput output = (MLPredictionOutput)classification.predict(predictionDataFrameInputDataSet, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for logistic regression prediction.");
        LogisticRegression classification = new LogisticRegression(parameters);
        classification.predict(predictionDataFrameInputDataSet, null);
    }

    @Test
    public void constructorNegativeLearnRate() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Learning rate should not be negative");
        new LogisticRegression(parameters.toBuilder().learningRate(-0.1).build());
    }

    @Test
    public void constructorNegativeEpsilon() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Epsilon should not be negative");
        new LogisticRegression(parameters.toBuilder().epsilon(-1.0).build());
    }

    @Test
    public void constructorNegativeEpochs() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Epochs should not be negative");
        new LogisticRegression(parameters.toBuilder().epochs(-1).build());
    }

    @Test
    public void constructorNegativeBatchSize() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("MiniBatchSize should not be negative");
        new LogisticRegression(parameters.toBuilder().batchSize(-1).build());
    }

    private void trainAndVerify(LogisticRegressionParams params) {
        LogisticRegression classification = new LogisticRegression(params);
        MLModel model = classification.train(trainDataFrameInputDataSet);
        Assert.assertEquals(FunctionName.LOGISTIC_REGRESSION.name(), model.getName());
        Assert.assertEquals("1.0.0", model.getVersion());
        Assert.assertNotNull(model.getContent());
    }
}
