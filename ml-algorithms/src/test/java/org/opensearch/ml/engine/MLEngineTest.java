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
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;

import java.io.IOException;
import java.util.Arrays;

import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;
import static org.opensearch.ml.engine.helper.MLTestHelper.constructTestDataFrame;

public class MLEngineTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void predictKMeans() {
        MLModel model = trainKMeansModel();
        DataFrame predictionDataFrame = constructTestDataFrame(10);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictLinearRegression() {
        MLModel model = trainLinearRegressionModel();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }


    @Test
    public void loadLinearRegressionModel() {
        MLModel model = trainLinearRegressionModel();
        Predictable predictor = MLEngine.load(model, null);
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        MLPredictionOutput output = (MLPredictionOutput)predictor.predict(inputDataset);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void loadLinearRegressionModel_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not loaded");
        Predictable predictor = new LinearRegression();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        predictor.predict(inputDataset);
    }

    @Test
    public void trainKMeans() {
        MLModel model = trainKMeansModel();
        Assert.assertEquals(FunctionName.KMEANS.name(), model.getName());
        Assert.assertEquals(1, model.getVersion().intValue());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainLinearRegression() {
        MLModel model = trainLinearRegressionModel();
        Assert.assertEquals(FunctionName.LINEAR_REGRESSION.name(), model.getName());
        Assert.assertEquals(1, model.getVersion().intValue());
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
    public void train_NullInputDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data set should not be null");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLEngine.train(MLInput.builder().algorithm(algoName).build());
        }
    }

    @Test
    public void train_NullDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            DataFrame dataFrame = new DefaultDataFrame(new ColumnMeta[0]);
            MLEngine.train(MLInput.builder().inputDataset(new DataFrameInputDataset(dataFrame)).algorithm(algoName).build());
        }
    }

    @Test
    public void train_EmptyDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = Mockito.mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(0)).build();
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
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(10)).build();
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
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(10)).build();
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

    @Test
    public void trainAndPredictWithKmeans() {
        int dataSize = 100;
        MLAlgoParams parameters = KMeansParams.builder().build();
        DataFrame dataFrame = constructTestDataFrame(dataSize);
        MLInputDataset inputData = new DataFrameInputDataset(dataFrame);
        Input input = new MLInput(FunctionName.KMEANS, parameters, inputData);
        MLPredictionOutput output = (MLPredictionOutput) MLEngine.trainAndPredict(input);
        Assert.assertEquals(dataSize, output.getPredictionResult().size());
    }

    @Test
    public void trainAndPredictWithInvalidInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should be MLInput");
        Input input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0));
        MLEngine.trainAndPredict(input);
    }

    @Test
    public void executeLocalSampleCalculator() {
        Input input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0));
        LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) MLEngine.execute(input);
        Assert.assertEquals(3.0, output.getResult(), 1e-5);
    }

    @Test
    public void executeWithInvalidInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Function name should not be null");
        Input input = new Input() {
            @Override
            public FunctionName getFunctionName() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput streamOutput) throws IOException {

            }

            @Override
            public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
                return null;
            }
        };
        MLEngine.execute(input);
    }


    private MLModel trainKMeansModel() {
        KMeansParams parameters = KMeansParams.builder()
                .centroids(2)
                .iterations(10)
                .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
                .build();
        DataFrame trainDataFrame = constructTestDataFrame(100);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(trainDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).parameters(parameters).inputDataset(inputDataset).build();
        return MLEngine.train(mlInput);
    }

    private MLModel trainLinearRegressionModel() {
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