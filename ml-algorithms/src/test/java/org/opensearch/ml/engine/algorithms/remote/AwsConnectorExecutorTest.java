/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;

import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
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
    ScriptService scriptService;

    @Mock
    ActionListener<MLTaskResponse> actionListener;

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
    public void executePredict_RemoteInferenceInput_invalidIp() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test1.com/mock")
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
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
        Mockito.verify(actionListener, times(1)).onFailure(any(MLException.class));
    }

    @Test
    public void executePredict_RemoteInferenceInput_illegalIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
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
        AwsConnectorExecutor executor = spy(new AwsConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
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
}
