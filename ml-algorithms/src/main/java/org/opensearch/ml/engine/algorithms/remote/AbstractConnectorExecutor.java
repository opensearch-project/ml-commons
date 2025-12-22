/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Logger;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.output.model.ModelTensors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {

    @Setter
    private volatile boolean connectorPrivateIpEnabled;

    private final AtomicReference<SdkAsyncHttpClient> httpClientRef = new AtomicReference<>();

    private ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();

    public void initialize(Connector connector) {
        if (connector.getConnectorClientConfig() != null) {
            connectorClientConfig = connector.getConnectorClientConfig();
        }
    }

    protected SdkAsyncHttpClient getHttpClient() {
        // This block for high performance retrieval after http client is created.
        SdkAsyncHttpClient existingClient = httpClientRef.get();
        if (existingClient != null) {
            return existingClient;
        }
        // This block handles concurrent http client creation.
        synchronized (this) {
            existingClient = httpClientRef.get();
            if (existingClient != null) {
                return existingClient;
            }
            Duration connectionTimeout = Duration
                .ofMillis(
                    Optional
                        .ofNullable(connectorClientConfig.getConnectionTimeoutMillis())
                        .orElse(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE)
                );
            Duration readTimeout = Duration
                .ofMillis(
                    Optional
                        .ofNullable(connectorClientConfig.getReadTimeoutMillis())
                        .orElse(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE)
                );
            int maxConnection = Optional
                .ofNullable(connectorClientConfig.getMaxConnections())
                .orElse(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE);
            SdkAsyncHttpClient newClient = MLHttpClientFactory
                .getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection, connectorPrivateIpEnabled);
            httpClientRef.set(newClient);
            return newClient;
        }
    }

    /**
     * Gets MCP request headers from ThreadContext and adds them to the HTTP request builder.
     *
     * @param builder HttpRequest.Builder to add headers to
     */
    protected void getMcpRequestHeaders(HttpRequest.Builder builder) {
        if (getClient() == null) {
            return;
        }

        ThreadContext threadContext = getClient().threadPool().getThreadContext();

        String[] mcpHeaders = {
            CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID,
            CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY,
            CommonValue.MCP_HEADER_AWS_SESSION_TOKEN,
            CommonValue.MCP_HEADER_AWS_REGION,
            CommonValue.MCP_HEADER_AWS_SERVICE_NAME,
            CommonValue.MCP_HEADER_OPENSEARCH_URL };

        for (String headerName : mcpHeaders) {
            String headerValue = threadContext.getHeader(headerName);
            if (headerValue != null && !headerValue.isEmpty()) {
                builder.setHeader(headerName, headerValue);
                log.debug("Get MCP header: {}", headerName);
            }
        }
    }

    public void close() {
        SdkAsyncHttpClient httpClient = httpClientRef.getAndSet(null);
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * Creates a ThreadedActionListener to offload response processing from Netty I/O thread to ML thread pool.
     * This prevents blocking I/O threads during long-running cases like PER agent.
     *
     * @param logger Logger instance for the specific executor
     * @param actionListener The original action listener to wrap
     * @return ThreadedActionListener wrapped around the original listener
     */
    protected ThreadedActionListener<Tuple<Integer, ModelTensors>> createThreadedListener(
        Logger logger,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        return new ThreadedActionListener<>(logger, getClient().threadPool(), "opensearch_ml_predict_remote", actionListener, false);
    }
}
