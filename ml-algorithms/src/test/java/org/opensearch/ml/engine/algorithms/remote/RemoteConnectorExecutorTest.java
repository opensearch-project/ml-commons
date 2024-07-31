/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.RECURSIVE_PARAMETER_ENABLED;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;

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

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
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
            .of(ACCESS_KEY_FIELD, encryptor.encrypt("test_key"), SECRET_KEY_FIELD, encryptor.encrypt("test_secret_key"));
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
    public void executePreparePayloadAndInvoke_RecursiveDisabled() {
        Map<String, String> parameters = ImmutableMap
            .of(RECURSIVE_PARAMETER_ENABLED, "false", SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}", "role", "bot"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener);
        Mockito
            .verify(executor, times(1))
            .invokeRemoteService(any(), any(), any(), argThat(argument -> argument.contains("You are a ${parameters.role}")), any(), any());
    }

    @Test
    public void executePreparePayloadAndInvoke_RecursiveEnabled() {
        Map<String, String> parameters = ImmutableMap
            .of(RECURSIVE_PARAMETER_ENABLED, "true", SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}", "role", "bot"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener);
        Mockito
            .verify(executor, times(1))
            .invokeRemoteService(any(), any(), any(), argThat(argument -> argument.contains("You are a bot")), any(), any());
    }

    @Test
    public void executePreparePayloadAndInvoke_RecursiveDefault() {
        Map<String, String> parameters = ImmutableMap.of(SERVICE_NAME_FIELD, "sagemaker", REGION_FIELD, "us-west-2");
        Connector connector = getConnector(parameters);
        AwsConnectorExecutor executor = getExecutor(connector);

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("input", "You are a ${parameters.role}", "role", "bot"))
            .actionType(PREDICT)
            .build();
        String actionType = inputDataSet.getActionType().toString();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(inputDataSet).build();

        executor.preparePayloadAndInvoke(actionType, mlInput, null, actionListener);
        Mockito
            .verify(executor, times(1))
            .invokeRemoteService(any(), any(), any(), argThat(argument -> argument.contains("You are a bot")), any(), any());
    }
}
