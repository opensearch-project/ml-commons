/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_PREDICT;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleServiceAccountConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.serviceAccountCredential;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProvider;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProviderFactory;
import org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.FakeAuthorizationProvider;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandler;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

/** The google_cloud executor is the http executor: the bearer its provider issues, the retry and the stream come from there. */
@SuppressWarnings("unchecked")
public class GoogleConnectorExecutorTest extends MLStaticMockBase {

    private static final List<String> TRUSTED_VERTEX_URLS = List.of("^https://.*-aiplatform\\.googleapis\\.com/.*$");
    private static final String PREDICT_URL = "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1"
        + "/publishers/google/models/gemini:generateContent";
    private static final String BATCH_URL = "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1"
        + "/batchPredictionJobs";

    private final ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
    private final ThreadPool threadPool = mock(ThreadPool.class);
    private final Client client = mock(Client.class);
    private final SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
    /** The model requests the remote service received. */
    private final List<SdkHttpFullRequest> sent = new CopyOnWriteArrayList<>();
    /** The statuses the remote service answers with, in order; the last one is repeated. */
    private final Queue<Integer> statuses = new ArrayDeque<>(List.of(200));

    @Before
    public void setUp() {
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(OpenSearchExecutors.newDirectExecutorService());
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        });
        when(httpClient.execute(any())).thenAnswer(invocation -> {
            AsyncExecuteRequest request = invocation.getArgument(0);
            sent.add((SdkHttpFullRequest) request.request());
            int status;
            synchronized (statuses) {
                status = statuses.size() > 1 ? statuses.remove() : statuses.element();
            }
            respond(request.responseHandler(), status, "{\"answer\":" + sent.size() + "}");
            return CompletableFuture.completedFuture(null);
        });
    }

    private static void respond(SdkAsyncHttpResponseHandler handler, int status, String body) {
        handler.onHeaders(SdkHttpFullResponse.builder().statusCode(status).build());
        handler.onStream(subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
            subscriber.onComplete();
        });
    }

    private GoogleConnectorExecutor executor(GoogleCloudConnector connector, AuthorizationProvider provider) {
        AuthorizationProviderFactory factory = mock(AuthorizationProviderFactory.class);
        when(factory.providerFor(connector)).thenReturn(provider);
        GoogleConnectorExecutor executor = spy(new GoogleConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(TRUSTED_VERTEX_URLS);
        executor.setClient(client);
        doReturn(httpClient).when(executor).getHttpClient();
        executor.setAuthorizationProviderFactory(factory);
        return executor;
    }

    /** A legacy service account connector whose host takes the stored location. */
    private static GoogleCloudConnector storedLocationConnector() {
        return googleConnector(
            Map.of("location", "us-central1"),
            serviceAccountCredential(null),
            null,
            "https://${parameters.location}-aiplatform.googleapis.com/v1/projects/p/locations/${parameters.location}"
                + "/publishers/google/models/gemini:generateContent"
        );
    }

    /** A legacy service account connector with one batch_predict action. */
    private static GoogleCloudConnector batchConnector() {
        GoogleCloudConnector connector = GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("gcp")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(serviceAccountCredential(null))
            .actions(
                List
                    .of(
                        ConnectorAction
                            .builder()
                            .actionType(BATCH_PREDICT)
                            .method("POST")
                            .url(BATCH_URL)
                            .requestBody("{\"displayName\":\"job\"}")
                            .build()
                    )
            )
            .build();
        connector
            .decrypt(
                BATCH_PREDICT.name(),
                (values, tenantId, listener) -> listener.onResponse(values),
                null,
                ActionListener.wrap(r -> {}, e -> {
                    throw new AssertionError(e);
                })
            );
        return connector;
    }

    private static ActionListener<Tuple<Integer, ModelTensors>> listener() {
        return mock(ActionListener.class);
    }

    private static void invoke(
        GoogleConnectorExecutor executor,
        ConnectorAction.ActionType action,
        Map<String, String> parameters,
        ActionListener<Tuple<Integer, ModelTensors>> l
    ) {
        executor.invokeRemoteService(action.name(), null, new HashMap<>(parameters), "{}", new ExecutionContext(0), l);
    }

    private static List<String> authorization(SdkHttpFullRequest request) {
        return request
            .headers()
            .entrySet()
            .stream()
            .filter(header -> header.getKey().equalsIgnoreCase("Authorization"))
            .flatMap(header -> header.getValue().stream())
            .toList();
    }

    private static void assertSucceeded(ActionListener<?> l) {
        verify(l).onResponse(any());
        verify(l, never()).onFailure(any());
    }

    @Test
    public void aPredictRequest_carriesTheBearerTheProviderIssued() {
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        invoke(executor(googleServiceAccountConnector(PREDICT_URL), new FakeAuthorizationProvider("tok-1")), PREDICT, Map.of(), l);

        assertSucceeded(l);
        assertEquals(1, sent.size());
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));
        assertEquals(URI.create(PREDICT_URL), sent.get(0).getUri());
    }

    @Test
    public void aStoredAuthorizationHeader_isReplacedByTheBearer_notSentBesideIt() {
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();
        GoogleCloudConnector connector = googleConnector(
            null,
            serviceAccountCredential(null),
            null,
            PREDICT_URL,
            null,
            Map.of("authorization", "Bearer stale")
        );

        invoke(executor(connector, new FakeAuthorizationProvider("tok-1")), PREDICT, Map.of(), l);

        assertSucceeded(l);
        assertEquals(1, sent.size());
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));
    }

    @Test
    public void aBatchPredictRequest_carriesTheBearer() {
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        invoke(executor(batchConnector(), new FakeAuthorizationProvider("batch-token")), BATCH_PREDICT, Map.of(), l);

        assertSucceeded(l);
        assertEquals(1, sent.size());
        assertEquals(List.of("Bearer batch-token"), authorization(sent.get(0)));
        assertEquals(URI.create(BATCH_URL), sent.get(0).getUri());
    }

    @Test
    public void aStreamWithTheGeminiInterface_isAccepted_andCarriesTheBearer() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1");
        GoogleConnectorExecutor executor = executor(googleServiceAccountConnector(PREDICT_URL), provider);
        StreamingHandler handler = mock(StreamingHandler.class);
        StreamPredictActionListener<MLTaskResponse, ?> l = mock(StreamPredictActionListener.class);
        ArgumentCaptor<String> interfaces = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        // Written as the blueprints write it, in camel case: the executor lower-cases the value before the lookup.
        Map<String, String> parameters = new HashMap<>(Map.of(LLM_INTERFACE, "gemini/v1beta/generateContent"));

        // Only the handler is replaced; the factory's other statics keep their behaviour.
        try (
            MockedStatic<StreamingHandlerFactory> handlers = mockStatic(
                StreamingHandlerFactory.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS)
            )
        ) {
            handlers.when(() -> StreamingHandlerFactory.createHandler(any(), any(), any(), any(), any(), any(), any())).thenReturn(handler);
            // Stubbing ran the real method once; only the executor's call is counted.
            handlers.clearInvocations();
            executor.invokeRemoteServiceStream(PREDICT.name(), null, parameters, "{}", new ExecutionContext(0), l);
            handlers.verify(() -> StreamingHandlerFactory.createHandler(interfaces.capture(), any(), any(), any(), any(), any(), any()));
        }

        verify(l, never()).onFailure(any());
        assertEquals(LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT, interfaces.getValue());
        verify(handler).startStream(eq(URI.create(PREDICT_URL)), headers.capture(), any(), any(), any());
        assertEquals("Bearer tok-1", headers.getValue().get("Authorization"));
    }

    @Test
    public void a401_isSentAgainOnceWithTheNextToken() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1", "tok-2");
        statuses.clear();
        statuses.addAll(List.of(401, 200));
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        invoke(executor(googleServiceAccountConnector(PREDICT_URL), provider), PREDICT, Map.of(), l);

        assertSucceeded(l);
        assertEquals(2, sent.size());
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));
        assertEquals(List.of("Bearer tok-2"), authorization(sent.get(1)));
        assertEquals(List.of(provider.issued.get(0)), provider.invalidated);
    }

    @Test
    public void requestParametersCannotChangeTheLocationInTheHost_andNoTokenIsRequestedForSuchARequest() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1");
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        invoke(executor(storedLocationConnector(), provider), PREDICT, Map.of("location", "europe-west4"), l);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(l).onFailure(failure.capture());
        assertEquals(Connector.ACTION_URL_HOST_CHANGED_MESSAGE, failure.getValue().getMessage());
        assertEquals(0, provider.requests.get());
        assertEquals(List.of(), sent);
    }
}
