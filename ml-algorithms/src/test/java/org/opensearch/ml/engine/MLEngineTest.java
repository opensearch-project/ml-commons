/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SDK_CLIENT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SETTINGS;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;
import static org.opensearch.ml.engine.helper.MLTestHelper.constructTestDataFrame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
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
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchModule;

import software.amazon.awssdk.utils.ImmutableMap;

// TODO: refactor MLEngineClassLoader's static functions to avoid mockStatic
public class MLEngineTest extends MLStaticMockBase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLEngine mlEngine;

    private ActionListener<List<String>> endecryptListener = mock(ActionListener.class);

    @Before
    public void setUp() {
        Encryptor encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
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
    public void trainAndPredictWithMetricsCorrelationThrowsException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: METRICS_CORRELATION");
        int dataSize = 100;
        DataFrame dataFrame = constructTestDataFrame(dataSize);
        MLInputDataset inputData = new DataFrameInputDataset(dataFrame);
        Input input = new MLInput(FunctionName.METRICS_CORRELATION, null, inputData);
        mlEngine.trainAndPredict(input);
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
        mlEngine.execute(input, listener, null);
    }

    @Test
    public void executeWithMetricsCorrelationThrowsException() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported executable function: METRICS_CORRELATION");
        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        Input input = MetricsCorrelationInput.builder().inputData(inputData).build();
        mlEngine.execute(input, null, null);
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
        mlEngine.execute(input, listener, null);
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
    public void getPathAPIs_ReturnsCorrectPath() {
        String modelId = "deployedModel";

        // Use the actual base path from the mlEngine instance
        Path basePath = mlEngine.getMlCachePath().getParent();
        Path modelsCachePath = basePath.resolve("ml_cache").resolve("models_cache");
        Path expectedDeployModelRootPath = modelsCachePath.resolve(MLEngine.DEPLOY_MODEL_FOLDER);
        assertEquals(expectedDeployModelRootPath.toString(), mlEngine.getDeployModelRootPath().toString());
        Path expectedDeployModelPath = expectedDeployModelRootPath.resolve(modelId);
        assertEquals(expectedDeployModelPath.toString(), mlEngine.getDeployModelPath(modelId).toString());

        String expectedDeployModelZipPath = expectedDeployModelRootPath.resolve(modelId).resolve("myModel") + ".zip";
        assertEquals(expectedDeployModelZipPath, mlEngine.getDeployModelZipPath(modelId, "myModel"));
        Path expectedDeployModelChunkPath = expectedDeployModelRootPath.resolve(modelId).resolve("chunks").resolve("1");
        assertEquals(expectedDeployModelChunkPath.toString(), mlEngine.getDeployModelChunkPath(modelId, 1).toString());

        assertEquals(
            "https://artifacts.opensearch.org/models/ml-models/model_listing/pre_trained_models.json",
            mlEngine.getPrebuiltModelMetaListPath()
        );

        Path expectedRegisterRootPath = modelsCachePath.resolve(MLEngine.REGISTER_MODEL_FOLDER);
        assertEquals(expectedRegisterRootPath.toString(), mlEngine.getRegisterModelRootPath().toString());
        Path expectedRegisterModelPath = expectedRegisterRootPath.resolve(modelId);
        assertEquals(expectedRegisterModelPath.toString(), mlEngine.getRegisterModelPath(modelId).toString());

        Path expectedMdelCacheRootPath = modelsCachePath.resolve("models");
        assertEquals(expectedMdelCacheRootPath.toString(), mlEngine.getModelCacheRootPath().toString());
        Path expectedMdelCachePath = expectedMdelCacheRootPath.resolve(modelId);
        assertEquals(expectedMdelCachePath.toString(), mlEngine.getModelCachePath(modelId).toString());

        Path expectedAnalysisRootPath = modelsCachePath.resolve("analysis");
        assertEquals(expectedAnalysisRootPath.toString(), mlEngine.getAnalysisRootPath().toString());
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
        mlEngine = new MLEngine(testPath, new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w="));

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
        List<String> testStrings = List.of("testString1", "testString2");
        mlEngine.getEncryptor().encrypt(testStrings, null, endecryptListener);
        verify(endecryptListener).onResponse(any(List.class));
    }

    @Test
    public void testGetConnectorCredential() throws IOException {
        ActionListener<List<String>> resultListener = ActionListener.wrap(r -> {
            mlEngine.getEncryptor().encrypt(List.of("test_key_value"), null, endecryptListener);
            String test_connector_string = "{\"name\":\"test_connector_name\",\"version\":\"1\","
                + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
                + "\"parameters\":{\"region\":\"test region\"},\"credential\":{\"key\":\""
                + r.getFirst()
                + "\"},"
                + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
                + "\"headers\":{\"api_key\":\"${credential.key}\"},"
                + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"}],"
                + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                    new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                    null,
                    test_connector_string
                );
            parser.nextToken();

            HttpConnector connector = new HttpConnector("http", parser);
            ActionListener<Map<String, String>> credentialListener = ActionListener.wrap(credential -> {
                assertNotNull(credential);
                assertEquals(credential.get("key"), "test_key_value");
                assertEquals(credential.get("region"), "test region");
            }, e -> { fail("Failed to get credential"); });
            mlEngine.getConnectorCredential(connector, credentialListener);
        }, e -> { fail("Failed to encrypt"); });
        mlEngine.getEncryptor().encrypt(List.of("test_key_value"), null, resultListener);
    }

    @Test
    public void testGetConnectorCredentialWithoutRegion() throws IOException {
        ActionListener<List<String>> resultListener = ActionListener.wrap(r -> {
            String test_connector_string = "{\"name\":\"test_connector_name\",\"version\":\"1\","
                + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
                + "\"parameters\":{},\"credential\":{\"key\":\""
                + r.getFirst()
                + "\"},"
                + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
                + "\"headers\":{\"api_key\":\"${credential.key}\"},"
                + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"}],"
                + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}}";

            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                    new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                    null,
                    test_connector_string
                );
            parser.nextToken();

            HttpConnector connector = new HttpConnector("http", parser);
            ActionListener<Map<String, String>> credentialListener = ActionListener.wrap(credential -> {
                assertNotNull(credential);
                assertEquals(credential.get("key"), "test_key_value");
                assertEquals(credential.get("region"), null);
            }, e -> { fail("Failed to get credential"); });
            mlEngine.getConnectorCredential(connector, credentialListener);
        }, e -> { fail("Failed to encrypt"); });
        mlEngine.getEncryptor().encrypt(List.of("test_key_value"), null, resultListener);
    }

    @Test
    public void testDeploy_withPredictableActionListener_successful() throws IOException {
        ActionListener<List<String>> resultListener = ActionListener.wrap(r -> {
            String testConnector = String.format(Locale.ROOT, """
                {
                    "name": "sagemaker: t2ppl",
                    "description": "t2ppl model",
                    "version": 1,
                    "protocol": "aws_sigv4",
                    "credential": {
                        "access_key": "%s",
                        "secret_key": "%s"
                    },
                    "parameters": {
                        "region": "us-east-1",
                        "service_name": "sagemaker",
                        "input_type": "search_document"
                    },
                    "actions": [
                        {
                            "action_type": "predict",
                            "method": "POST",
                            "headers": {
                                "content-type": "application/json",
                                "x-amz-content-sha256": "required"
                            },
                            "url": "https://runtime.sagemaker.us-west-2.amazonaws.com/endpoints/my-endpoint/invocations",
                            "request_body": "{\\"prompt\\":\\"${parameters.prompt}\\"}"
                        }
                    ]
                }
                """, r.get(0), r.get(1));

            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                    new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                    null,
                    testConnector
                );
            parser.nextToken();

            MLModel model = mock(MLModel.class);
            AwsConnector connector = new AwsConnector("aws_sigv4", parser);
            when(model.getAlgorithm()).thenReturn(FunctionName.REMOTE);
            when(model.getConnector()).thenReturn(connector);
            ActionListener<Predictable> actionListener = mock(ActionListener.class);
            SdkClient sdkClient = mock(SdkClient.class);
            when(sdkClient.isGlobalResource(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
            Map<String, Object> params = ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, Settings.EMPTY);
            mlEngine.deploy(model, params, actionListener);
            verify(actionListener).onResponse(any(Predictable.class));
        }, e -> { fail("Failed to encrypt"); });
        mlEngine.getEncryptor().encrypt(List.of("access-key", "secret-key"), null, resultListener);
    }

    @Test
    public void testDeploy_withPredictableActionListener_exceptional() {
        MLModel model = mock(MLModel.class);
        when(model.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        when(model.getConnector()).thenThrow(new RuntimeException("Runtime error"));
        ActionListener<Predictable> actionListener = mock(ActionListener.class);
        SdkClient sdkClient = mock(SdkClient.class);
        when(sdkClient.isGlobalResource(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        Map<String, Object> params = ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, Settings.EMPTY);
        mlEngine.deploy(model, params, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().getMessage().contains("Runtime error"));
    }
}
