/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;
import static org.opensearch.ml.engine.helper.MLTestHelper.constructTestDataFrame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockedStatic;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

// TODO: refactor MLEngineClassLoader's static functions to avoid mockStatic
public class MLEngineTest extends MLStaticMockBase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLEngine mlEngine;

    @Before
    public void setUp() {
        Encryptor encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + UUID.randomUUID()), encryptor);
    }

    @Test
    public void testPrebuiltModelPath() {
        String modelName = "huggingface/sentence-transformers/msmarco-distilbert-base-tas-b";
        String version = "1.0.1";
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        String prebuiltModelPath = mlEngine.getPrebuiltModelPath(modelName, version, modelFormat);
        String prebuiltModelConfigPath = mlEngine.getPrebuiltModelConfigPath(modelName, version, modelFormat);
        assertEquals(
            "https://artifacts.opensearch.org/models/ml-models/huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.1/torch_script/sentence-transformers_msmarco-distilbert-base-tas-b-1.0.1-torch_script.zip",
            prebuiltModelPath
        );
        assertEquals(
            "https://artifacts.opensearch.org/models/ml-models/huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.1/torch_script/config.json",
            prebuiltModelConfigPath
        );
    }

    @Test
    public void predictKMeans() {
        MLModel model = trainKMeansModel();
        DataFrame predictionDataFrame = constructTestDataFrame(10);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput) mlEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictLinearRegression() {
        MLModel model = trainLinearRegressionModel();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build();
        MLPredictionOutput output = (MLPredictionOutput) mlEngine.predict(mlInput, model);
        DataFrame predictions = output.getPredictionResult();
        assertEquals(2, predictions.size());
    }

    @Test
    public void deployLinearRegressionModel() {
        MLModel model = trainLinearRegressionModel();
        Predictable predictor = mlEngine.deploy(model, null);
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        MLPredictionOutput output = (MLPredictionOutput) predictor
            .predict(MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build());
        DataFrame predictions = output.getPredictionResult();
        assertEquals(2, predictions.size());
    }

    @Test
    public void deployLinearRegressionModel_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model not deployed");
        Predictable predictor = new LinearRegression();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(predictionDataFrame).build();
        predictor.predict(MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build());
    }

    @Test
    public void trainKMeans() {
        MLModel model = trainKMeansModel();
        assertEquals(FunctionName.KMEANS.name(), model.getName());
        assertEquals("1.0.0", model.getVersion());
        assertNotNull(model.getContent());
    }

    @Test
    public void trainLinearRegression() {
        MLModel model = trainLinearRegressionModel();
        assertEquals(FunctionName.LINEAR_REGRESSION.name(), model.getName());
        assertEquals("1.0.0", model.getVersion());
        assertNotNull(model.getContent());
    }

    @Test
    public void train_NullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should not be null");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            mlEngine.train(null);
        }
    }

    @Test
    public void train_NullInputDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data set should not be null");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            mlEngine.train(MLInput.builder().algorithm(algoName).build());
        }
    }

    @Test
    public void train_NullDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            DataFrame dataFrame = new DefaultDataFrame(new ColumnMeta[0]);
            mlEngine.train(MLInput.builder().inputDataset(new DataFrameInputDataset(dataFrame)).algorithm(algoName).build());
        }
    }

    @Test
    public void train_EmptyDataFrame() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input data frame should not be null or empty");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(0)).build();
            mlEngine.train(MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build());
        }
    }

    @Test
    public void train_UnsupportedAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: LINEAR_REGRESSION");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(10)).build();
            mlEngine.train(MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build());
        }
    }

    @Test
    public void predictNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should not be null");
        mlEngine.predict(null, null);
    }

    @Test
    public void predictWithoutAlgoName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructTestDataFrame(10)).build();
        Input mlInput = MLInput.builder().inputDataset(inputDataset).build();
        mlEngine.predict(mlInput, null);
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructLinearRegressionPredictionDataFrame()).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(inputDataset).build();
        mlEngine.predict(mlInput, null);
    }

    @Test
    public void predictUnsupportedAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: LINEAR_REGRESSION");
        FunctionName algoName = FunctionName.LINEAR_REGRESSION;
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            loader.when(() -> MLEngineClassLoader.initInstance(algoName, null, MLAlgoParams.class)).thenReturn(null);
            MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(constructLinearRegressionPredictionDataFrame()).build();
            Input mlInput = MLInput.builder().algorithm(algoName).inputDataset(inputDataset).build();
            mlEngine.predict(mlInput, null);
        }
    }

    @Test
    public void trainAndPredictWithKmeans() {
        int dataSize = 100;
        MLAlgoParams parameters = KMeansParams.builder().build();
        DataFrame dataFrame = constructTestDataFrame(dataSize);
        MLInputDataset inputData = new DataFrameInputDataset(dataFrame);
        Input input = new MLInput(FunctionName.KMEANS, parameters, inputData);
        MLPredictionOutput output = (MLPredictionOutput) mlEngine.trainAndPredict(input);
        assertEquals(dataSize, output.getPredictionResult().size());
    }

    @Test
    public void trainAndPredictWithInvalidInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input should be MLInput");
        Input input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0));
        mlEngine.trainAndPredict(input);
    }

    @Test
    public void executeLocalSampleCalculator() throws Exception {
        Input input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0));
        ActionListener<Output> listener = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            assertEquals(3.0, output.getResult(), 1e-5);
        }, e -> { fail("Test failed"); });
        mlEngine.execute(input, listener);
    }

    @Test
    public void executeWithInvalidInput() throws Exception {
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
        ActionListener<Output> listener = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            assertEquals(3.0, output.getResult(), 1e-5);
        }, e -> { fail("Test failed"); });
        mlEngine.execute(input, listener);
    }

    private MLModel trainKMeansModel() {
        KMeansParams parameters = KMeansParams
            .builder()
            .centroids(2)
            .iterations(10)
            .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
            .build();
        DataFrame trainDataFrame = constructTestDataFrame(100);
        MLInputDataset inputDataset = DataFrameInputDataset.builder().dataFrame(trainDataFrame).build();
        Input mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).parameters(parameters).inputDataset(inputDataset).build();
        return mlEngine.train(mlInput);
    }

    private MLModel trainLinearRegressionModel() {
        LinearRegressionParams parameters = LinearRegressionParams
            .builder()
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
        Input mlInput = MLInput
            .builder()
            .algorithm(FunctionName.LINEAR_REGRESSION)
            .parameters(parameters)
            .inputDataset(inputDataset)
            .build();

        return mlEngine.train(mlInput);
    }

    @Test
    public void getRegisterModelPath_ReturnsCorrectPath() {
        String modelId = "testModel";
        String modelName = "myModel";
        String version = "1.0";

        Path basePath = mlEngine.getMlCachePath().getParent(); // Get the actual base path used in the setup
        Path expectedPath = basePath
            .resolve("ml_cache")
            .resolve("models_cache")
            .resolve(MLEngine.REGISTER_MODEL_FOLDER)
            .resolve(modelId)
            .resolve(version)
            .resolve(modelName);
        Path actualPath = mlEngine.getRegisterModelPath(modelId, modelName, version);

        assertEquals(expectedPath.toString(), actualPath.toString());
    }

    @Test
    public void getDeployModelPath_ReturnsCorrectPath() {
        String modelId = "deployedModel";

        // Use the actual base path from the mlEngine instance
        Path basePath = mlEngine.getMlCachePath().getParent();
        Path expectedPath = basePath.resolve("ml_cache").resolve("models_cache").resolve(MLEngine.DEPLOY_MODEL_FOLDER).resolve(modelId);
        Path actualPath = mlEngine.getDeployModelPath(modelId);

        assertEquals(expectedPath.toString(), actualPath.toString());
    }

    @Test
    public void getModelCachePath_ReturnsCorrectPath() {
        String modelId = "cachedModel";
        String modelName = "modelName";
        String version = "1.2";

        // Use the actual base path from the mlEngine instance
        Path basePath = mlEngine.getMlCachePath().getParent();
        Path expectedPath = basePath
            .resolve("ml_cache")
            .resolve("models_cache")
            .resolve("models")
            .resolve(modelId)
            .resolve(version)
            .resolve(modelName);
        Path actualPath = mlEngine.getModelCachePath(modelId, modelName, version);

        assertEquals(expectedPath.toString(), actualPath.toString());
    }

    @Test
    public void testMLEngineInitialization() {
        Path testPath = Path.of("/tmp/test" + UUID.randomUUID());
        mlEngine = new MLEngine(testPath, new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w="));

        Path expectedMlCachePath = testPath.resolve("ml_cache");
        Path expectedMlConfigPath = expectedMlCachePath.resolve("config");

        assertEquals(expectedMlCachePath, mlEngine.getMlCachePath());
        assertEquals(expectedMlConfigPath, mlEngine.getMlConfigPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTrainWithInvalidInput() {
        mlEngine.train(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPredictWithInvalidInput() {
        mlEngine.predict(null, null);
    }

    @Test
    public void testEncryptMethod() {
        String testString = "testString";
        String encryptedString = mlEngine.encrypt(testString);
        assertNotNull(encryptedString);
        assertNotEquals(testString, encryptedString);
    }

}
