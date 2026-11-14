/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

/** An SDK HTTP client that checks every request's destination with {@link ConnectorAddressValidator} before sending it. */
@Log4j2
public class MLValidatableAsyncHttpClient implements SdkAsyncHttpClient {
    private final SdkAsyncHttpClient delegate;
    private final ConnectorAddressValidator validator;

    protected MLValidatableAsyncHttpClient(
        SdkAsyncHttpClient client,
        boolean connectorPrivateIpEnabled,
        List<Pattern> connectorTrustedPrivateEndpoints,
        List<Pattern> connectorRestrictedIpPatterns
    ) {
        this.delegate = client;
        this.validator = new ConnectorAddressValidator(
            connectorPrivateIpEnabled,
            connectorTrustedPrivateEndpoints,
            connectorRestrictedIpPatterns
        );
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
        String protocol = request.request().protocol();
        String host = request.request().host();
        int port = request.request().port();
        String endpoint = request.request().getUri().toString();
        try {
            validator.validate(endpoint, protocol, host, port);
            return delegate.execute(request);
        } catch (Exception e) {
            log.error("Failed to validate request!", e);
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }
}
