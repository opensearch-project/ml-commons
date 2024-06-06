/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BackoffPolicy;
import org.opensearch.client.Client;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
    ActionListener<MLTaskResponse> actionListener;

    Encryptor encryptor;

    @Mock
    private ScriptService scriptService;

    Map<String, ?> dataAsMap = Map.of("completion", "answer");

    List<ModelTensor> modelTensors = List
        .of(
            new ModelTensor("tensor0", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap),
            new ModelTensor("tensor1", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap),
            new ModelTensor("tensor2", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap)
        );

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory("{\"result\": \"hello world\"}"));
    }

    @Test
    public void executePredict_RemoteInferenceInput_MissingCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
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
    public void executePredict_RemoteInferenceInput_EmptyIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        assert exceptionCaptor.getValue() instanceof NullPointerException;
        assertEquals("host must not be null.", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void executePredict_TextDocsInferenceInput() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
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
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withStepSize() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "2");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        MLInputDataset inputDataSet1 = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet1).build(), actionListener);

        Mockito.verify(actionListener, times(0)).onFailure(any());
        Mockito.verify(executor, times(3)).preparePayloadAndInvokeRemoteModel(any(), any(), any());
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withStepSize_returnOrderedResults() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "1");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        doAnswer(invocation -> {
            MLInput mlInput = invocation.getArgument(0);
            ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
            String doc = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs().get(0);
            Integer idx = Integer.parseInt(doc.substring(doc.length() - 1));
            actionListener.onResponse(new Tuple<>(3 - idx, new ModelTensors(modelTensors.subList(3 - idx, 4 - idx))));
            return null;
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        Mockito.verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        for (int idx = 0; idx < 3; idx++) {
            assert ((ModelTensorOutput) responseCaptor.getValue().getOutput())
                .getMlModelOutputs()
                .get(idx)
                .getMlModelTensors()
                .get(0)
                .equals(modelTensors.get(idx));
        }
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withStepSize_partiallyFailed_thenFail() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "1");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        doAnswer(invocation -> {
            MLInput mlInput = invocation.getArgument(0);
            ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
            String doc = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs().get(0);
            if (doc.endsWith("1")) {
                actionListener.onFailure(new OpenSearchStatusException("test failure", RestStatus.BAD_REQUEST));
            } else {
                actionListener.onResponse(new Tuple<>(0, new ModelTensors(modelTensors.subList(0, 1))));
            }
            return null;
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        assert exceptionCaptor.getValue() instanceof OpenSearchStatusException;
        assertEquals("test failure", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withStepSize_failWithMultipleFailures() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "1");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT))
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        doAnswer(invocation -> {
            MLInput mlInput = invocation.getArgument(0);
            ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
            String doc = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs().get(0);
            if (!doc.endsWith("1")) {
                actionListener.onFailure(new OpenSearchStatusException("test failure", RestStatus.BAD_REQUEST));
            } else {
                actionListener.onResponse(new Tuple<>(0, new ModelTensors(modelTensors.subList(0, 1))));
            }
            return null;
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        assert exceptionCaptor.getValue() instanceof OpenSearchStatusException;
        assertEquals("test failure", exceptionCaptor.getValue().getMessage());
        assert exceptionCaptor.getValue().getSuppressed().length == 1;
        assert exceptionCaptor.getValue().getSuppressed()[0] instanceof OpenSearchStatusException;
        assertEquals("test failure", exceptionCaptor.getValue().getSuppressed()[0].getMessage());
    }

    @Test
    public void executePredict_RemoteInferenceInput_nullHttpClient_throwNPException() throws NoSuchFieldException, IllegalAccessException {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
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
        AwsConnectorExecutor executor0 = new AwsConnectorExecutor(connector);
        Field httpClientField = AwsConnectorExecutor.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(executor0, null);
        AwsConnectorExecutor executor = spy(executor0);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        assert exceptionCaptor.getValue() instanceof NullPointerException;
    }

    @Test
    public void executePredict_RemoteInferenceInput_negativeStepSize_throwIllegalArgumentException() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "-1");
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
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());
        assert exceptionCaptor.getValue() instanceof IllegalArgumentException;
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withoutStepSize_emptyPredictionAction() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);
        ArgumentCaptor<Exception> exceptionArgumentCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionArgumentCaptor.capture());
        assert exceptionArgumentCaptor.getValue() instanceof IllegalArgumentException;
        assert "no predict action found".equals(exceptionArgumentCaptor.getValue().getMessage());
    }

    @Test
    public void executePredict_TextDocsInferenceInput_withoutStepSize_userDefinedPreProcessFunction() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(
                "\n    StringBuilder builder = new StringBuilder();\n    builder.append(\"\\\"\");\n    String first = params.text_docs[0];\n    builder.append(first);\n    builder.append(\"\\\"\");\n    def parameters = \"{\" +\"\\\"text_inputs\\\":\" + builder + \"}\";\n    return  \"{\" +\"\\\"parameters\\\":\" + parameters + \"}\";"
            )
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap.of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker");
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
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(executor.getScriptService()).thenReturn(scriptService);

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_whenRetryEnabled_thenInvokeRemoteModelWithRetry() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .build();
        Map<String, String> credential = ImmutableMap
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
        Map<String, String> parameters = ImmutableMap
            .of(REGION_FIELD, "us-west-2", SERVICE_NAME_FIELD, "sagemaker", "input_docs_processed_step_size", "5");
        // execute with retry disabled
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT);
        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(connectorClientConfig)
            .build();
        connector.decrypt((c) -> encryptor.decrypt(c));
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        ExecutorService executorService = mock(ExecutorService.class);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(any())).thenReturn(executorService);
        doNothing().when(executorService).execute(any());

        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of("input1", "input2", "input3")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        Mockito.verify(executor, times(0)).invokeRemoteModelWithRetry(any(), any(), any(), any(), any());
        Mockito.verify(executor, times(1)).invokeRemoteModel(any(), any(), any(), any(), any());

        // execute with retry enabled
        ConnectorClientConfig connectorClientConfig2 = new ConnectorClientConfig(10, 10, 10, 1, 1, 1, RetryBackoffPolicy.CONSTANT);
        Connector connector2 = AwsConnector
            .awsConnectorBuilder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .credential(credential)
            .actions(Arrays.asList(predictAction))
            .connectorClientConfig(connectorClientConfig2)
            .build();
        connector2.decrypt((c) -> encryptor.decrypt(c));
        executor.initialize(connector2);
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build(), actionListener);

        Mockito.verify(executor, times(1)).invokeRemoteModelWithRetry(any(), any(), any(), any(), any());
        Mockito.verify(actionListener, times(0)).onFailure(any());
    }

    @Test
    public void testGetRetryBackoffPolicy() {
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(mock(AwsConnector.class)));

        ConnectorClientConfig.ConnectorClientConfigBuilder configBuilder = ConnectorClientConfig
            .builder()
            .retryBackoffMillis(123)
            .retryTimeoutSeconds(456)
            .maxRetryTimes(789)
            .retryBackoffPolicy(RetryBackoffPolicy.CONSTANT);

        assertEquals(
            executor.getRetryBackoffPolicy(configBuilder.build()).getClass(),
            BackoffPolicy.constantBackoff(TimeValue.timeValueMillis(123), Integer.MAX_VALUE).getClass()
        );

        configBuilder.retryBackoffPolicy(RetryBackoffPolicy.EXPONENTIAL_EQUAL_JITTER);
        assertEquals(
            executor.getRetryBackoffPolicy(configBuilder.build()).getClass(),
            BackoffPolicy.exponentialEqualJitterBackoff(123, 456).getClass()
        );

        configBuilder.retryBackoffPolicy(RetryBackoffPolicy.EXPONENTIAL_FULL_JITTER);
        assertEquals(
            executor.getRetryBackoffPolicy(configBuilder.build()).getClass(),
            BackoffPolicy.exponentialFullJitterBackoff(123).getClass()
        );
    }

    @Test
    public void invokeRemoteModelWithRetry_whenRetryableException_thenRetryUntilSuccess() {
        MLInput mlInput = mock(MLInput.class);
        Map<String, String> parameters = Map.of();
        String payload = "";
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(10, 10, 10, 1, 10, -1, RetryBackoffPolicy.CONSTANT);
        ExecutionContext executionContext = new ExecutionContext(123);
        ActionListener<Tuple<Integer, ModelTensors>> actionListener = mock(ActionListener.class);
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(mock(AwsConnector.class)));
        ExecutorService executorService = mock(ExecutorService.class);

        doAnswer(new Answer() {
            private int countOfInvocation = 0;

            @Override
            public Void answer(InvocationOnMock invocation) {
                ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
                // fail the first 10 invocation, then success
                if (countOfInvocation++ < 10) {
                    actionListener.onFailure(new RemoteConnectorThrottlingException("test failure retryable", RestStatus.BAD_REQUEST));
                } else {
                    actionListener.onResponse(new Tuple<>(123, mock(ModelTensors.class)));
                }
                return null;
            }
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());
        when(executor.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.executor(any())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(), any(), any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any());

        executor.invokeRemoteModelWithRetry(mlInput, parameters, payload, executionContext, actionListener);
        Mockito.verify(actionListener, times(0)).onFailure(any());
        Mockito.verify(actionListener, times(1)).onResponse(any());
        Mockito.verify(executor, times(11)).invokeRemoteModel(any(), any(), any(), any(), any());
    }

    @Test
    public void invokeRemoteModelWithRetry_whenRetryExceedMaxRetryTimes_thenCallOnFailure() {
        MLInput mlInput = mock(MLInput.class);
        Map<String, String> parameters = Map.of();
        String payload = "";
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(10, 10, 10, 1, 10, 5, RetryBackoffPolicy.CONSTANT);
        ExecutionContext executionContext = new ExecutionContext(123);
        ActionListener<Tuple<Integer, ModelTensors>> actionListener = mock(ActionListener.class);
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(mock(AwsConnector.class)));
        ExecutorService executorService = mock(ExecutorService.class);

        doAnswer(new Answer() {
            private int countOfInvocation = 0;

            @Override
            public Void answer(InvocationOnMock invocation) {
                ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
                // fail the first 10 invocation, then success
                if (countOfInvocation++ < 10) {
                    actionListener.onFailure(new RemoteConnectorThrottlingException("test failure retryable", RestStatus.BAD_REQUEST));
                } else {
                    actionListener.onResponse(new Tuple<>(123, mock(ModelTensors.class)));
                }
                return null;
            }
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());
        when(executor.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.executor(any())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(), any(), any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any());

        executor.invokeRemoteModelWithRetry(mlInput, parameters, payload, executionContext, actionListener);
        Mockito.verify(actionListener, times(1)).onFailure(any());
        Mockito.verify(actionListener, times(0)).onResponse(any());
        Mockito.verify(executor, times(6)).invokeRemoteModel(any(), any(), any(), any(), any());
    }

    @Test
    public void invokeRemoteModelWithRetry_whenNonRetryableException_thenCallOnFailure() {
        MLInput mlInput = mock(MLInput.class);
        Map<String, String> parameters = Map.of();
        String payload = "";
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(10, 10, 10, 1, 10, -1, RetryBackoffPolicy.CONSTANT);
        ExecutionContext executionContext = new ExecutionContext(123);
        ActionListener<Tuple<Integer, ModelTensors>> actionListener = mock(ActionListener.class);
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(mock(AwsConnector.class)));
        ExecutorService executorService = mock(ExecutorService.class);

        doAnswer(new Answer() {
            private int countOfInvocation = 0;

            @Override
            public Void answer(InvocationOnMock invocation) {
                ActionListener<Tuple<Integer, ModelTensors>> actionListener = invocation.getArgument(4);
                // fail the first 2 invocation with retryable exception, then fail with non-retryable exception
                if (countOfInvocation++ < 2) {
                    actionListener.onFailure(new RemoteConnectorThrottlingException("test failure retryable", RestStatus.BAD_REQUEST));
                } else {
                    actionListener.onFailure(new OpenSearchStatusException("test failure", RestStatus.BAD_REQUEST));
                }
                return null;
            }
        }).when(executor).invokeRemoteModel(any(), any(), any(), any(), any());
        when(executor.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.executor(any())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(), any(), any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any());

        ArgumentCaptor<Exception> exceptionArgumentCaptor = ArgumentCaptor.forClass(Exception.class);

        executor.invokeRemoteModelWithRetry(mlInput, parameters, payload, executionContext, actionListener);
        Mockito.verify(actionListener, times(1)).onFailure(exceptionArgumentCaptor.capture());
        Mockito.verify(actionListener, times(0)).onResponse(any());
        Mockito.verify(executor, times(3)).invokeRemoteModel(any(), any(), any(), any(), any());
        assert exceptionArgumentCaptor.getValue() instanceof OpenSearchStatusException;
        assertEquals("test failure", exceptionArgumentCaptor.getValue().getMessage());
        assertEquals("test failure retryable", exceptionArgumentCaptor.getValue().getSuppressed()[0].getMessage());
        assertEquals("test failure retryable", exceptionArgumentCaptor.getValue().getSuppressed()[1].getMessage());
    }
}
