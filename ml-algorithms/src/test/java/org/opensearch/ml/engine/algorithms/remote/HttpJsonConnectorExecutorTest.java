/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class HttpJsonConnectorExecutorTest extends MLStaticMockBase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private ActionListener<Tuple<Integer, ModelTensors>> actionListener;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    private ThreadContext threadContext;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setConnectorPrivateIpEnabled(false);
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setConnectorPrivateIpEnabled(true);
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setConnectorPrivateIpEnabled(false);
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        executor
            .invokeRemoteService(PREDICT.name(), createMLInput(), new HashMap<>(), "hello world", new ExecutionContext(0), actionListener);
    }

    @Test
    // @org.junit.Ignore("Temporarily disabled due to IP validation issues")
    public void invokeRemoteService_SkipSslVerification_True() {
        try (MockedStatic<MLHttpClientFactory> mockedFactory = mockStatic(MLHttpClientFactory.class)) {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(PREDICT)
                .method("POST")
                .url("http://openai.com/mock")
                .requestBody("hello world")
                .build();
            ConnectorClientConfig clientConfig = new ConnectorClientConfig(
                10,
                10,
                10,
                1,
                1,
                0,
                RetryBackoffPolicy.CONSTANT,
                true,
                null,
                null,
                null
            );
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .connectorClientConfig(clientConfig)
                .actions(Arrays.asList(predictAction))
                .build();
            SdkAsyncHttpClient mockClient = mock(SdkAsyncHttpClient.class);
            mockedFactory
                .when(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setClient(client);
            when(client.threadPool()).thenReturn(threadPool);
            when(threadPool.getThreadContext()).thenReturn(threadContext);
            executor
                .invokeRemoteService(
                    PREDICT.name(),
                    createMLInput(),
                    new HashMap<>(),
                    "hello world",
                    new ExecutionContext(0),
                    actionListener
                );
            verify(actionListener, never()).onFailure(any());
            ArgumentCaptor<Boolean> sslVerificationCaptor = ArgumentCaptor.forClass(Boolean.class);
            mockedFactory
                .verify(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            sslVerificationCaptor.capture(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                );
            // Assert that skipSslVerification was set to true
            assertTrue("SSL verification should be disabled", sslVerificationCaptor.getValue());
        }
    }

    @Test
    // @org.junit.Ignore("Temporarily disabled due to IP validation issues")
    public void invokeRemoteService_SkipSslVerification_False() {
        try (MockedStatic<MLHttpClientFactory> mockedFactory = mockStatic(MLHttpClientFactory.class)) {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(PREDICT)
                .method("POST")
                .url("http://openai.com/mock")
                .requestBody("hello world")
                .build();
            ConnectorClientConfig clientConfig = new ConnectorClientConfig(
                10,
                10,
                10,
                1,
                1,
                0,
                RetryBackoffPolicy.CONSTANT,
                false,
                null,
                null,
                null
            );
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .connectorClientConfig(clientConfig)
                .actions(Arrays.asList(predictAction))
                .build();
            SdkAsyncHttpClient mockClient = mock(SdkAsyncHttpClient.class);
            mockedFactory
                .when(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setClient(client);
            when(client.threadPool()).thenReturn(threadPool);
            when(threadPool.getThreadContext()).thenReturn(threadContext);
            executor
                .invokeRemoteService(
                    PREDICT.name(),
                    createMLInput(),
                    new HashMap<>(),
                    "hello world",
                    new ExecutionContext(0),
                    actionListener
                );
            verify(actionListener, never()).onFailure(any());
            ArgumentCaptor<Boolean> sslVerificationCaptor = ArgumentCaptor.forClass(Boolean.class);
            mockedFactory
                .verify(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            sslVerificationCaptor.capture(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                );
            // Assert that skipSslVerification was set to false
            assertFalse("SSL verification should be enabled", sslVerificationCaptor.getValue());
        }
    }

    @Test
    // @org.junit.Ignore("Temporarily disabled due to IP validation issues ")
    public void invokeRemoteService_SkipSslVerification_Null() {
        try (MockedStatic<MLHttpClientFactory> mockedFactory = mockStatic(MLHttpClientFactory.class)) {
            ConnectorAction predictAction = ConnectorAction
                .builder()
                .actionType(PREDICT)
                .method("POST")
                .url("http://openai.com/mock")
                .requestBody("hello world")
                .build();
            ConnectorClientConfig clientConfig = new ConnectorClientConfig(
                10,
                10,
                10,
                1,
                1,
                0,
                RetryBackoffPolicy.CONSTANT,
                null,
                null,
                null,
                null
            );
            Connector connector = HttpConnector
                .builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .connectorClientConfig(clientConfig)
                .actions(Arrays.asList(predictAction))
                .build();
            SdkAsyncHttpClient mockClient = mock(SdkAsyncHttpClient.class);
            mockedFactory
                .when(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setClient(client);
            when(client.threadPool()).thenReturn(threadPool);
            when(threadPool.getThreadContext()).thenReturn(threadContext);
            executor
                .invokeRemoteService(
                    PREDICT.name(),
                    createMLInput(),
                    new HashMap<>(),
                    "hello world",
                    new ExecutionContext(0),
                    actionListener
                );
            verify(actionListener, never()).onFailure(any());
            ArgumentCaptor<Boolean> sslVerificationCaptor = ArgumentCaptor.forClass(Boolean.class);
            mockedFactory
                .verify(
                    () -> MLHttpClientFactory
                        .getAsyncHttpClient(
                            any(Duration.class),
                            any(Duration.class),
                            anyInt(),
                            anyBoolean(),
                            sslVerificationCaptor.capture(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                );
            // Assert that skipSslVerification defaults to false when null
            assertFalse("SSL verification should be enabled when null", sslVerificationCaptor.getValue());
        }
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
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
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

    @Test
    public void testHttpClientCacheInvalidation_CredentialRotation() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();

        ConnectorClientConfig clientConfig = new ConnectorClientConfig(
            10,
            10,
            10,
            1,
            1,
            0,
            RetryBackoffPolicy.CONSTANT,
            false, // skipSslVerification
            false, // mutualTlsEnabled - disabled to avoid certificate validation
            null,
            null
        );

        // Initial credentials
        Map<String, String> initialCredentials = new HashMap<>();
        initialCredentials.put("access_key", "initial-access-key");
        initialCredentials.put("secret_key", "initial-secret-key");

        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(clientConfig)
            .actions(Arrays.asList(predictAction))
            .credential(initialCredentials)
            .build();

        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));

        SdkAsyncHttpClient client1 = executor.getHttpClient();

        SdkAsyncHttpClient client2 = executor.getHttpClient();
        assertSame("HTTP client should be cached", client1, client2);

        Map<String, String> rotatedCredentials = new HashMap<>();
        rotatedCredentials.put("access_key", "rotated-access-key");
        rotatedCredentials.put("secret_key", "rotated-secret-key");

        HttpConnector rotatedConnector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(clientConfig)
            .actions(Arrays.asList(predictAction))
            .credential(rotatedCredentials)
            .build();

        HttpJsonConnectorExecutor rotatedExecutor = spy(new HttpJsonConnectorExecutor(rotatedConnector));

        SdkAsyncHttpClient client3 = rotatedExecutor.getHttpClient();
        assertNotSame("HTTP client should be recreated after credential rotation", client1, client3);
    }

    @Test
    public void testHttpClientCacheInvalidation_ConfigurationChange() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();

        ConnectorClientConfig initialConfig = new ConnectorClientConfig(
            10,  // maxConnections
            10,  // connectionTimeout
            10,  // readTimeout
            1,
            1,
            0,
            RetryBackoffPolicy.CONSTANT,
            false, // skipSslVerification
            false, // mutualTlsEnabled
            null,
            null
        );

        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(initialConfig)
            .actions(Arrays.asList(predictAction))
            .build();

        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));

        SdkAsyncHttpClient client1 = executor.getHttpClient();

        SdkAsyncHttpClient client2 = executor.getHttpClient();
        assertSame("HTTP client should be cached", client1, client2);

        ConnectorClientConfig updatedConfig = new ConnectorClientConfig(
            20,  // different maxConnections
            15,  // different connectionTimeout
            15,  // different readTimeout
            1,
            1,
            0,
            RetryBackoffPolicy.CONSTANT,
            true,  // different skipSslVerification
            false, // mutualTlsEnabled
            null,
            null
        );

        HttpConnector updatedConnector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(updatedConfig)
            .actions(Arrays.asList(predictAction))
            .build();

        HttpJsonConnectorExecutor updatedExecutor = spy(new HttpJsonConnectorExecutor(updatedConnector));

        SdkAsyncHttpClient client3 = updatedExecutor.getHttpClient();
        assertNotSame("HTTP client should be recreated after configuration change", client1, client3);
    }

    @Test
    public void testHttpClientCacheKey_Generation() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();

        ConnectorClientConfig clientConfig = new ConnectorClientConfig(
            10,
            10,
            10,
            1,
            1,
            0,
            RetryBackoffPolicy.CONSTANT,
            false,
            false, // mTLS disabled to avoid certificate validation
            null,
            null
        );

        Map<String, String> credentials = new HashMap<>();
        credentials.put("access_key", "test-access-key");
        credentials.put("secret_key", "test-secret-key");

        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(clientConfig)
            .actions(Arrays.asList(predictAction))
            .credential(credentials)
            .build();

        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);

        try {
            java.lang.reflect.Method method = HttpJsonConnectorExecutor.class.getDeclaredMethod("generateHttpClientCacheKey");
            method.setAccessible(true);

            String cacheKey1 = (String) method.invoke(executor);
            String cacheKey2 = (String) method.invoke(executor);

            assertEquals("Cache key should be consistent", cacheKey1, cacheKey2);

            assertTrue("Cache key should contain connection timeout", cacheKey1.contains("conn:10"));
            assertTrue("Cache key should contain read timeout", cacheKey1.contains("read:10"));
            assertTrue("Cache key should contain max connections", cacheKey1.contains("max:10"));
            assertTrue("Cache key should contain SSL settings", cacheKey1.contains("skipSsl:false"));
            assertTrue("Cache key should contain mTLS settings", cacheKey1.contains("mtls:false"));

        } catch (Exception e) {
            throw new RuntimeException("Failed to test cache key generation", e);
        }
    }

    @Test
    public void testHttpClientCacheInvalidation_MtlsToggle() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();

        ConnectorClientConfig configWithoutMtls = new ConnectorClientConfig(
            10,
            10,
            10,
            1,
            1,
            0,
            RetryBackoffPolicy.CONSTANT,
            false, // skipSslVerification
            false, // mutualTlsEnabled = false
            null,
            null
        );

        HttpConnector connectorWithoutMtls = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(configWithoutMtls)
            .actions(Arrays.asList(predictAction))
            .build();

        HttpJsonConnectorExecutor executorWithoutMtls = spy(new HttpJsonConnectorExecutor(connectorWithoutMtls));
        SdkAsyncHttpClient clientWithoutMtls = executorWithoutMtls.getHttpClient();

        ConnectorClientConfig configWithDifferentSettings = new ConnectorClientConfig(
            20,
            20,
            20,
            1,
            1,
            0, // Different connection settings
            RetryBackoffPolicy.CONSTANT,
            true,  // Different skipSslVerification
            false, // mutualTlsEnabled = false (to avoid certificate validation)
            null,
            null
        );

        HttpConnector connectorWithDifferentSettings = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(configWithDifferentSettings)
            .actions(Arrays.asList(predictAction))
            .build();

        HttpJsonConnectorExecutor executorWithDifferentSettings = spy(new HttpJsonConnectorExecutor(connectorWithDifferentSettings));
        SdkAsyncHttpClient clientWithDifferentSettings = executorWithDifferentSettings.getHttpClient();

        assertNotSame("HTTP client should be different when configuration is changed", clientWithoutMtls, clientWithDifferentSettings);
    }

    private MLInput createMLInput() {
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        return MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.REMOTE).build();
    }
}
