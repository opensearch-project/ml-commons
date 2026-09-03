/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import java.util.Collections;
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
import org.opensearch.common.unit.TimeValue;
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
        executor.setConnectorTrustedPrivateEndpoints(Collections.emptyList());
        executor.setConnectorRestrictedIpPatterns(Collections.emptyList());
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://.*$"));
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
        executor.setConnectorTrustedPrivateEndpoints(Collections.emptyList());
        executor.setConnectorRestrictedIpPatterns(Collections.emptyList());
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://.*$"));
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
        executor.setConnectorTrustedPrivateEndpoints(Collections.emptyList());
        executor.setConnectorRestrictedIpPatterns(Collections.emptyList());
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://.*$"));
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
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
                            any(),
                            any(),
                            anyBoolean(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
                            any(),
                            any(),
                            sslVerificationCaptor.capture(),
                            any(),
                            any()
                        )
                );
            // Assert that skipSslVerification was set to true
            assertTrue("SSL verification should be disabled", sslVerificationCaptor.getValue());
        }
    }

    @Test
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
                            any(),
                            any(),
                            anyBoolean(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
                            any(),
                            any(),
                            sslVerificationCaptor.capture(),
                            any(),
                            any()
                        )
                );
            // Assert that skipSslVerification was set to false
            assertFalse("SSL verification should be enabled", sslVerificationCaptor.getValue());
        }
    }

    @Test
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
                            any(),
                            any(),
                            anyBoolean(),
                            any(),
                            any()
                        )
                )
                .thenReturn(mockClient);

            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
                            any(),
                            any(),
                            sslVerificationCaptor.capture(),
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
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://openai\\.com/.*$"));
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
    // Invalidation itself is covered in MLHttpClientCacheManagerTests, which drives one cache
    // manager with changing configuration. Here we only assert the cache-hit path.
    public void testGetHttpClient_SameExecutor_ReturnsCachedClient() {
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

        // Access the cache manager directly to test the generateHttpClientCacheKey method
        MLHttpClientCacheManager cacheManager = new MLHttpClientCacheManager();

        String cacheKey1 = cacheManager.generateHttpClientCacheKey(connector, clientConfig);
        String cacheKey2 = cacheManager.generateHttpClientCacheKey(connector, clientConfig);

        assertEquals("Cache key should be consistent", cacheKey1, cacheKey2);

        assertTrue("Cache key should contain connection timeout", cacheKey1.contains("conn:10"));
        assertTrue("Cache key should contain read timeout", cacheKey1.contains("read:10"));
        assertTrue("Cache key should contain max connections", cacheKey1.contains("max:10"));
        assertTrue("Cache key should contain SSL settings", cacheKey1.contains("skipSsl:false"));
        assertTrue("Cache key should contain mTLS settings", cacheKey1.contains("mtls:false"));
    }

    /** Builds an mTLS connector whose credentials hold both PEM and PKCS12 material. */
    private HttpConnector mtlsConnectorWithBothCertFormats(ConnectorClientConfig clientConfig) {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("hello world")
            .build();

        // Validation only checks that the material for the *configured* keystore type is present, so a
        // credential map may legitimately carry both formats. That makes keystoreType the only thing
        // distinguishing these two configurations.
        Map<String, String> credentials = new HashMap<>();
        credentials.put("client_cert_pem", "-----BEGIN CERTIFICATE-----\nCert\n-----END CERTIFICATE-----");
        credentials.put("client_key_pem", "-----BEGIN PRIVATE KEY-----\nKey\n-----END PRIVATE KEY-----");
        credentials.put("client_cert_pkcs12", "MIIKjwIBAzCCCkUGCSqGSIb3DQEHAaCCCjYEggoy");
        credentials.put("keystore_password", "testpass");

        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .connectorClientConfig(clientConfig)
            .actions(Arrays.asList(predictAction))
            .credential(credentials)
            .build();
        connector.setDecryptedCredential(credentials);
        return connector;
    }

    private ConnectorClientConfig mtlsConfig(String keystoreType) {
        return new ConnectorClientConfig(10, 10, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT, false, true, keystoreType);
    }

    @Test
    public void testHttpClientCacheKey_KeystoreTypeChange_ProducesDifferentKey() {
        MLHttpClientCacheManager cacheManager = new MLHttpClientCacheManager();

        ConnectorClientConfig pemConfig = mtlsConfig("PEM");
        ConnectorClientConfig pkcs12Config = mtlsConfig("PKCS12");

        String pemKey = cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(pemConfig), pemConfig);
        String pkcs12Key = cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(pkcs12Config), pkcs12Config);

        assertTrue("Cache key should record the keystore type", pemKey.contains("ksType:PEM"));
        assertTrue("Cache key should record the keystore type", pkcs12Key.contains("ksType:PKCS12"));
        assertNotEquals(
            "Switching PEM -> PKCS12 must invalidate the cached client even when credentials are byte-identical",
            pemKey,
            pkcs12Key
        );
    }

    @Test
    public void testHttpClientCacheKey_EquivalentKeystoreTypeSpellings_ProduceSameKey() {
        MLHttpClientCacheManager cacheManager = new MLHttpClientCacheManager();

        // KeystoreType.from is case-insensitive and defaults null to PEM, so these must not cause
        // spurious cache misses that would needlessly rebuild the client.
        ConnectorClientConfig upperConfig = mtlsConfig("PKCS12");
        ConnectorClientConfig lowerConfig = mtlsConfig("pkcs12");
        ConnectorClientConfig nullConfig = mtlsConfig(null);
        ConnectorClientConfig pemConfig = mtlsConfig("PEM");

        assertEquals(
            "Keystore type should be normalised before entering the cache key",
            cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(upperConfig), upperConfig),
            cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(lowerConfig), lowerConfig)
        );
        assertEquals(
            "A null keystore type defaults to PEM and should key the same as an explicit PEM",
            cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(pemConfig), pemConfig),
            cacheManager.generateHttpClientCacheKey(mtlsConnectorWithBothCertFormats(nullConfig), nullConfig)
        );
    }

    @Test
    public void testClientCloseGracePeriod_ShortTimeouts_UsesMinimumFloor() {
        // connect + read = 15s, below the floor, so the floor wins.
        ConnectorClientConfig config = new ConnectorClientConfig(10, 5, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT, false, false, null);

        assertEquals(
            "Short timeouts should not shrink the grace period below the floor",
            MLHttpClientCacheManager.MIN_CLIENT_CLOSE_GRACE_PERIOD,
            MLHttpClientCacheManager.clientCloseGracePeriod(config)
        );
    }

    @Test
    public void testClientCloseGracePeriod_LongReadTimeout_ExceedsMinimumFloor() {
        // connect 10s + read 300s: a request may legitimately still be running well past the 30s floor,
        // so the grace period must stretch to cover it rather than closing the pool mid-flight.
        ConnectorClientConfig config = new ConnectorClientConfig(10, 300, 10, 1, 1, 0, RetryBackoffPolicy.CONSTANT, false, false, null);

        TimeValue gracePeriod = MLHttpClientCacheManager.clientCloseGracePeriod(config);

        assertEquals("Grace period should cover connect + read", TimeValue.timeValueSeconds(310), gracePeriod);
        assertTrue(
            "Grace period must exceed the floor when the configured timeouts do",
            gracePeriod.seconds() > MLHttpClientCacheManager.MIN_CLIENT_CLOSE_GRACE_PERIOD.seconds()
        );
    }

    @Test
    public void testClientCloseGracePeriod_NullTimeouts_UsesMinimumFloor() {
        ConnectorClientConfig config = new ConnectorClientConfig(
            null,
            null,
            null,
            null,
            null,
            null,
            RetryBackoffPolicy.CONSTANT,
            null,
            null,
            null
        );

        assertEquals(
            "Null timeouts must not produce a zero-length grace period",
            MLHttpClientCacheManager.MIN_CLIENT_CLOSE_GRACE_PERIOD,
            MLHttpClientCacheManager.clientCloseGracePeriod(config)
        );
        assertEquals(
            "A null config must not throw",
            MLHttpClientCacheManager.MIN_CLIENT_CLOSE_GRACE_PERIOD,
            MLHttpClientCacheManager.clientCloseGracePeriod(null)
        );
    }

    @Test
    public void invokeRemoteService_predictTimeUrlOverride_blocked() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://${parameters.endpoint}/v1/chat/completions")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(ImmutableMap.of("endpoint", "api.openai.com"))
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setConnectorPrivateIpEnabled(true);
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://api\\.openai\\.com/.*$"));
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        Map<String, String> overrideParams = new HashMap<>();
        overrideParams.put("endpoint", "attacker.example.com/anything?");
        overrideParams.put("input", "hello");

        executor
            .invokeRemoteService(
                PREDICT.name(),
                createMLInput(),
                overrideParams,
                "{\"input\": \"hello\"}",
                new ExecutionContext(0),
                actionListener
            );

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertEquals("Connector URL is not matching the trusted connector endpoint regex", captor.getValue().getMessage());
    }

    @Test
    public void invokeRemoteService_predictTimeUrlOverride_allowedHost_passesValidation() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://${parameters.endpoint}/v1/chat/completions")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(ImmutableMap.of("endpoint", "api.openai.com"))
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setConnectorPrivateIpEnabled(true);
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://api\\.openai\\.com/.*$"));
        executor.setClient(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        Map<String, String> params = new HashMap<>();
        params.put("endpoint", "api.openai.com");
        params.put("input", "hello");

        executor
            .invokeRemoteService(
                PREDICT.name(),
                createMLInput(),
                params,
                "{\"input\": \"hello\"}",
                new ExecutionContext(0),
                actionListener
            );

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(actionListener, Mockito.atMost(1)).onFailure(captor.capture());
        for (Exception e : captor.getAllValues()) {
            assertFalse(
                "Validator must not reject a resolved URL that matches the allowlist",
                "Connector URL is not matching the trusted connector endpoint regex".equals(e.getMessage())
            );
        }
    }

    private MLInput createMLInput() {
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        return MLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.REMOTE).build();
    }
}
