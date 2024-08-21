/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.OpenSearchException;
import org.opensearch.SpecialPermission;
import org.opensearch.client.Client;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientDelegate;
import org.opensearch.sdk.SdkClientSettings;
import org.opensearch.sdk.client.LocalClusterIndicesClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * A class to create a {@link SdkClient} with implementation based on settings
 */
@Log4j2
public class SdkClientFactory {

    /**
     * Create a new SdkClient with implementation determined by the value of the Remote Metadata Type setting
     * @param client The OpenSearch node client used as the default implementation
     * @param xContentRegistry The OpenSearch XContentRegistry
     * @param settings OpenSearch cluster settings.
     * @return An instance of SdkClient which delegates to an implementation based on Remote Metadata Type
     */
    public static SdkClient createSdkClient(Client client, NamedXContentRegistry xContentRegistry, Settings settings) {
        String remoteMetadataType = SdkClientSettings.REMOTE_METADATA_TYPE.get(settings);
        String remoteMetadataEndpoint = SdkClientSettings.REMOTE_METADATA_ENDPOINT.get(settings);
        String region = SdkClientSettings.REMOTE_METADATA_REGION.get(settings);

        switch (remoteMetadataType) {
            case SdkClientSettings.REMOTE_OPENSEARCH:
                if (Strings.isBlank(remoteMetadataEndpoint)) {
                    throw new OpenSearchException("Remote Opensearch client requires a metadata endpoint.");
                }
                log.info("Using remote opensearch cluster as metadata store");
                return new SdkClient(new RemoteClusterIndicesClient(createOpenSearchClient(remoteMetadataEndpoint)));
            case SdkClientSettings.AWS_OPENSEARCH_SERVICE:
                if (Strings.isBlank(remoteMetadataEndpoint) || Strings.isBlank(region)) {
                    throw new OpenSearchException("AWS Opensearch Service client requires a metadata endpoint and region.");
                }
                log.info("Using remote AWS Opensearch Service cluster as metadata store");
                return new SdkClient(new RemoteClusterIndicesClient(createAwsOpenSearchServiceClient(remoteMetadataEndpoint, region)));
            case SdkClientSettings.AWS_DYNAMO_DB:
                if (Strings.isBlank(remoteMetadataEndpoint) || Strings.isBlank(region)) {
                    throw new OpenSearchException("AWS Opensearch Service client requires a metadata endpoint and region.");
                }
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
        } else if (region.equals("local")) {
            return SocketAccess
                .doPrivileged(
                    (PrivilegedAction<DynamoDbClient>) () -> DynamoDbClient
                        .builder()
                        .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                        .endpointOverride(URI.create("http://localhost:8000"))
                        .httpClient(UrlConnectionHttpClient.builder().build())
                        .region(Region.US_WEST_2)
                        // Demo only, these are not real credentials anywhere
                        .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create("fakeAccessKeyId", "fakeSecretAccessKey"))
                        )
                        .build()
                );
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
            Map<String, String> env = System.getenv();
            String user = env.getOrDefault("user", "admin");
            String pass = env.getOrDefault("password", "MySecurePassword123");
            // Endpoint syntax: https://127.0.0.1:9200
            HttpHost host = HttpHost.create(remoteMetadataEndpoint);
            SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(null, (chain, authType) -> true).build();
            ApacheHttpClient5Transport transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setMapper(
                    new JacksonJsonpMapper(
                        new ObjectMapper()
                            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                            .registerModule(new JavaTimeModule())
                    )
                )
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(user, pass.toCharArray()));
                    if (URIScheme.HTTP.getId().equalsIgnoreCase(host.getSchemeName())) {
                        // No SSL/TLS
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    // Disable SSL/TLS verification as our local testing clusters use self-signed certificates
                    final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder
                        .create()
                        .setSslContext(sslContext)
                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build();
                    final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
                        .create()
                        .setTlsStrategy(tlsStrategy)
                        .build();
                    return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).setConnectionManager(connectionManager);
                })
                .build();
            return new OpenSearchClient(transport);
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }

    private static OpenSearchClient createAwsOpenSearchServiceClient(String remoteMetadataEndpoint, String region) {
        // https://github.com/opensearch-project/opensearch-java/blob/main/guides/auth.md
        return new OpenSearchClient(
            new AwsSdk2Transport(
                ApacheHttpClient.builder().build(),
                remoteMetadataEndpoint.replaceAll("^https?://", ""), // OpenSearch endpoint, without https://
                "es", // signing service name, use "aoss" for OpenSearch Serverless
                Region.of(region), // signing service region
                AwsSdk2TransportOptions.builder().build()
            )
        );
    }

    private static final class SocketAccess {
        private SocketAccess() {}

        public static <T> T doPrivileged(PrivilegedAction<T> operation) {
            SpecialPermission.check();
            return AccessController.doPrivileged(operation);
        }
    }
}
