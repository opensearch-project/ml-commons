/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.SKIP_VALIDATE_MISSING_PARAMETERS;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.SparseEmbeddingFormat;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

public class RemoteConnectorExecutorTest {

    Encryptor encryptor;

    @Mock
    Client client;

    @Mock
    ThreadPool threadPool;

    @Mock
    private ScriptService scriptService;

    @Mock
    ActionListener<Tuple<Integer, ModelTensors>> actionListener;

    @Mock
    private MLAlgoParams mlInputParams;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory("{\"result\": \"hello world\"}"));
    }

    private Connector getConnector(Map<String, String> parameters) {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key", null), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key", null));
        return AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
    }

    private AwsConnectorExecutor getExecutor(Connector connector) {
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        return executor;
    }

    @Test
    public void executePreparePayloadAndInvoke_SkipValidateMissingParameterDisabled() {
        Map<String, String> parameters = ImmutableMap
            .of(SKIP_VALIDATE_MISSING_PARAMETERS, "false", SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        Exception exception = Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener, null)
            );
        assert exception.getMessage().contains("Some parameter placeholder not filled in payload: role");
    }

    @Test
    public void executePreparePayloadAndInvoke_SkipValidateMissingParameterEnabled() {
        Map<String, String> parameters = ImmutableMap
            .of(SKIP_VALIDATE_MISSING_PARAMETERS, "true", SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener, null);
        Mockito
            .verify(executor, times(1))
            .invokeRemoteService(any(), any(), any(), argThat(argument -> argument.contains("You are a ${parameters.role}")), any(), any());
    }

    @Test
    public void executePreparePayloadAndInvoke_SkipValidateMissingParameterDefault() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        Exception exception = Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener, null)
            );
        assert exception.getMessage().contains("Some parameter placeholder not filled in payload: role");
    }

    @Test
    public void executePreparePayloadAndInvoke_PassingParameter() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        AsymmetricTextEmbeddingParameters inputParams = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .embeddingContentType(null)
            .build();
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .parameters(inputParams)
            .inputDataset(inputDataSet)
            .build();

        Exception exception = Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener, null)
            );
        assert exception.getMessage().contains("Some parameter placeholder not filled in payload: role");
    }

    @Test
    public void executePreparePayloadAndInvoke_GetParamsIOException() throws Exception {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "test input"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        doThrow(new IOException("UT test IOException")).when(mlInputParams).toXContent(any(XContentBuilder.class), any());
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .parameters(mlInputParams)
            .inputDataset(inputDataSet)
            .build();

        executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener, null);
        verify(actionListener).onFailure(argThat(e -> e instanceof IOException && e.getMessage().contains("UT test IOException")));
    }

    @Test
    public void executeGetParams_MissingParameter() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "${parameters.input}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        AsymmetricTextEmbeddingParameters inputParams = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .embeddingContentType(null)
            .build();
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .parameters(inputParams)
            .inputDataset(inputDataSet)
            .build();

        try {
            Map<String, String> paramsMap = RemoteConnectorExecutor.getParams(mlInput);
            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("sparse_embedding_format", "WORD");
            Assert.assertEquals(expectedMap, paramsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void executeGetParams_PassingParameter() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "${parameters.input}"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        AsymmetricTextEmbeddingParameters inputParams = AsymmetricTextEmbeddingParameters
            .builder()
            .sparseEmbeddingFormat(SparseEmbeddingFormat.WORD)
            .embeddingContentType(AsymmetricTextEmbeddingParameters.EmbeddingContentType.PASSAGE)
            .build();
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .parameters(inputParams)
            .inputDataset(inputDataSet)
            .build();

        try {
            Map<String, String> paramsMap = RemoteConnectorExecutor.getParams(mlInput);
            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("sparse_embedding_format", "WORD");
            expectedMap.put("content_type", "PASSAGE");
            Assert.assertEquals(expectedMap, paramsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void executeGetParams_ConvertToString() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "${parameters.input}"))
            .actionType(PREDICT)
            .build();
        KMeansParams inputParams = KMeansParams
            .builder()
            .centroids(5)
            .iterations(100)
            .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
            .build();
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .parameters(inputParams)
            .inputDataset(inputDataSet)
            .build();

        try {
            Map<String, String> paramsMap = RemoteConnectorExecutor.getParams(mlInput);
            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("centroids", "5");
            expectedMap.put("iterations", "100");
            expectedMap.put("distance_type", "EUCLIDEAN");
            Assert.assertEquals(expectedMap, paramsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void executeAction_WithTransportChannel() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "bedrock", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "test input"))
            .actionType(PREDICT)
            .build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        ActionListener<MLTaskResponse> streamActionListener = mock(ActionListener.class);
        TransportChannel channel = mock(TransportChannel.class);

        Mockito.doAnswer(invocation -> {
            ActionListener<Tuple<Integer, ModelTensors>> listener = invocation.getArgument(3);
            ModelTensors mockTensors = mock(ModelTensors.class);
            listener.onResponse(new Tuple<>(200, mockTensors));
            return null;
        }).when(executor).preparePayloadAndInvoke(any(), any(), any(), any(), any());
        executor.executeAction(PREDICT.name(), mlInput, streamActionListener, channel);

        verify(executor, times(1))
            .preparePayloadAndInvoke(eq(PREDICT.name()), eq(mlInput), any(ExecutionContext.class), any(ActionListener.class), eq(channel));

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(streamActionListener, times(1)).onResponse(responseCaptor.capture());
        assertNotNull(responseCaptor.getValue());
        assertTrue(responseCaptor.getValue().getOutput() instanceof ModelTensorOutput);
    }
}
