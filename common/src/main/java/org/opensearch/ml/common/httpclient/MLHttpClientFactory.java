/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.opensearch.secure_sm.AccessController.doPrivileged;

import java.time.Duration;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

@Log4j2
public class MLHttpClientFactory {

    public static SdkAsyncHttpClient getAsyncHttpClient(
        Duration connectionTimeout,
        Duration readTimeout,
        int maxConnections,
        boolean connectorPrivateIpEnabled,
        boolean skipSslVerification
    ) {
        return doPrivileged(() -> {
            log
                .debug(
                    "Creating MLHttpClient with connectionTimeout: {}, readTimeout: {}, maxConnections: {}",
                    connectionTimeout,
                    readTimeout,
                    maxConnections
                );
            SdkAsyncHttpClient delegate = NettyNioAsyncHttpClient
                .builder()
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .maxConcurrency(maxConnections)
                .buildWithDefaults(
                    AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, skipSslVerification).build()
                );
            return new MLValidatableAsyncHttpClient(delegate, connectorPrivateIpEnabled);
        });
    }
}
