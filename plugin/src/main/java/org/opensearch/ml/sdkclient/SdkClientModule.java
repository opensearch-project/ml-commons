/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import org.opensearch.OpenSearchException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.common.inject.AbstractModule;
import org.opensearch.core.common.Strings;
import org.opensearch.sdk.SdkClient;

import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

/**
 * A module for binding this plugin's desired implementation of {@link SdkClient}.
 */
public class SdkClientModule extends AbstractModule {

    // Constants to configure the remote client
    public static final String REMOTE_METADATA_ENDPOINT = "REMOTE_METADATA_ENDPOINT";
    public static final String REGION = "REGION";

    @Override
    protected void configure() {
        boolean local = Strings.isNullOrEmpty(System.getenv(REMOTE_METADATA_ENDPOINT));
        if (local) {
            bind(SdkClient.class).to(LocalClusterIndicesClient.class);
        } else {
            bind(SdkClient.class).toInstance(new RemoteClusterIndicesClient(createOpenSearchClient()));
        }
    }

    private OpenSearchClient createOpenSearchClient() {
        SdkHttpClient httpClient = ApacheHttpClient.builder().build();
        try {
            return new OpenSearchClient(
                new AwsSdk2Transport(
                    httpClient,
                    System.getenv(REMOTE_METADATA_ENDPOINT),
                    Region.of(System.getenv(REGION)),
                    AwsSdk2TransportOptions.builder().build()
                )
            );
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
