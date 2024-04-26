/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;

import com.google.common.collect.ImmutableMap;

public class HttpJsonConnectorExecutorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private ActionListener<List<ModelTensors>> actionListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void invokeRemoteModel_WrongHttpMethod() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("wrong_method")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null, null, actionListener);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assertEquals("unsupported http method", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteModel_invalidIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://127.0.0.1/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteModel(
                createMLInput(),
                new HashMap<>(),
                "{\"input\": \"hello world\"}",
                new HashMap<>(),
                new ExecutionContext(0, new CountDownLatch(1), new AtomicReference<>()),
                actionListener
            );
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof IllegalArgumentException;
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteModel_Empty_payload() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteModel(
                createMLInput(),
                new HashMap<>(),
                null,
                new HashMap<>(),
                new ExecutionContext(0, new CountDownLatch(1), new AtomicReference<>()),
                actionListener
            );
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof IllegalArgumentException;
        assertEquals("Content length is 0. Aborting request to remote model", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteModel_get_request() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("GET")
            .url("http://openai.com/mock")
            .requestBody("")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteModel(
                createMLInput(),
                new HashMap<>(),
                null,
                new HashMap<>(),
                new ExecutionContext(0, new CountDownLatch(1), new AtomicReference<>()),
                actionListener
            );
    }

    @Test
    public void invokeRemoteModel_post_request() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteModel(
                createMLInput(),
                new HashMap<>(),
                "hello world",
                new HashMap<>(),
                new ExecutionContext(0, new CountDownLatch(1), new AtomicReference<>()),
                actionListener
            );
    }

    @Test
    public void invokeRemoteModel_nullHttpClient_throwMLException() throws NoSuchFieldException, IllegalAccessException {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        Field httpClientField = HttpJsonConnectorExecutor.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(executor, null);
        executor
            .invokeRemoteModel(
                createMLInput(),
                new HashMap<>(),
                "hello world",
                new HashMap<>(),
                new ExecutionContext(0, new CountDownLatch(1), new AtomicReference<>()),
                actionListener
            );
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue() instanceof NullPointerException;
    }

    private MLInput createMLInput() {
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        return MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.REMOTE).build();
    }
}
