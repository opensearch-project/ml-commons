/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;

import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;

public class AwsConnectorExecutorTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    Settings settings;

    ThreadContext threadContext;

    @Mock
    ScriptService scriptService;

    @Mock
    SdkHttpClient httpClient;

    @Mock
    ExecutableHttpRequest httpRequest;

    @Mock
    HttpExecuteResponse response;

    Encryptor encryptor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
    }

    @Test
    public void executePredict_RemoteInferenceInput_MissingCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .protocol("http")
            .version("1")
            .actions(Arrays.asList(predictAction))
            .build();
    }

    @Test
    public void executePredict_RemoteInferenceInput_NullResponse() throws IOException {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule.expectMessage("No response from model");
        when(response.responseBody()).thenReturn(Optional.empty());
        when(httpRequest.call()).thenReturn(response);
        SdkHttpResponse httpResponse = mock(SdkHttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(response.httpResponse()).thenReturn(httpResponse);
        when(httpClient.prepareRequest(any())).thenReturn(httpRequest);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = Map
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = Map.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector, httpClient));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
    }

    @Test
    public void executePredict_RemoteInferenceInput_InvalidToken() throws IOException {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule.expectMessage("{\"message\":\"The security token included in the request is invalid\"}");
        String jsonString = "{\"message\":\"The security token included in the request is invalid\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes());
        AbortableInputStream abortableInputStream = AbortableInputStream.create(inputStream);
        when(response.responseBody()).thenReturn(Optional.of(abortableInputStream));
        when(httpRequest.call()).thenReturn(response);
        SdkHttpResponse httpResponse = mock(SdkHttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(403);
        when(response.httpResponse()).thenReturn(httpResponse);
        when(httpClient.prepareRequest(any())).thenReturn(httpRequest);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = Map
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = Map.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector, httpClient));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
    }

    @Test
    public void executePredict_RemoteInferenceInput() throws IOException {
        String jsonString = "{\"key\":\"value\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes());
        AbortableInputStream abortableInputStream = AbortableInputStream.create(inputStream);
        when(response.responseBody()).thenReturn(Optional.of(abortableInputStream));
        SdkHttpResponse httpResponse = mock(SdkHttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(response.httpResponse()).thenReturn(httpResponse);
        when(httpRequest.call()).thenReturn(response);
        when(httpClient.prepareRequest(any())).thenReturn(httpRequest);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = Map
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = Map.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector, httpClient));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("input", "test input data")).build();
        ModelTensorOutput modelTensorOutput = executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals("value", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("key"));
    }

    @Test
    public void executePredict_TextDocsInferenceInput() throws IOException {
        String jsonString = "{\"key\":\"value\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes());
        AbortableInputStream abortableInputStream = AbortableInputStream.create(inputStream);
        when(response.responseBody()).thenReturn(Optional.of(abortableInputStream));
        when(httpRequest.call()).thenReturn(response);
        SdkHttpResponse httpResponse = mock(SdkHttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(response.httpResponse()).thenReturn(httpResponse);
        when(httpClient.prepareRequest(any())).thenReturn(httpRequest);

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = Map
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = Map.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector, httpClient));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(List.of("input")).build();
        ModelTensorOutput modelTensorOutput = executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals("value", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("key"));
    }
}
