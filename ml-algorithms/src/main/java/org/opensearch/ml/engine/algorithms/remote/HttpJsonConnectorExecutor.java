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

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLValidationException;
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

    private final AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();
    private final AtomicReference<String> httpClientCacheKey = new AtomicReference<>();
    private final CertificateProcessor certificateProcessor = new CertificateProcessor();

    @Setter
    @Getter
    private StreamTransportService streamTransportService;

    public HttpJsonConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (HttpConnector) connector;
    }

    @Override
    public Logger getLogger() {
        return log;
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
            // Re-validate the resolved URL against the trusted-connector-endpoints
            connector.validateResolvedEndpoint(connector.getActionEndpoint(action, parameters), trustedConnectorEndpointsRegex);

            SdkHttpFullRequest request;
            switch (connector.getActionHttpMethod(action).toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, POST);
                    break;
                case "GET":
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, GET);
                    break;
                case "PUT":
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, PUT);
                    break;
                case "DELETE":
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, DELETE);
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
            // Re-validate the resolved URL against the trusted-connector-endpoints allowlist
            connector.validateResolvedEndpoint(connector.getActionEndpoint(action, parameters), trustedConnectorEndpointsRegex);

            String llmInterface = parameters.get(LLM_INTERFACE);
            llmInterface = llmInterface.trim().toLowerCase(Locale.ROOT);
            llmInterface = StringEscapeUtils.unescapeJava(llmInterface);
            validateLLMInterface(llmInterface);

            StreamingHandler handler = StreamingHandlerFactory
                .createHandler(llmInterface, connector, null, super.getConnectorClientConfig(), parameters);
            handler.startStream(action, parameters, payload, actionListener);
        } catch (Exception e) {
            log.error("Failed to execute streaming", e);
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
        String currentCacheKey = generateHttpClientCacheKey();

        if (httpClientRef.get() == null || !Objects.equals(httpClientCacheKey.get(), currentCacheKey)) {
            synchronized (this) {
                if (httpClientRef.get() == null || !Objects.equals(httpClientCacheKey.get(), currentCacheKey)) {
                    SdkAsyncHttpClient existingClient = httpClientRef.get();
                    if (existingClient != null) {
                        try {
                            existingClient.close();
                            log.debug("Closed existing HTTP client due to configuration change");
                        } catch (Exception e) {
                            log.warn("Failed to close existing HTTP client: {}", e.getMessage());
                        }
                    }

                    SdkAsyncHttpClient newClient = createHttpClient();
                    httpClientRef.set(newClient);
                    httpClientCacheKey.set(currentCacheKey);

                    log.debug("Created new HTTP client with cache key: {}", currentCacheKey);
                }
            }
        }
        return httpClientRef.get();
    }

    /**
     * Generate a cache key that includes all configuration parameters that affect HTTP client creation.
     * This ensures the client is recreated when credentials or SSL configuration changes.
     */
    private String generateHttpClientCacheKey() {
        StringBuilder keyBuilder = new StringBuilder();

        keyBuilder.append("conn:").append(super.getConnectorClientConfig().getConnectionTimeout());
        keyBuilder.append(",read:").append(super.getConnectorClientConfig().getReadTimeout());
        keyBuilder.append(",max:").append(super.getConnectorClientConfig().getMaxConnections());

        Boolean skipSslVerification = super.getConnectorClientConfig().getSkipSslVerification();
        Boolean mutualTlsEnabled = super.getConnectorClientConfig().getMutualTlsEnabled();
        keyBuilder.append(",skipSsl:").append(skipSslVerification != null ? skipSslVerification : false);
        keyBuilder.append(",mtls:").append(mutualTlsEnabled != null ? mutualTlsEnabled : false);

        if (mutualTlsEnabled != null && mutualTlsEnabled && connector.getDecryptedCredential() != null) {
            int credentialHash = Objects
                .hash(
                    connector.getDecryptedCredential().get("client_cert"),
                    connector.getDecryptedCredential().get("client_key"),
                    connector.getDecryptedCredential().get("ca_cert"),
                    connector.getDecryptedCredential().get("client_cert_path"),
                    connector.getDecryptedCredential().get("client_key_path"),
                    connector.getDecryptedCredential().get("ca_cert_path")
                );
            keyBuilder.append(",creds:").append(credentialHash);
        }

        return keyBuilder.toString();
    }

    /**
     * Create a new HTTP client with current configuration.
     * Extracted from the original getHttpClient method for better separation of concerns.
     */
    private SdkAsyncHttpClient createHttpClient() {
        Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
        Boolean skipSslVerification = super.getConnectorClientConfig().getSkipSslVerification();
        Boolean mutualTlsEnabled = super.getConnectorClientConfig().getMutualTlsEnabled();

        boolean skipSslVerificationValue = skipSslVerification != null ? skipSslVerification : false;
        boolean mutualTlsEnabledValue = mutualTlsEnabled != null ? mutualTlsEnabled : false;

        if (skipSslVerificationValue) {
            log.warn("SSL certificate verification is DISABLED");
        }

        SSLContext sslContext = null;
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;
        String clientDescription = "standard";

        // Build SSL context for mutual TLS if enabled
        // Note: connector.getDecryptedCredential() is guaranteed to be non-null when mTLS is enabled
        // due to the decryption-precedes-execution invariant enforced by the connector framework
        if (mutualTlsEnabledValue) {
            try {
                certificateProcessor.validateCertificateConfig(super.getConnectorClientConfig(), connector.getDecryptedCredential());

                // Enforce certificate-only authentication (no mixed auth methods)
                certificateProcessor
                    .validateCertificateOnlyAuthentication(super.getConnectorClientConfig(), connector.getDecryptedCredential());

                CertificateProcessor.SSLContextWithManagers contextWithManagers = certificateProcessor
                    .buildSSLContext(super.getConnectorClientConfig(), connector.getDecryptedCredential());

                if (contextWithManagers != null) {
                    sslContext = contextWithManagers.getSslContext();
                    keyManagers = contextWithManagers.getKeyManagers();
                    trustManagers = contextWithManagers.getTrustManagers();

                    log.debug("Successfully extracted SSL context and managers");
                    log
                        .debug(
                            "Key managers: {}, Trust managers: {}",
                            keyManagers != null ? keyManagers.length : 0,
                            trustManagers != null ? trustManagers.length : 0
                        );
                }

                clientDescription = "mutual-TLS";
                log.debug("Successfully configured certificate-only mutual TLS");

            } catch (MLValidationException e) {
                log.error("Certificate validation failed: {}", e.getMessage());
                throw e;
            } catch (SecurityException e) {
                log.error("Security policy violation during SSL context initialization: {}", e.getMessage());
                throw new MLException("SSL security policy violation: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("Failed to configure mutual TLS: {}", e.getMessage());
                throw new MLException("Failed to configure mutual TLS: " + e.getMessage(), e);
            }
        } else {
            log
                .info(
                    "HttpJsonConnectorExecutor creating HTTP client for connector: {} - maxConnections: {}, connectionTimeout: {}s, readTimeout: {}s",
                    connector.getName(),
                    maxConnection,
                    super.getConnectorClientConfig().getConnectionTimeout(),
                    super.getConnectorClientConfig().getReadTimeout()
                );
        }

        log
            .info(
                "HTTP client created - type: {}, maxConnections: {}, connectionTimeout: {}s, readTimeout: {}s, mutualTLS: {}",
                clientDescription,
                maxConnection,
                super.getConnectorClientConfig().getConnectionTimeout(),
                super.getConnectorClientConfig().getReadTimeout(),
                mutualTlsEnabledValue
            );

        return MLHttpClientFactory
            .getAsyncHttpClient(
                connectionTimeout,
                readTimeout,
                maxConnection,
                connectorPrivateIpEnabled,
                connectorTrustedPrivateEndpoints,
                connectorRestrictedIpPatterns,
                skipSslVerificationValue,
                sslContext,
                clientDescription,
                keyManagers,
                trustManagers
            );
    }
}
