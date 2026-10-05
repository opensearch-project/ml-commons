/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

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
        Map<String, String> parameters = new HashMap<>();
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
}
