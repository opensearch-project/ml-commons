/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.opensearch.OpenSearchException;
import org.opensearch.SpecialPermission;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.common.inject.AbstractModule;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;

import java.security.AccessController;
import java.security.PrivilegedAction;

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

    private final String remoteStoreType;
    private final String remoteMetadataEndpoint;
    private final String region; // not using with RestClient

    static {
        // Aws v2 sdk tries to load a default profile from home path which is restricted. Hence, setting these to random valid paths.
        // @SuppressForbidden(reason = "Need to provide this override to v2 SDK so that path does not default to home path")
        if (ProfileFileSystemSetting.AWS_SHARED_CREDENTIALS_FILE.getStringValue().isEmpty()) {
            SocketAccess.doPrivileged(
                    () -> System.setProperty(
                            ProfileFileSystemSetting.AWS_SHARED_CREDENTIALS_FILE.property(),
                            System.getProperty("opensearch.path.conf")
                    )
            );
        }
        if (ProfileFileSystemSetting.AWS_CONFIG_FILE.getStringValue().isEmpty()) {
            SocketAccess.doPrivileged(
                    () -> System.setProperty(ProfileFileSystemSetting.AWS_CONFIG_FILE.property(), System.getProperty("opensearch.path.conf"))
            );
        }
    }

    private static final class SocketAccess {
        private SocketAccess() {}

        public static <T> T doPrivileged(PrivilegedAction<T> operation) {
            SpecialPermission.check();
            return AccessController.doPrivileged(operation);
        }
    }

    /**
     * Instantiate this module using environment variables
     */
    public SdkClientModule() {
        this(System.getenv(REMOTE_METADATA_TYPE), System.getenv(REMOTE_METADATA_ENDPOINT), System.getenv(REGION));
    }

    /**
     * Instantiate this module specifying the endpoint and region. Package private for testing.
     * @param remoteMetadataEndpoint The remote endpoint
     * @param region The region
     */
    SdkClientModule(String remoteStoreType, String remoteMetadataEndpoint, String region) {
        this.remoteStoreType = remoteStoreType;
        this.remoteMetadataEndpoint = remoteMetadataEndpoint;
        this.region = region == null ? "us-west-2" : region;
    }

    @Override
    protected void configure() {/*
        if (this.remoteStoreType == null) {
            log.info("Using local opensearch cluster as metadata store");
            bind(SdkClient.class).to(LocalClusterIndicesClient.class);
            return;
        }

        switch (this.remoteStoreType) {
            case REMOTE_OPENSEARCH:
                log.info("Using remote opensearch cluster as metadata store");
                bind(SdkClient.class).toInstance(new RemoteClusterIndicesClient(createOpenSearchClient()));
                return;
            case AWS_DYNAMO_DB:
                log.info("Using dynamo DB as metadata store");
                bind(SdkClient.class).toInstance(new DDBOpenSearchClient(createDynamoDbClient()));
                return;
            default:
                log.info("Using local opensearch cluster as metadata store");
                bind(SdkClient.class).to(LocalClusterIndicesClient.class);
        }*/
        bind(SdkClient.class).toInstance(new DDBOpenSearchClient(createDynamoDbClient()));
    }

    private DynamoDbClient createDynamoDbClient() {
        if (this.region == null) {
            throw new IllegalStateException("REGION environment variable needs to be set!");
        }

        return DynamoDbClient.builder()
                .region(Region.of(this.region))
                .build();
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
            ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));
        } catch (Exception e) {
            throw new OpenSearchException(e);
        }
    }
}
