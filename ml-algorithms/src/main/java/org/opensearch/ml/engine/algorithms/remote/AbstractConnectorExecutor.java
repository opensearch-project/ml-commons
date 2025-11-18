/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.net.http.HttpRequest;

import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Setter
@Getter
public abstract class AbstractConnectorExecutor implements RemoteConnectorExecutor {
    private ConnectorClientConfig connectorClientConfig;

    public void initialize(Connector connector) {
        if (connector.getConnectorClientConfig() != null) {
            connectorClientConfig = connector.getConnectorClientConfig();
        } else {
            connectorClientConfig = new ConnectorClientConfig();
        }
    }

    /**
     * Gets MCP request headers from ThreadContext and adds them to the HTTP request builder.
     * 
     * @param builder HttpRequest.Builder to add headers to
     */
    protected void getMcpRequestHeaders(HttpRequest.Builder builder) {
        if (getClient() != null) {
            ThreadContext threadContext = getClient().threadPool().getThreadContext();

            String accessKeyId = threadContext.getHeader(CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID);
            if (accessKeyId != null && !accessKeyId.isEmpty()) {
                builder.header("aws-access-key-id", accessKeyId);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID);
            }

            String secretAccessKey = threadContext.getHeader(CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY);
            if (secretAccessKey != null && !secretAccessKey.isEmpty()) {
                builder.header("aws-secret-access-key", secretAccessKey);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY);
            }

            String sessionToken = threadContext.getHeader(CommonValue.MCP_HEADER_AWS_SESSION_TOKEN);
            if (sessionToken != null && !sessionToken.isEmpty()) {
                builder.header("aws-session-token", sessionToken);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_AWS_SESSION_TOKEN);
            }

            String region = threadContext.getHeader(CommonValue.MCP_HEADER_AWS_REGION);
            if (region != null && !region.isEmpty()) {
                builder.header("aws-region", region);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_AWS_REGION);
            }

            String serviceName = threadContext.getHeader(CommonValue.MCP_HEADER_AWS_SERVICE_NAME);
            if (serviceName != null && !serviceName.isEmpty()) {
                builder.header("aws-service-name", serviceName);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_AWS_SERVICE_NAME);
            }

            String opensearchUrl = threadContext.getHeader(CommonValue.MCP_HEADER_OPENSEARCH_URL);
            if (opensearchUrl != null && !opensearchUrl.isEmpty()) {
                builder.header("opensearch-url", opensearchUrl);
                log.debug("Get MCP header: {}", CommonValue.MCP_HEADER_OPENSEARCH_URL);
            }
        }
    }
}
