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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.OpenSearchException;
import org.opensearch.client.Client;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.common.inject.AbstractModule;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.sdk.SdkClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
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
    private Client client;
    private NamedXContentRegistry namedXContentRegistry;

    /**
     * Instantiate this module using environment variables
     */
    public SdkClientModule(Client client, NamedXContentRegistry namedXContentRegistry) {
        this(
            client,
            namedXContentRegistry,
            System.getenv(REMOTE_METADATA_TYPE),
            System.getenv(REMOTE_METADATA_ENDPOINT),
            System.getenv(REGION)
        );
    }

    /**
     * Instantiate this module specifying the endpoint and region. Package private for testing.
     * @param remoteMetadataType Type of remote metadata store
     * @param remoteMetadataEndpoint The remote endpoint
     * @param region The region
     */
    SdkClientModule(
        Client client,
        NamedXContentRegistry namedXContentRegistry,
        String remoteMetadataType,
        String remoteMetadataEndpoint,
        String region
    ) {
        this.client = client;
        this.namedXContentRegistry = namedXContentRegistry;
        this.remoteMetadataType = remoteMetadataType;
        this.remoteMetadataEndpoint = remoteMetadataEndpoint;
        this.region = region;
    }

    @Override
    protected void configure() {
        if (this.remoteMetadataType == null) {
            log.info("Using local opensearch cluster as metadata store");
            bindLocalClient();
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
                bindLocalClient();
        }
    }

    private void bindLocalClient() {
        if (client == null) {
            bind(SdkClient.class).to(LocalClusterIndicesClient.class);
        } else {
            bind(SdkClient.class).toInstance(new LocalClusterIndicesClient(this.client, this.namedXContentRegistry));
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
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // Basic http(not-s) client using RestClient.
            SdkHttpClient httpClient = ApacheHttpClient.builder().build();
            AwsSdk2Transport awsSdk2Transport = new AwsSdk2Transport(
                httpClient,
                HttpHost.create(remoteMetadataEndpoint).getHostName(),
                "aoss",
                Region.of(region),
                AwsSdk2TransportOptions.builder().build()
            );
            /*RestClient restClient = RestClient
                // This HttpHost syntax works with export REMOTE_METADATA_ENDPOINT=http://127.0.0.1:9200
                .builder(HttpHost.create(remoteMetadataEndpoint))
                .setStrictDeprecationMode(true)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    try {
                        return httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider)
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    } catch (Exception e) {
                        throw new OpenSearchException(e);
                    }
                })
                .build();*/
            ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            // return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
            return new OpenSearchClient(awsSdk2Transport);
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
