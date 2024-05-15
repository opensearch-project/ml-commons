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

    public static final String REMOTE_METADATA_ENDPOINT = "REMOTE_METADATA_ENDPOINT";
    public static final String REGION = "REGION";

    private final String remoteMetadataEndpoint;
    private final String region;

    /**
     * Instantiate this module using environment variables
     */
    public SdkClientModule() {
        this(System.getenv(REMOTE_METADATA_ENDPOINT), System.getenv(REGION));
    }

    /**
     * Instantiate this module specifying the endpoint and region. Package private for testing.
     * @param remoteMetadataEndpoint The remote endpoint
     * @param region The region
     */
    SdkClientModule(String remoteMetadataEndpoint, String region) {
        this.remoteMetadataEndpoint = remoteMetadataEndpoint;
        this.region = region;
    }

    @Override
    protected void configure() {
        boolean local = Strings.isNullOrEmpty(remoteMetadataEndpoint);
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
                new AwsSdk2Transport(httpClient, remoteMetadataEndpoint, Region.of(region), AwsSdk2TransportOptions.builder().build())
            );
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
