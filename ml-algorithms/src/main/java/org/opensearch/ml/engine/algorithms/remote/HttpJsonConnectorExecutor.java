/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static software.amazon.awssdk.http.SdkHttpMethod.DELETE;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;
import static software.amazon.awssdk.http.SdkHttpMethod.PUT;

import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.OAuth2Support;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProvider;
import org.opensearch.ml.engine.algorithms.remote.oauth2.AuthorizationProviderFactory;
import org.opensearch.ml.engine.algorithms.remote.oauth2.TokenHandle;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandler;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamingHandlerFactory;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor extends AbstractConnectorExecutor {
    @Getter
    private HttpConnector connector;
    @Setter
    @Getter
    private ScriptService scriptService;

    @Setter
    @Getter
    private TokenBucket rateLimiter;
    @Setter
    @Getter
    private Map<String, TokenBucket> userRateLimiterMap;
    @Setter
    @Getter
    private Client client;
    @Setter
    @Getter
    private MLGuard mlGuard;
    @Setter
    private volatile boolean connectorPrivateIpEnabled;
    @Setter
    private volatile List<Pattern> connectorTrustedPrivateEndpoints;
    @Setter
    private volatile List<Pattern> connectorRestrictedIpPatterns;

    @Setter
    @Getter
    private volatile List<String> trustedConnectorEndpointsRegex;

    @Setter
    private volatile AuthorizationProviderFactory authorizationProviderFactory;
    private volatile AuthorizationProvider authorizationProvider;
    /** True when the connector's auth_config has a type that attaches a token. */
    private final boolean declaresAuthorization;

    private final CertificateProcessor certificateProcessor = new CertificateProcessor();
    // Cache manager for client lifecycle management
    private final MLHttpClientCacheManager cacheManager = new MLHttpClientCacheManager();

    @Setter
    @Getter
    private StreamTransportService streamTransportService;

    public HttpJsonConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (HttpConnector) connector;
        OAuth2AuthConfig authConfig = this.connector.getAuthConfig();
        this.declaresAuthorization = authConfig != null && authConfig.getType().isActive();
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void invokeRemoteService(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        try {
            // The request is built from this URI, which passed the connector's URL checks.
            URI endpoint = connector.resolveActionEndpoint(action, parameters, trustedConnectorEndpointsRegex);
            Map<String, String> headers = connector.getHeadersWithRuntimeParameters(parameters);
            if (!declaresAuthorization) {
                send(action, endpoint, headers, parameters, payload, executionContext, actionListener);
                return;
            }
            // Checked before a token is requested, so a request that cannot be sent costs no call to the identity provider.
            AuthConfigValidator.requireHttps(endpoint);
            AuthorizationProvider provider = authorizationProvider();
            ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true);
            ActionListener<TokenHandle> sendWithToken = ActionListener.wrap(handle -> {
                Map<String, String> authorized = OAuth2Support.withBearer(headers, handle.value());
                ActionListener<Tuple<Integer, ModelTensors>> responseListener = refreshOnceOnUnauthorized(
                    provider,
                    handle,
                    action,
                    mlInput,
                    parameters,
                    payload,
                    executionContext,
                    actionListener
                );
                send(action, endpoint, authorized, parameters, payload, executionContext, responseListener);
            }, actionListener::onFailure);
            provider.authorizationValue(ActionListener.runBefore(sendWithToken, context::restore));
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(e);
        }
    }

    /** Builds the request from a URI that passed the connector's URL checks and the final headers, and sends it. */
    @SuppressWarnings("removal")
    private void send(
        String action,
        URI endpoint,
        Map<String, String> headers,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        try {
            SdkHttpFullRequest request;
            switch (connector.getActionHttpMethod(action).toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    request = ConnectorUtils.buildSdkRequest(action, endpoint, headers, parameters, payload, POST);
                    break;
                case "GET":
                    request = ConnectorUtils.buildSdkRequest(action, endpoint, headers, parameters, payload, GET);
                    break;
                case "PUT":
                    request = ConnectorUtils.buildSdkRequest(action, endpoint, headers, parameters, payload, PUT);
                    break;
                case "DELETE":
                    request = ConnectorUtils.buildSdkRequest(action, endpoint, headers, parameters, payload, DELETE);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }
            ThreadContext.StoredContext storedContext = client.threadPool().getThreadContext().newStoredContext(true);

            // TODO: We should have an idea to identify the source of the request(predict/agent execution),
            // but currently it's not easy, so reusing the predict thread pool won't harm anything.
            ThreadedActionListener<Tuple<Integer, ModelTensors>> threadedListener = createThreadedListener(log, actionListener);

            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(request)
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(
                    new MLSdkAsyncHttpResponseHandler(
                        executionContext,
                        ActionListener.runBefore(threadedListener, storedContext::restore),
                        parameters,
                        connector,
                        scriptService,
                        mlGuard,
                        action
                    )
                )
                .build();
            AccessController
                .doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> getHttpClient().execute(executeRequest));
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(new MLException("Fail to execute http connector", e));
        }
    }

    /** The provider of this executor's connector, built on first use. */
    private AuthorizationProvider authorizationProvider() {
        AuthorizationProvider provider = authorizationProvider;
        if (provider == null) {
            // Two first requests may each build one; they are equivalent and the last one is kept.
            provider = authorizationProviderFactory.providerFor(connector);
            authorizationProvider = provider;
        }
        return provider;
    }

    /** The response listener of a request that carried {@code handle}'s token. */
    private ActionListener<Tuple<Integer, ModelTensors>> refreshOnceOnUnauthorized(
        AuthorizationProvider provider,
        TokenHandle handle,
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        return ActionListener.delegateResponse(actionListener, (listener, e) -> {
            // Sent again at most once per request, and only when the provider dropped or replaced the token.
            if (isUnauthorized(e) && provider.invalidate(handle) && executionContext.getTokenRefreshUsed().compareAndSet(false, true)) {
                invokeRemoteService(action, mlInput, parameters, payload, executionContext, listener);
            } else {
                listener.onFailure(e);
            }
        });
    }

    private static boolean isUnauthorized(Exception e) {
        return e instanceof OpenSearchStatusException status && status.status() == RestStatus.UNAUTHORIZED;
    }

    @Override
    public void invokeRemoteServiceStream(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        try {
            // The request is built from this URI, which passed the connector's URL checks.
            URI endpoint = connector.resolveActionEndpoint(action, parameters, trustedConnectorEndpointsRegex);

            String llmInterface = StringEscapeUtils.unescapeJava(parameters.get(LLM_INTERFACE).trim().toLowerCase(Locale.ROOT));
            validateLLMInterface(llmInterface);

            Map<String, String> headers = connector.getHeadersWithRuntimeParameters(parameters);
            if (!declaresAuthorization) {
                startStream(llmInterface, endpoint, headers, null, parameters, payload, actionListener);
                return;
            }
            // Checked before a token is requested, so a request that cannot be sent costs no call to the identity provider.
            AuthConfigValidator.requireHttps(endpoint);
            AuthorizationProvider provider = authorizationProvider();
            ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true);
            // A 401 ends the stream and discards this token unless it is younger than ten seconds; the stream is not sent again.
            ActionListener<TokenHandle> streamWithToken = ActionListener.wrap(handle -> {
                Map<String, String> authorized = OAuth2Support.withBearer(headers, handle.value());
                startStream(llmInterface, endpoint, authorized, () -> provider.invalidate(handle), parameters, payload, actionListener);
            }, e -> streamFailed(e, actionListener));
            provider.authorizationValue(ActionListener.runBefore(streamWithToken, context::restore));
        } catch (Exception e) {
            streamFailed(e, actionListener);
        }
    }

    private void startStream(
        String llmInterface,
        URI endpoint,
        Map<String, String> headers,
        Runnable onUnauthorized,
        Map<String, String> parameters,
        String payload,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        StreamingHandler handler = StreamingHandlerFactory
            .createHandler(
                llmInterface,
                connector,
                null,
                super.getConnectorClientConfig(),
                parameters,
                new ConnectorAddressValidator(connectorPrivateIpEnabled, connectorTrustedPrivateEndpoints, connectorRestrictedIpPatterns),
                onUnauthorized
            );
        handler.startStream(endpoint, headers, parameters, payload, actionListener);
    }

    private static void streamFailed(Exception e, StreamPredictActionListener<MLTaskResponse, ?> actionListener) {
        log.error("Failed to execute streaming", e);
        // A refusal keeps its status and an argument error its message, as on the request path.
        if (e instanceof OpenSearchStatusException || e instanceof IllegalArgumentException) {
            actionListener.onFailure(e);
        } else {
            actionListener.onFailure(new MLException("Fail to execute streaming", e));
        }
    }

    private void validateLLMInterface(String llmInterface) {
        switch (llmInterface) {
            case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported llm interface: %s", llmInterface));
        }
    }

    @VisibleForTesting
    protected SdkAsyncHttpClient getHttpClient() {
        // Use cache manager for client lifecycle management
        SdkAsyncHttpClient httpClient = cacheManager
            .getOrCreateHttpClient(connector, super.getConnectorClientConfig(), client, this::createHttpClient);

        // Store in inherited httpClientRef for compatibility with AbstractConnectorExecutor.close()
        // This ensures proper resource cleanup when the executor is closed
        super.httpClientRef.set(httpClient);

        return httpClient;
    }

    /**
     * Create a new HTTP client with current configuration.
     */
    private SdkAsyncHttpClient createHttpClient() {
        return MLHttpClientCacheManager
            .createHttpClient(
                connector,
                super.getConnectorClientConfig(),
                certificateProcessor,
                connectorPrivateIpEnabled,
                connectorTrustedPrivateEndpoints,
                connectorRestrictedIpPatterns
            );
    }

    /**
     * Override close() to properly clean up resources managed by the cache manager.
     * This prevents resource leaks by ensuring both the cache manager and inherited
     * httpClientRef are properly closed.
     */
    @Override
    public void close() {
        // Close the cache manager first to handle any deferred cleanup
        cacheManager.close();

        // Call parent close() to handle inherited httpClientRef
        super.close();
    }
}
