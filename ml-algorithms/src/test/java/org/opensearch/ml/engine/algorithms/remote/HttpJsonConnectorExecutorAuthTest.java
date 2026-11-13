/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.AUTH_CONFIG_FIELD;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.ACTION_HEADERS;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.TOKEN_RESPONSE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.block;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clusterSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.featureSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.oauth2Connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.reply;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.scriptedTransport;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.tokenClient;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProvider;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProviderFactory;
import org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures;
import org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.FakeAuthorizationProvider;
import org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandler;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.reactivestreams.Subscription;

import okhttp3.Request;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

@SuppressWarnings("unchecked")
public class HttpJsonConnectorExecutorAuthTest extends MLStaticMockBase {

    private static final String ACTION_URL = "https://203.0.113.20/v1/chat/completions";
    private static final List<String> TRUSTED_ACTION_URLS = List.of("^https?://203\\.0\\.113\\.20/.*$");
    private static final String SECOND_TOKEN_RESPONSE = TOKEN_RESPONSE.replace("tok-1", "tok-2");

    private final ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
    private final ThreadPool threadPool = mock(ThreadPool.class);
    private final Client client = mock(Client.class);
    private final SdkAsyncHttpClient httpClient = mock(SdkAsyncHttpClient.class);
    /** The model requests the remote service received. */
    private final List<SdkHttpFullRequest> sent = new CopyOnWriteArrayList<>();
    /** The statuses the remote service answers with, in order; the last one is repeated. */
    private final Queue<Integer> statuses = new ArrayDeque<>(List.of(200));
    /** The node cache's clock and the requests the token endpoint received, for the tests over the real token cache. */
    private final AtomicLong clock = new AtomicLong();
    private final List<Request> tokenRequests = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(OpenSearchExecutors.newDirectExecutorService());
        // A retry runs at once.
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

    private void remoteAnswers(Integer... answers) {
        statuses.clear();
        statuses.addAll(List.of(answers));
    }

