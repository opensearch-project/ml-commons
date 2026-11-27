/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class GoogleConnectorExecutorTest extends MLStaticMockBase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private ActionListener<Tuple<Integer, ModelTensors>> actionListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private GoogleCloudConnector adcConnector(String method, String url) {
        return adcConnector(method, url, Map.of());
    }

    private GoogleCloudConnector adcConnector(String method, String url, Map<String, String> stored) {
        Map<String, String> parameters = new HashMap<>(stored);
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method(method)
            .url(url)
            .requestBody("{\"contents\":[]}")
            .build();
        return GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
    }

    @Test
    public void invokeRemoteService_WrongHttpMethod() {
        GoogleCloudConnector connector = adcConnector("wrong_method", "https://us-central1-aiplatform.googleapis.com/v1/x:generateContent");
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        executor.invokeRemoteService(PREDICT.name(), null, null, null, null, actionListener);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        Mockito.verify(actionListener, times(1)).onFailure(captor.capture());
        assertEquals("unsupported http method", captor.getValue().getMessage());
    }

    private GoogleCredentialProvider fixedTokenProvider(String token) {
        return new GoogleCredentialProvider(null) {
            @Override
            public String getAccessToken() {
                return token;
            }
        };
    }

    private Client clientWithThreadContext() {
        Client osClient = mock(Client.class);
        ThreadPool tp = mock(ThreadPool.class);
        when(osClient.threadPool()).thenReturn(tp);
        when(tp.getThreadContext()).thenReturn(new ThreadContext(Settings.builder().build()));
        return osClient;
    }

    @Test
    public void invokeRemoteService_InjectsBearerToken() throws Exception {
        GoogleCloudConnector connector = adcConnector("POST", "https://us-central1-aiplatform.googleapis.com/v1/x:generateContent");
        // ADC mode has no credentials; decrypt just initializes the (empty) decrypted headers.
        connector
            .decrypt(PREDICT.name(), (keys, tenantId, listener) -> listener.onResponse(keys), null, ActionListener.wrap(r -> {}, e -> {}));

        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        executor.setCredentialProvider(fixedTokenProvider("test-token"));
        executor.setClient(clientWithThreadContext());

        SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
        doReturn(httpClient).when(executor).getHttpClient();
        ArgumentCaptor<AsyncExecuteRequest> captor = ArgumentCaptor.forClass(AsyncExecuteRequest.class);
        when(httpClient.execute(captor.capture())).thenReturn(CompletableFuture.completedFuture(null));

        executor.invokeRemoteService(PREDICT.name(), new MLInput(), new HashMap<>(), "{\"contents\":[]}", null, actionListener);

        String auth = captor.getValue().request().firstMatchingHeader("Authorization").orElse(null);
        assertEquals("Bearer test-token", auth);
    }

    @Test
    public void invokeRemoteService_BatchPredict_InjectsToken() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);
        ConnectorAction batchAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
            .method("POST")
            .url("https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1/batchPredictionJobs")
            .requestBody("{\"displayName\":\"job\"}")
            .build();
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .parameters(parameters)
            .actions(Arrays.asList(batchAction))
            .build();
        connector
            .decrypt(
                ConnectorAction.ActionType.BATCH_PREDICT.name(),
                (keys, tenantId, listener) -> listener.onResponse(keys),
                null,
                ActionListener.wrap(r -> {}, e -> {})
            );

        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        executor.setCredentialProvider(fixedTokenProvider("batch-token"));
        executor.setClient(clientWithThreadContext());

        SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
        doReturn(httpClient).when(executor).getHttpClient();
        ArgumentCaptor<AsyncExecuteRequest> captor = ArgumentCaptor.forClass(AsyncExecuteRequest.class);
        when(httpClient.execute(captor.capture())).thenReturn(CompletableFuture.completedFuture(null));

        executor
            .invokeRemoteService(
                ConnectorAction.ActionType.BATCH_PREDICT.name(),
                new MLInput(),
                new HashMap<>(),
                "{\"displayName\":\"job\"}",
                null,
                actionListener
            );

        assertEquals("Bearer batch-token", captor.getValue().request().firstMatchingHeader("Authorization").orElse(null));
    }

    @Test
    public void invokeRemoteServiceStream_UnsupportedInterface_Fails() {
        GoogleCloudConnector connector = adcConnector("POST", "https://us-central1-aiplatform.googleapis.com/v1/x:streamGenerateContent");
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "totally_unsupported");

        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> streamListener = mock(
            StreamPredictActionListener.class
        );

        executor.invokeRemoteServiceStream(PREDICT.name(), null, params, "{}", null, streamListener);
        Mockito.verify(streamListener, times(1)).onFailure(Mockito.any(Exception.class));
    }

    @Test
    public void invokeRemoteServiceStream_MissingInterface_Fails() {
        GoogleCloudConnector connector = adcConnector("POST", "https://us-central1-aiplatform.googleapis.com/v1/x:streamGenerateContent");
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));

        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> streamListener = mock(
            StreamPredictActionListener.class
        );

        executor.invokeRemoteServiceStream(PREDICT.name(), null, new HashMap<>(), "{}", null, streamListener);
        Mockito.verify(streamListener, times(1)).onFailure(Mockito.any(Exception.class));
    }

    @Test
    public void credentialProvider_LazilyBuildsOnceAndCaches() {
        // Every other test pre-injects a provider, so the lazy branch that calls
        // GoogleCredentialProvider.fromConnector is otherwise never exercised.
        GoogleCredentials adc = GoogleCredentials.create(new AccessToken("ya29.adc-token", new Date(Long.MAX_VALUE)));
        GoogleCloudConnector connector = adcConnector("POST", "https://us-central1-aiplatform.googleapis.com/v1/x:generateContent");
        connector
            .decrypt(PREDICT.name(), (keys, tenantId, listener) -> listener.onResponse(keys), null, ActionListener.wrap(r -> {}, e -> {}));
        GoogleConnectorExecutor executor = new GoogleConnectorExecutor(connector);

        try (MockedStatic<GoogleCredentials> mocked = mockStatic(GoogleCredentials.class)) {
            mocked.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);

            GoogleCredentialProvider first = executor.credentialProvider();
            assertSame(first, executor.credentialProvider());
            mocked.verify(GoogleCredentials::getApplicationDefault, times(1));
        }
    }

    @Test
    public void invokeRemoteService_HostOverrideRejectedBeforeClientCall() {
        GoogleCloudConnector connector = adcConnector("POST", "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/m/invoke");
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor
            .setTrustedConnectorEndpointsRegex(
                org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
                    .get(org.opensearch.common.settings.Settings.EMPTY)
            );
        SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
        org.mockito.Mockito.doReturn(httpClient).when(executor).getHttpClient();
        @SuppressWarnings("unchecked")
        ActionListener<Tuple<Integer, ModelTensors>> listener = mock(ActionListener.class);
        Map<String, String> overrides = new HashMap<>();
        overrides.put("region", "evil.example?x=a");

        executor.invokeRemoteService(PREDICT.name(), null, overrides, "{\"input\": \"x\"}", new ExecutionContext(0), listener);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(1)).onFailure(failure.capture());
        assertTrue(failure.getValue() instanceof IllegalArgumentException);
        assertTrue(failure.getValue().getMessage(), failure.getValue().getMessage().contains("not matching"));
        verify(httpClient, org.mockito.Mockito.never()).execute(any());
    }

    @Test
    public void invokeRemoteServiceStream_HostOverrideRejected() {
        GoogleCloudConnector connector = adcConnector("POST", "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/m/invoke");
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor
            .setTrustedConnectorEndpointsRegex(
                org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
                    .get(org.opensearch.common.settings.Settings.EMPTY)
            );
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );
        Map<String, String> overrides = new HashMap<>();
        overrides.put("region", "evil.example?x=a");
        overrides.put("_llm_interface", "openai/v1/chat/completions");

        executor.invokeRemoteServiceStream(PREDICT.name(), null, overrides, "{\"input\": \"x\"}", new ExecutionContext(0), listener);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(1)).onFailure(failure.capture());
        Throwable cause = failure.getValue() instanceof IllegalArgumentException ? failure.getValue() : failure.getValue().getCause();
        assertTrue(String.valueOf(cause), cause instanceof IllegalArgumentException && cause.getMessage().contains("not matching"));
    }

    /** A Vertex AI connector whose host takes the stored location. */
    private GoogleCloudConnector storedLocationConnector() {
        return adcConnector(
            "POST",
            "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/p/locations/${parameters.location}"
                + "/publishers/google/models/gemini:generateContent",
            Map.of("location", "us-central1")
        );
    }

    @Test
    public void invokeRemoteService_requestParametersCannotChangeLocationInHost() {
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(storedLocationConnector()));
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        GoogleCredentialProvider credentialProvider = mock(GoogleCredentialProvider.class);
        executor.setCredentialProvider(credentialProvider);
        SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
        doReturn(httpClient).when(executor).getHttpClient();

        executor.invokeRemoteService(PREDICT.name(), null, Map.of("location", "europe-west4"), "{\"contents\":[]}", null, actionListener);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(failure.capture());
        assertEquals(Connector.ACTION_URL_HOST_CHANGED_MESSAGE, failure.getValue().getMessage());
        verify(credentialProvider, never()).getAccessToken();
        verify(httpClient, never()).execute(any());
    }

    @Test
    public void invokeRemoteServiceStream_requestParametersCannotChangeLocationInHost() {
        GoogleConnectorExecutor executor = new GoogleConnectorExecutor(storedLocationConnector());
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        GoogleCredentialProvider credentialProvider = mock(GoogleCredentialProvider.class);
        executor.setCredentialProvider(credentialProvider);
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );
        Map<String, String> parameters = Map.of("location", "europe-west4", "_llm_interface", "gemini/v1beta/generateContent");

        executor.invokeRemoteServiceStream(PREDICT.name(), null, parameters, "{\"contents\":[]}", null, listener);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(failure.capture());
        assertEquals(Connector.ACTION_URL_HOST_CHANGED_MESSAGE, failure.getValue().getCause().getMessage());
        verify(credentialProvider, never()).getAccessToken();
    }

    @Test
    public void invokeRemoteServiceStream_privateDestinationFollowsPrivateIpSetting() {
        GoogleCloudConnector connector = adcConnector("POST", "http://10.0.0.1/v1/x:streamGenerateContent");
        connector
            .decrypt(PREDICT.name(), (keys, tenantId, listener) -> listener.onResponse(keys), null, ActionListener.wrap(r -> {}, e -> {}));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "gemini/v1beta/generateContent");

        GoogleConnectorExecutor off = new GoogleConnectorExecutor(connector);
        off.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://10\\.0\\.0\\.1/.*$"));
        off.setCredentialProvider(fixedTokenProvider("stream-token"));
        off.setConnectorPrivateIpEnabled(false);
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> l1 = mock(StreamPredictActionListener.class);
        off.invokeRemoteServiceStream(PREDICT.name(), null, new HashMap<>(params), "{\"input\": \"x\"}", new ExecutionContext(0), l1);
        ArgumentCaptor<Exception> f1 = ArgumentCaptor.forClass(Exception.class);
        verify(l1, times(1)).onFailure(f1.capture());
        assertTrue(String.valueOf(f1.getValue().getCause()), f1.getValue().getCause().getMessage().contains("private ip address"));

        GoogleConnectorExecutor on = new GoogleConnectorExecutor(connector);
        on.setTrustedConnectorEndpointsRegex(Arrays.asList("^http://10\\.0\\.0\\.1/.*$"));
        on.setCredentialProvider(fixedTokenProvider("stream-token"));
        on.setConnectorPrivateIpEnabled(true);
        on.setConnectorTrustedPrivateEndpoints(Arrays.asList(java.util.regex.Pattern.compile("^http://10\\.0\\.0\\.2/.*$")));
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> l2 = mock(StreamPredictActionListener.class);
        on.invokeRemoteServiceStream(PREDICT.name(), null, new HashMap<>(params), "{\"input\": \"x\"}", new ExecutionContext(0), l2);
        ArgumentCaptor<Exception> f2 = ArgumentCaptor.forClass(Exception.class);
        verify(l2, times(1)).onFailure(f2.capture());
        assertTrue(
            String.valueOf(f2.getValue().getCause()),
            f2.getValue().getCause().getMessage().contains("trusted connector private endpoint")
        );
    }

    @Test
    public void invokeRemoteServiceStream_passesTheCheckedUriAndTheBearerHeader() throws Exception {
        GoogleConnectorExecutor executor = new GoogleConnectorExecutor(storedLocationConnector());
        executor.setTrustedConnectorEndpointsRegex(Arrays.asList("^https://.*-aiplatform\\.googleapis\\.com/.*$"));
        GoogleCredentialProvider credentialProvider = mock(GoogleCredentialProvider.class);
        when(credentialProvider.getAccessToken()).thenReturn("token-1");
        executor.setCredentialProvider(credentialProvider);
        org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandler handler = mock(
            org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandler.class
        );
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );
        Map<String, String> parameters = Map.of("location", "us-central1", "_llm_interface", "gemini/v1beta/generateContent");
        try (
            MockedStatic<org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory> factory = mockStatic(
                org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory.class
            )
        ) {
            factory
                .when(
                    () -> org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory
                        .createHandler(any(), any(), any(), any(), any(), any())
                )
                .thenReturn(handler);

            executor.invokeRemoteServiceStream(PREDICT.name(), null, parameters, "{\"contents\":[]}", null, listener);
        }

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(handler)
            .startStream(
                org.mockito.ArgumentMatchers
                    .eq(
                        java.net.URI
                            .create(
                                "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1"
                                    + "/publishers/google/models/gemini:generateContent"
                            )
                    ),
                headers.capture(),
                any(),
                any(),
                any()
            );
        assertEquals("Bearer token-1", headers.getValue().get("Authorization"));
    }
}
