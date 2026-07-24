/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.GOOGLE_CLOUD;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static software.amazon.awssdk.http.SdkHttpMethod.DELETE;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;
import static software.amazon.awssdk.http.SdkHttpMethod.PUT;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
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
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
@ConnectorExecutor(GOOGLE_CLOUD)
public class GoogleConnectorExecutor extends AbstractConnectorExecutor {

    @Getter
    private GoogleCloudConnector connector;
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
    @Getter
    private StreamTransportService streamTransportService;

    // Lazily built from the connector; injectable for tests.
    @Setter
    private GoogleCredentialProvider credentialProvider;

    public GoogleConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (GoogleCloudConnector) connector;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    GoogleCredentialProvider credentialProvider() {
        if (credentialProvider == null) {
            credentialProvider = GoogleCredentialProvider.fromConnector(connector);
        }
        return credentialProvider;
    }

    @SuppressWarnings("removal")
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
            // Re-validate the resolved URL against the trusted-connector-endpoints allowlist.
            connector.validateResolvedEndpoint(connector.getActionEndpoint(action, parameters), trustedConnectorEndpointsRegex);

            // Resolve and validate the HTTP method up front, before minting a token.
            SdkHttpMethod method;
            switch (connector.getActionHttpMethod(action).toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    method = POST;
                    break;
                case "GET":
                    method = GET;
                    break;
                case "PUT":
                    method = PUT;
                    break;
                case "DELETE":
                    method = DELETE;
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            // Mint a fresh OAuth2 token and set it as the bearer Authorization header. Set
            // programmatically (not via ${parameters.*}) so the header-injection guard is intact.
            String token = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> credentialProvider().getAccessToken());
            connector.setAuthorizationValue("Bearer " + token);

            SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, method);
            ThreadContext.StoredContext storedContext = client.threadPool().getThreadContext().newStoredContext(true);

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
            log.error("Fail to execute google_cloud connector", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Fail to execute google_cloud connector", e);
            actionListener.onFailure(new MLException("Fail to execute google_cloud connector", e));
        }
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
            // Re-validate the resolved URL against the trusted-connector-endpoints allowlist.
            connector.validateResolvedEndpoint(connector.getActionEndpoint(action, parameters), trustedConnectorEndpointsRegex);

            String llmInterface = parameters.get(LLM_INTERFACE);
            if (llmInterface == null) {
                throw new IllegalArgumentException(LLM_INTERFACE + " is required for streaming");
            }
            llmInterface = StringEscapeUtils.unescapeJava(llmInterface.trim().toLowerCase(Locale.ROOT));
            validateLLMInterface(llmInterface);

            // Mint a fresh OAuth2 token and set it as the bearer Authorization header before streaming.
            String token = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> credentialProvider().getAccessToken());
            connector.setAuthorizationValue("Bearer " + token);

            StreamingHandler handler = StreamingHandlerFactory
                .createHandler(llmInterface, connector, null, super.getConnectorClientConfig(), parameters);
            handler.startStream(action, parameters, payload, actionListener);
        } catch (Exception e) {
            log.error("Failed to execute google_cloud streaming", e);
            actionListener.onFailure(new MLException("Fail to execute google_cloud streaming", e));
        }
    }

    private void validateLLMInterface(String llmInterface) {
        switch (llmInterface) {
            case LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT:
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported llm interface: %s", llmInterface));
        }
    }

    @VisibleForTesting
    protected SdkAsyncHttpClient getHttpClient() {
        if (httpClientRef.get() == null) {
            Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
            Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
            Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
            Boolean skipSslVerification = super.getConnectorClientConfig().getSkipSslVerification();
            boolean skipSslVerificationValue = skipSslVerification != null ? skipSslVerification : false;
            if (skipSslVerificationValue) {
                log.warn("SSL certificate verification is DISABLED for connector {}", connector.getName());
            }
            this.httpClientRef
                .compareAndSet(
                    null,
                    MLHttpClientFactory
                        .getAsyncHttpClient(
                            connectionTimeout,
                            readTimeout,
                            maxConnection,
                            connectorPrivateIpEnabled,
                            connectorTrustedPrivateEndpoints,
                            connectorRestrictedIpPatterns,
                            skipSslVerificationValue
                        )
                );
        }
        return httpClientRef.get();
    }
}