    private static void respond(SdkAsyncHttpResponseHandler handler, int status, String body) {
        handler.onHeaders(SdkHttpFullResponse.builder().statusCode(status).build());
        handler.onStream(subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));
            subscriber.onNext(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
            subscriber.onComplete();
        });
    }

    private HttpJsonConnectorExecutor executor(HttpConnector connector, AuthorizationProvider provider) {
        AuthorizationProviderFactory factory = mock(AuthorizationProviderFactory.class);
        when(factory.providerFor(connector)).thenReturn(provider);
        return executor(connector, factory);
    }

    private HttpJsonConnectorExecutor executor(HttpConnector connector, AuthorizationProviderFactory factory) {
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setTrustedConnectorEndpointsRegex(TRUSTED_ACTION_URLS);
        executor.setClient(client);
        doReturn(httpClient).when(executor).getHttpClient();
        executor.setAuthorizationProviderFactory(factory);
        return executor;
    }

    private static ActionListener<Tuple<Integer, ModelTensors>> listener() {
        return mock(ActionListener.class);
    }

    private static void predict(
        HttpJsonConnectorExecutor executor,
        Map<String, String> parameters,
        ActionListener<Tuple<Integer, ModelTensors>> l
    ) {
        executor.invokeRemoteService(PREDICT.name(), null, parameters, "{}", new ExecutionContext(0), l);
    }

    private static void predict(HttpJsonConnectorExecutor executor, ActionListener<Tuple<Integer, ModelTensors>> l) {
        predict(executor, new HashMap<>(), l);
    }

    private static void stream(HttpJsonConnectorExecutor executor, StreamPredictActionListener<MLTaskResponse, ?> l) {
        Map<String, String> parameters = new HashMap<>(Map.of(LLM_INTERFACE, LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS));
        executor.invokeRemoteServiceStream(PREDICT.name(), null, parameters, "{}", new ExecutionContext(0), l);
    }

    /** The only completion of {@code l}, a failure. */
    private static Exception failureOf(ActionListener<?> l) {
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(l).onFailure(failure.capture());
        verify(l, never()).onResponse(any());
        return failure.getValue();
    }

    /** The failure a stream listener received. */
    private static Exception streamFailureOf(StreamPredictActionListener<MLTaskResponse, ?> l) {
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(l).onFailure(failure.capture());
        return failure.getValue();
    }

    /** {@code l} completed once, with a response. */
    private static void assertSucceeded(ActionListener<?> l) {
        verify(l).onResponse(any());
        verify(l, never()).onFailure(any());
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

    private static void assertUnauthorized(Exception e, int answer) {
        assertEquals(RestStatus.UNAUTHORIZED, ((OpenSearchStatusException) e).status());
        assertTrue(e.getMessage(), e.getMessage().endsWith("{\"answer\":" + answer + "}"));
    }

    @Test
    public void aRequestCarriesTheBearerOnceNextToTheConnectorHeaders() {
        HttpConnector connector = oauth2Connector(ACTION_URL);
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        predict(executor(connector, new FakeAuthorizationProvider("tok-1")), l);

        assertSucceeded(l);
        assertEquals(1, sent.size());
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));
        assertEquals(List.of("1"), sent.get(0).headers().get("x-api-version"));
        assertEquals(ACTION_HEADERS, connector.getDecryptedHeaders());
    }

    @Test
    public void onlyTheConnectorsAuthConfigDecidesWhetherATokenIsAttached() {
        HttpConnector oauth2 = oauth2Connector(ACTION_URL);
        AuthorizationProviderFactory factory = mock(AuthorizationProviderFactory.class);
        when(factory.providerFor(oauth2)).thenReturn(new FakeAuthorizationProvider("tok-1"));
        Map<String, String> none = new HashMap<>(Map.of(AUTH_CONFIG_FIELD, "{\"type\":\"none\"}", "auth", "{\"type\":\"none\"}"));
        ActionListener<Tuple<Integer, ModelTensors>> l1 = listener();

        predict(executor(oauth2, factory), none, l1);

        assertSucceeded(l1);
        verify(factory).providerFor(same(oauth2));
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));

        // No auth_config, a none one, or an oauth2 block in parameters.auth, which is an ordinary parameter.
        String oauth2Block = block(PUBLIC_TOKEN_URL, CLIENT_AUTH_METHOD_CLIENT_SECRET_POST, "");
        HttpConnector authParameter = connector(null, ACTION_URL);
        authParameter.getParameters().put("auth", oauth2Block);
        Map<String, String> oauth2Request = new HashMap<>(Map.of(AUTH_CONFIG_FIELD, oauth2Block, "auth", oauth2Block));
        for (HttpConnector plain : List.of(connector(null, ACTION_URL), connector(OAuth2AuthConfig.NONE, ACTION_URL), authParameter)) {
            AuthorizationProviderFactory untouched = mock(AuthorizationProviderFactory.class);
            ActionListener<Tuple<Integer, ModelTensors>> l2 = listener();
            sent.clear();

            predict(executor(plain, untouched), new HashMap<>(oauth2Request), l2);

            assertSucceeded(l2);
            verifyNoInteractions(untouched);
            assertEquals(List.of(), authorization(sent.get(0)));
        }
    }

    @Test
    public void aCleartextActionUrlIsRefusedBeforeATokenIsRequestedForRequestsAndStreams() {
        AuthorizationProviderFactory factory = mock(AuthorizationProviderFactory.class);
        HttpJsonConnectorExecutor executor = executor(oauth2Connector("http://203.0.113.20/v1/chat/completions"), factory);
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();
        StreamPredictActionListener<MLTaskResponse, ?> streamListener = mock(StreamPredictActionListener.class);

        predict(executor, l);
        try (MockedStatic<StreamingHandlerFactory> handlers = mockStatic(StreamingHandlerFactory.class)) {
            stream(executor, streamListener);
            handlers.verifyNoInteractions();
        }

        for (Exception e : List.of(failureOf(l), streamFailureOf(streamListener))) {
            assertSame(IllegalArgumentException.class, e.getClass());
            assertEquals(AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE, e.getMessage());
        }
        verifyNoInteractions(factory);
        assertEquals(List.of(), sent);
    }

    @Test
    public void aTokenObtainedBeforeConnectorAuthWasDisabledIsNotSent() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider(() -> false, "tok-1", "tok-2");
        HttpJsonConnectorExecutor executor = executor(oauth2Connector(ACTION_URL), provider);
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();
        StreamPredictActionListener<MLTaskResponse, ?> streamListener = mock(StreamPredictActionListener.class);

        predict(executor, l);
        try (MockedStatic<StreamingHandlerFactory> handlers = mockStatic(StreamingHandlerFactory.class)) {
            stream(executor, streamListener);
            handlers.verifyNoInteractions();
        }

        OpenSearchStatusException predictFailure = (OpenSearchStatusException) failureOf(l);
        OpenSearchStatusException streamFailure = (OpenSearchStatusException) streamFailureOf(streamListener);
        for (OpenSearchStatusException e : List.of(predictFailure, streamFailure)) {
            assertEquals(RestStatus.FORBIDDEN, e.status());
            assertEquals(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, e.getMessage());
        }
        assertEquals(List.of(), sent);
    }

    @Test
    public void aRefusedProviderIsNotKept() {
        HttpConnector connector = oauth2Connector(ACTION_URL);
        AuthorizationProviderFactory factory = mock(AuthorizationProviderFactory.class);
        when(factory.providerFor(connector)).thenThrow(new IllegalArgumentException("refused"));
        HttpJsonConnectorExecutor executor = executor(connector, factory);

        for (int i = 0; i < 2; i++) {
            ActionListener<Tuple<Integer, ModelTensors>> l = listener();
            predict(executor, l);
            assertEquals("refused", failureOf(l).getMessage());
        }

        verify(factory, times(2)).providerFor(connector);
        assertEquals(List.of(), sent);
    }

    /** {@link #oauth2Connector} whose client config retries three times after a millisecond. */
    private static HttpConnector retryingConnector() {
        HttpConnector connector = oauth2Connector(ACTION_URL);
        connector.setConnectorClientConfig(new ConnectorClientConfig().toBuilder().maxRetryTimes(3).retryBackoffMillis(1).build());
        return connector;
    }

    private static void predictWithRetry(HttpJsonConnectorExecutor executor, ActionListener<Tuple<Integer, ModelTensors>> l) {
        executor.invokeRemoteServiceWithRetry(PREDICT.name(), null, new HashMap<>(), "{}", new ExecutionContext(0), l);
    }

    @Test
    public void aFailedTokenRequestIsNotRetried() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider(new MLException("token request failed"));
        HttpJsonConnectorExecutor executor = executor(retryingConnector(), provider);
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        predictWithRetry(executor, l);

        assertEquals("token request failed", failureOf(l).getMessage());
        verify(executor, times(1)).invokeRemoteService(any(), any(), any(), any(), any(), any());
        assertEquals(1, provider.requests.get());
        assertEquals(List.of(), sent);
    }

    @Test
    public void a401IsSentAgainOnceWithANewTokenWhenTheProviderDroppedTheOldOne() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1", "tok-2");
        remoteAnswers(401, 200);
        ActionListener<Tuple<Integer, ModelTensors>> l = listener();

        predict(executor(oauth2Connector(ACTION_URL), provider), l);

        assertSucceeded(l);
        assertEquals(List.of("Bearer tok-1"), authorization(sent.get(0)));
        assertEquals(List.of("Bearer tok-2"), authorization(sent.get(1)));
        // The handle the rejected request carried, not the provider's newer one.
        assertEquals(1, provider.invalidated.size());
        assertSame(provider.issued.get(0), provider.invalidated.get(0));
    }

    @Test
    public void a403OrAKeptTokenEndsTheRequest() {
        FakeAuthorizationProvider forbidden = new FakeAuthorizationProvider("tok-1", "tok-2");
        remoteAnswers(403);
        ActionListener<Tuple<Integer, ModelTensors>> l1 = listener();

        predict(executor(oauth2Connector(ACTION_URL), forbidden), l1);

        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) failureOf(l1)).status());
        assertEquals(1, sent.size());
        assertEquals(List.of(), forbidden.invalidated);

        FakeAuthorizationProvider keeping = new FakeAuthorizationProvider("tok-1", "tok-2");
        keeping.invalidates = false;
        remoteAnswers(401);
        sent.clear();
        ActionListener<Tuple<Integer, ModelTensors>> l2 = listener();

        predict(executor(oauth2Connector(ACTION_URL), keeping), l2);

        assertUnauthorized(failureOf(l2), 1);
        assertEquals(1, sent.size());
        assertEquals(1, keeping.requests.get());
    }

    @Test
    public void theRetriesOfARequestShareOneTokenRefresh() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1", "tok-2", "tok-3");
        remoteAnswers(401, 503, 401);
        ActionListener<Tuple<Integer, ModelTensors>> l1 = listener();

        predictWithRetry(executor(retryingConnector(), provider), l1);

        assertUnauthorized(failureOf(l1), 3);
        assertEquals(3, sent.size());
        assertEquals(List.of(provider.issued.get(0), provider.issued.get(2)), provider.invalidated);

        FakeAuthorizationProvider recovering = new FakeAuthorizationProvider("tok-1", "tok-2", "tok-3");
        remoteAnswers(401, 503, 200);
        sent.clear();
        ActionListener<Tuple<Integer, ModelTensors>> l2 = listener();

        predictWithRetry(executor(retryingConnector(), recovering), l2);

        assertSucceeded(l2);
        assertEquals(3, sent.size());
        assertEquals(List.of(recovering.issued.get(0)), recovering.invalidated);
    }

    @Test
    public void aStreamCarriesTheBearerAndDropsItsTokenOn401() {
        FakeAuthorizationProvider provider = new FakeAuthorizationProvider("tok-1");
        HttpJsonConnectorExecutor oauth2 = executor(oauth2Connector(ACTION_URL), provider);
        AuthorizationProviderFactory untouched = mock(AuthorizationProviderFactory.class);
        HttpJsonConnectorExecutor plain = executor(connector(null, ACTION_URL), untouched);
        StreamingHandler handler = mock(StreamingHandler.class);
        ArgumentCaptor<ConnectorAddressValidator> validators = ArgumentCaptor.forClass(ConnectorAddressValidator.class);
        ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);

        try (MockedStatic<StreamingHandlerFactory> handlers = mockStatic(StreamingHandlerFactory.class)) {
            handlers.when(() -> StreamingHandlerFactory.createHandler(any(), any(), any(), any(), any(), any(), any())).thenReturn(handler);

            stream(oauth2, mock(StreamPredictActionListener.class));
            stream(plain, mock(StreamPredictActionListener.class));

            handlers
                .verify(
                    () -> StreamingHandlerFactory
                        .createHandler(any(), any(), any(), any(), any(), validators.capture(), callbacks.capture()),
                    times(2)
                );
        }
        verify(handler, times(2)).startStream(eq(URI.create(ACTION_URL)), headers.capture(), any(), any(), any());

        Map<String, String> withBearer = new HashMap<>(ACTION_HEADERS);
        withBearer.put("Authorization", "Bearer tok-1");
        assertEquals(List.of(withBearer, ACTION_HEADERS), headers.getAllValues());
        assertNotSame(ConnectorAddressValidator.DEFAULT, validators.getAllValues().get(0));
        callbacks.getAllValues().get(0).run();
        assertEquals(provider.issued, provider.invalidated);
        assertNull(callbacks.getAllValues().get(1));
        verifyNoInteractions(untouched);
    }

    /** The node's factory, cache and token client, the token endpoint answering with {@code tokenResponses}. */
    private AuthorizationProviderFactory nodeFactory(String... tokenResponses) {
        ClusterSettings clusterSettings = clusterSettings();
        MLFeatureEnabledSetting settings = featureSettings(clusterSettings);
        OAuth2TokenCache cache = new OAuth2TokenCache(clusterSettings, settings, stat -> {}, clock::get);
        OAuth2TestFixtures.Reply[] replies = List
            .of(tokenResponses)
            .stream()
            .map(body -> reply(200, body))
            .toArray(OAuth2TestFixtures.Reply[]::new);
        return new AuthorizationProviderFactory(cache, tokenClient(scriptedTransport(tokenRequests, replies), threadPool), settings);
    }

    /** Sends one predict request and waits for its outcome; a failure is returned, a response gives null. */
    private static Exception predictAndWait(HttpJsonConnectorExecutor executor) throws Exception {
        CompletableFuture<Tuple<Integer, ModelTensors>> result = new CompletableFuture<>();
        predict(executor, ActionListener.wrap(result::complete, result::completeExceptionally));
        try {
            result.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return (Exception) e.getCause();
        }
    }

    @Test
    public void requestsOfOneConnectorShareTheTokenOfTheNodeCache() throws Exception {
        HttpJsonConnectorExecutor executor = executor(oauth2Connector(ACTION_URL), nodeFactory(TOKEN_RESPONSE));

        assertNull(predictAndWait(executor));
        assertNull(predictAndWait(executor));

        assertEquals(1, tokenRequests.size());
        assertEquals(2, sent.size());
        for (SdkHttpFullRequest request : sent) {
            assertEquals(List.of("Bearer tok-1"), authorization(request));
        }
    }

    @Test
    public void aRemoteServiceThatRejectsEveryTokenCausesOneTokenRequestPerTenSeconds() throws Exception {
        HttpJsonConnectorExecutor executor = executor(oauth2Connector(ACTION_URL), nodeFactory(TOKEN_RESPONSE, SECOND_TOKEN_RESPONSE));
        assertNull(predictAndWait(executor));
        remoteAnswers(401);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10));

        for (int i = 0; i < 100; i++) {
            assertEquals(RestStatus.UNAUTHORIZED, ((OpenSearchStatusException) predictAndWait(executor)).status());
        }

        assertEquals(2, tokenRequests.size());
        assertEquals(102, sent.size());
        assertEquals(List.of("Bearer tok-2"), authorization(sent.get(101)));
    }
}
