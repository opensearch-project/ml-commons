/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.LinearRegressionParams;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.Model;
import org.opensearch.ml.common.parameter.MLPredictionOutput;


import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;

public class MLEngineTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void predictKMeans() {
        Model model = trainKMeansModel();
        DataFrame predictionDataFrame = constructKMeansDataFrame(10);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictLinearRegression() {
        Model model = trainLinearRegressionModel();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void trainKMeans() {
        Model model = trainKMeansModel();
        Assert.assertEquals(FunctionName.KMEANS.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainLinearRegression() {
        Model model = trainLinearRegressionModel();
        Assert.assertEquals(FunctionName.LINEAR_REGRESSION.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void train_NullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should not be null");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLEngine.train(null);
        }
    }

    @Test
    public void train_NullDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLEngine.train(MLInput.builder().algorithm(algoName).build());
        }
    }

    @Test
    public void train_EmptyDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructKMeansDataFrame(0)).build();
            MLEngine.train(MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build());
        }
    }

    @Test
    public void train_UnsupportedAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: LINEAR_REGRESSION");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructKMeansDataFrame(10)).build();
            MLEngine.train(MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build());
        }
    }

    @Test
    public void predictNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should not be null");
        MLEngine.predict(null, null);
    }

    @Test
    public void predictWithoutAlgoName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructKMeansDataFrame(10)).build();
        Input mlInput = MLInput.builder().inputDataset(inputDataset).build();
        MLEngine.predict(mlInput, null);
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructLinearRegressionPredictionDataFrame()).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build();
        MLEngine.predict(mlInput, null);
    }

    @Test
    public void predictUnsupportedAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: LINEAR_REGRESSION");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructLinearRegressionPredictionDataFrame()).build();
            Input mlInput = MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build();
            MLEngine.predict(mlInput, null);
        }
    }

    private Model trainKMeansModel() {
        KMeansParams parameters = KMeansParams.builder()
                .centroids(2)
                .iterations(10)
                .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
                .build();
        DataFrame trainDataFrame = constructKMeansDataFrame(100);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(trainDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).parameters(parameters).inputDataset(inputDataset).build();
        return MLEngine.train(mlInput);
    }

    private Model trainLinearRegressionModel() {
        LinearRegressionParams parameters = LinearRegressionParams.builder()
                .objectiveType(LinearRegressionParams.ObjectiveType.SQUARED_LOSS)
                .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .learningRate(0.01)
                .epsilon(1e-6)
                .beta1(0.9)
                .beta2(0.99)
                .target("price")
                .build();
        DataFrame trainDataFrame = constructLinearRegressionTrainDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(trainDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).parameters(parameters).inputDataset(inputDataset).build();

        return MLEngine.train(mlInput);
    }
}