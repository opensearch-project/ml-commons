/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.opensearch.OpenSearchException;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.common.inject.AbstractModule;
import org.opensearch.core.common.Strings;
import org.opensearch.sdk.SdkClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A module for binding this plugin's desired implementation of {@link SdkClient}.
 */
public class SdkClientModule extends AbstractModule {

    public static final String REMOTE_METADATA_ENDPOINT = "REMOTE_METADATA_ENDPOINT";
    public static final String REGION = "REGION";

    private final String remoteMetadataEndpoint;
    private final String region; // not using with RestClient

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
        try {
            // Basic http(not-s) client using RestClient.
            RestClient restClient = RestClient
                // This HttpHost syntax works with export REMOTE_METADATA_ENDPOINT=http://127.0.0.1:9200
                .builder(HttpHost.create(remoteMetadataEndpoint))
                .setStrictDeprecationMode(true)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    try {
                        return httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    } catch (Exception e) {
                        throw new OpenSearchException(e);
                    }
                })
                .build();
            return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(new ObjectMapper())));
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
