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
import org.opensearch.sdk.SdkClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * A module for binding this plugin's desired implementation of {@link SdkClient}.
 */
@Log4j2
public class SdkClientModule extends AbstractModule {

    public static final String REMOTE_METADATA_TYPE = "REMOTE_METADATA_TYPE";
    public static final String REMOTE_METADATA_ENDPOINT = "REMOTE_METADATA_ENDPOINT";
    public static final String REGION = "REGION";
    public static final String REMOTE_OPENSEARCH = "RemoteOpenSearch";
    public static final String AWS_DYNAMO_DB = "AWSDynamoDB";

    private final String remoteMetadataType;
    private final String remoteMetadataEndpoint;
    private final String region; // not using with RestClient

    /**
     * Instantiate this module using environment variables
     */
    public SdkClientModule() {
        this(System.getenv(REMOTE_METADATA_TYPE), System.getenv(REMOTE_METADATA_ENDPOINT), System.getenv(REGION));
    }

    /**
     * Instantiate this module specifying the endpoint and region. Package private for testing.
     * @param remoteMetadataType Type of remote metadata store
     * @param remoteMetadataEndpoint The remote endpoint
     * @param region The region
     */
    SdkClientModule(String remoteMetadataType, String remoteMetadataEndpoint, String region) {
        this.remoteMetadataType = remoteMetadataType;
        this.remoteMetadataEndpoint = remoteMetadataEndpoint;
        this.region = region;
    }

    @Override
    protected void configure() {
        if (this.remoteMetadataType == null) {
            log.info("Using local opensearch cluster as metadata store");
            bind(SdkClient.class).to(LocalClusterIndicesClient.class);
            return;
        }

        switch (this.remoteMetadataType) {
            case REMOTE_OPENSEARCH:
                log.info("Using remote opensearch cluster as metadata store");
                bind(SdkClient.class).toInstance(new RemoteClusterIndicesClient(createOpenSearchClient()));
                return;
            case AWS_DYNAMO_DB:
                log.info("Using dynamo DB as metadata store");
                bind(SdkClient.class)
                    .toInstance(new DDBOpenSearchClient(createDynamoDbClient(), new RemoteClusterIndicesClient(createOpenSearchClient())));
                return;
            default:
                log.info("Using local opensearch cluster as metadata store");
                bind(SdkClient.class).to(LocalClusterIndicesClient.class);
        }
    }

    private DynamoDbClient createDynamoDbClient() {
        if (this.region == null) {
            throw new IllegalStateException("REGION environment variable needs to be set!");
        }

        AwsCredentialsProviderChain credentialsProviderChain = AwsCredentialsProviderChain
            .builder()
            .addCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .addCredentialsProvider(ContainerCredentialsProvider.builder().build())
            .addCredentialsProvider(InstanceProfileCredentialsProvider.create())
            .build();

        return DynamoDbClient.builder().region(Region.of(this.region)).credentialsProvider(credentialsProviderChain).build();
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
            ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
