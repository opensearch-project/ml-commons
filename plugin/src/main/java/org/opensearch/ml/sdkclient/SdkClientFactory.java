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
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.OpenSearchException;
import org.opensearch.client.Client;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientDelegate;

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
 * A class to create a {@link SdkClient} with implementation based on settings
 */
@Log4j2
public class SdkClientFactory {

    // TODO Move these settings keys to plugin getSettings implementation with proper prefixes
    public static final String REMOTE_METADATA_TYPE = "REMOTE_METADATA_TYPE";
    public static final String REMOTE_METADATA_ENDPOINT = "REMOTE_METADATA_ENDPOINT";
    public static final String REGION = "REGION";
    public static final String REMOTE_OPENSEARCH = "RemoteOpenSearch";
    public static final String AWS_DYNAMO_DB = "AWSDynamoDB";
    public static final String AWS_OPENSEARCH_SERVICE = "AWSOpenSearchService";

    private static final Setting<String> REMOTE_METADATA_TYPE_SETTING = Setting.simpleString(REMOTE_METADATA_TYPE, Property.Final);
    private static final Setting<String> REMOTE_METADATA_ENDPOINT_SETTING = Setting.simpleString(REMOTE_METADATA_ENDPOINT, Property.Final);
    private static final Setting<String> REGION_SETTING = Setting.simpleString(REGION, Property.Final);

    /**
     * Create a new SdkClient with implementation determined by the value of the Remote Metadata Type setting
     * @param client The OpenSearch node client used as the default implementation
     * @param xContentRegistry The OpenSearch XContentRegistry
     * @param settings OpenSearch cluster settings.
     * @return An instance of SdkClient which delegates to an implementation based on Remote Metadata Type
     */
    public static SdkClient createSdkClient(Client client, NamedXContentRegistry xContentRegistry, Settings settings) {
        String remoteMetadataType = REMOTE_METADATA_TYPE_SETTING.get(settings);
        String remoteMetadataEndpoint = REMOTE_METADATA_ENDPOINT_SETTING.get(settings);
        String region = REGION_SETTING.get(settings);

        switch (remoteMetadataType) {
            case REMOTE_OPENSEARCH:
                log.info("Using remote opensearch cluster as metadata store");
                return new SdkClient(new RemoteClusterIndicesClient(createOpenSearchClient(remoteMetadataEndpoint)));
            case AWS_OPENSEARCH_SERVICE:
                log.info("Using remote AWS Opensearch Service cluster as metadata store");
                return new SdkClient(new RemoteClusterIndicesClient(createAwsOpenSearchServiceClient(remoteMetadataEndpoint, region)));
            case AWS_DYNAMO_DB:
                log.info("Using dynamo DB as metadata store");
                return new SdkClient(
                    new DDBOpenSearchClient(
                        createDynamoDbClient(region),
                        new RemoteClusterIndicesClient(createOpenSearchClient(remoteMetadataEndpoint))
                    )
                );
            default:
                log.info("Using local opensearch cluster as metadata store");
                return new SdkClient(new LocalClusterIndicesClient(client, xContentRegistry));
        }
    }

    // Package private for testing
    static SdkClient wrapSdkClientDelegate(SdkClientDelegate delegate) {
        return new SdkClient(delegate);
    }

    private static DynamoDbClient createDynamoDbClient(String region) {
        if (region == null) {
            throw new IllegalStateException("REGION environment variable needs to be set!");
        }

        AwsCredentialsProviderChain credentialsProviderChain = AwsCredentialsProviderChain
            .builder()
            .addCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .addCredentialsProvider(ContainerCredentialsProvider.builder().build())
            .addCredentialsProvider(InstanceProfileCredentialsProvider.create())
            .build();

        return DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(credentialsProviderChain).build();
    }

    private static OpenSearchClient createOpenSearchClient(String remoteMetadataEndpoint) {
        try {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // Basic http(not-s) client using RestClient.
            RestClient restClient = RestClient
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
                .build();
            ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }

    private static OpenSearchClient createAwsOpenSearchServiceClient(String remoteMetadataEndpoint, String region) {
        // https://github.com/opensearch-project/opensearch-java/blob/main/guides/auth.md
        SdkHttpClient httpClient = ApacheHttpClient.builder().build();

        return new OpenSearchClient(
            new AwsSdk2Transport(
                httpClient,
                remoteMetadataEndpoint, // OpenSearch endpoint, without https://
                "es", // signing service name, use "aoss" for OpenSearch Serverless
                Region.of(region), // signing service region
                AwsSdk2TransportOptions.builder().build()
            )
        );
    }
}
