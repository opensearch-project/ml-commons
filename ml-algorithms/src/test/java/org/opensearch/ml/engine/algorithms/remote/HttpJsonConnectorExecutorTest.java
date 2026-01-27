/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.SKIP_SSL_VERIFICATION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.collect.Tuple;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;

import com.google.common.collect.ImmutableMap;

public class HttpJsonConnectorExecutorTest {

    private static final Logger logger = LogManager.getLogger(MLHttpClientFactory.class);
    private static final String LOG_APPENDER_NAME = "TestLogAppender";
    private static TestLogAppender testAppender;
    private static LoggerConfig loggerConfig;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private ActionListener<Tuple<Integer, ModelTensors>> actionListener;

    @BeforeClass
    public static void setUpClass() {
        testAppender = new TestLogAppender(LOG_APPENDER_NAME);
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        loggerConfig = context.getConfiguration().getLoggerConfig(logger.getName());
        loggerConfig.addAppender(testAppender, Level.WARN, null);
        context.updateLoggers();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() {
        testAppender.clear();
    }

    @AfterClass
    public static void tearDownClass() {
        loggerConfig.removeAppender(LOG_APPENDER_NAME);
        testAppender.stop();
    }

    @Test
    public void invokeRemoteService_WrongHttpMethod() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.invokeRemoteService(PREDICT.name(), null, null, null, null, actionListener);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assertEquals("unsupported http method", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteService_invalidIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.setConnectorPrivateIpEnabled(false);
        executor
            .invokeRemoteService(
                PREDICT.name(),
                createMLInput(),
                new HashMap<>(),
                "{\"input\": \"hello world\"}",
                new ExecutionContext(0),
                actionListener
            );
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof IllegalArgumentException;
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteService_EnabledPrivateIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.setConnectorPrivateIpEnabled(true);
        executor
            .invokeRemoteService(
                PREDICT.name(),
                createMLInput(),
                new HashMap<>(),
                "{\"input\": \"hello world\"}",
                new ExecutionContext(0),
                actionListener
            );
        Mockito.verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void invokeRemoteService_DisabledPrivateIpAddress() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.setConnectorPrivateIpEnabled(false);
        executor
            .invokeRemoteService(
                PREDICT.name(),
                createMLInput(),
                new HashMap<>(),
                "{\"input\": \"hello world\"}",
                new ExecutionContext(0),
                actionListener
            );
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof IllegalArgumentException;
        assertEquals("Remote inference host name has private ip address: 127.0.0.1", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteService_Empty_payload() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), null, new ExecutionContext(0), actionListener);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof IllegalArgumentException;
        assertEquals("Content length is 0. Aborting request to remote model", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteService_get_request() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        executor.invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), null, new ExecutionContext(0), actionListener);
    }

    @Test
    public void invokeRemoteService_post_request() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
            .invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), "hello world", new ExecutionContext(0), actionListener);
    }

    @Test
    public void invokeRemoteService_SkipSslVerification_True() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(Map.of(SKIP_SSL_VERIFICATION, "true"))
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), "hello world", new ExecutionContext(0), actionListener);
        verify(actionListener, never()).onFailure(any());

        boolean isWarningLogged = testAppender
            .getLogEvents()
            .stream()
            .anyMatch(
                event -> event.getLevel() == Level.WARN
                    && event
                        .getMessage()
                        .getFormattedMessage()
                        .contains(
                            "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                                + " attacks. Only use this setting in trusted environments."
                        )
            );
        assertTrue(isWarningLogged);
    }

    @Test
    public void invokeRemoteService_SkipSslVerification_False() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(Map.of(SKIP_SSL_VERIFICATION, "false"))
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor
            .invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), "hello world", new ExecutionContext(0), actionListener);
        verify(actionListener, never()).onFailure(any());

        boolean isWarningLogged = testAppender
            .getLogEvents()
            .stream()
            .anyMatch(
                event -> event.getLevel() == Level.WARN
                    && event
                        .getMessage()
                        .getFormattedMessage()
                        .contains(
                            "SSL certificate verification is DISABLED. This connection is vulnerable to man-in-the-middle"
                                + " attacks. Only use this setting in trusted environments."
                        )
            );
        assertFalse(isWarningLogged);
    }

    @Test
    public void invokeRemoteService_nullHttpClient_throwMLException() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(null);
        executor
            .invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), "hello world", new ExecutionContext(0), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue() instanceof NullPointerException;
    }

    @Test
    public void testInvokeRemoteServiceStream_ValidInterface() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
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
        StreamPredictActionListener<MLTaskResponse, ?> actionListener = mock(StreamPredictActionListener.class);

        Map<String, String> parameters = ImmutableMap.of("_llm_interface", "openai/v1/chat/completions", "input", "test input");
        String payload = "{\"input\": \"test input\"}";
        executor.invokeRemoteServiceStream(PREDICT.name(), createMLInput(), parameters, payload, new ExecutionContext(0), actionListener);
        verify(actionListener, never()).onFailure(any());
    }

    @Test
    public void testInvokeRemoteServiceStream_WithException() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
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
        StreamPredictActionListener<MLTaskResponse, ?> streamActionListener = mock(StreamPredictActionListener.class);

        Map<String, String> parameters = ImmutableMap.of("_llm_interface", "invalid_interface", "input", "test input");
        String payload = "{\"input\": \"test input\"}";

        executor
            .invokeRemoteServiceStream(PREDICT.name(), createMLInput(), parameters, payload, new ExecutionContext(0), streamActionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(streamActionListener, times(1)).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof MLException);
        assertEquals("Fail to execute streaming", captor.getValue().getMessage());
    }

    private MLInput createMLInput() {
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        return MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.REMOTE).build();
    }

    /**
     * Log appender class to check the skip ssl verification warning
     */
    static class TestLogAppender extends AbstractAppender {

        private final List<LogEvent> logEvents = new ArrayList<>();

        public TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false);
            start();
        }

        @Override
        public void append(LogEvent event) {
            logEvents.add(event.toImmutable());
        }

        public List<LogEvent> getLogEvents() {
            return logEvents;
        }

        public void clear() {
            logEvents.clear();
        }
    }
}
