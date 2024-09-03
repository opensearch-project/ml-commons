/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INGESTFIELDS;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INPUTFIELDS;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.OUTPUTIELDS;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.SOURCE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;

import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public class S3DataIngestionTests {

    private MLBatchIngestionInput mlBatchIngestionInput;
    private S3DataIngestion s3DataIngestion;

    @Mock
    Client client;

    @Mock
    S3Client s3Client;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        s3DataIngestion = new S3DataIngestion(client);

        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("input", "$.content");
        fieldMap.put("output", "$.SageMakerOutput");
        fieldMap.put(INPUTFIELDS, Arrays.asList("chapter", "title"));
        fieldMap.put(OUTPUTIELDS, Arrays.asList("chapter_embedding", "title_embedding"));
        fieldMap.put(INGESTFIELDS, Arrays.asList("$.id"));

        Map<String, String> credential = Map
            .of("region", "us-east-1", "access_key", "some accesskey", "secret_key", "some secret", "session_token", "some token");
        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put(SOURCE, Arrays.asList("s3://offlinebatch/output/sagemaker_djl_batch_input.json.out"));

        mlBatchIngestionInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(fieldMap)
            .credential(credential)
            .dataSources(dataSource)
            .build();
    }

    @Test
    public void testInitS3Client_WithSessionToken() {
        // Mock the S3Client and its builder
        S3Client mockS3Client = mock(S3Client.class);
        S3ClientBuilder mockBuilder = mock(S3ClientBuilder.class);

        // Mock the builder method chain
        when(mockBuilder.region(Region.of("us-east-1"))).thenReturn(mockBuilder);
        // Correctly handle the credentialsProvider method
        when(mockBuilder.credentialsProvider(any(StaticCredentialsProvider.class))).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockS3Client);

        // try (MockedStatic<S3Client> s3ClientStaticMock = mockStatic(S3Client.class)) {
        // s3ClientStaticMock.when(S3Client::builder).thenReturn(mockBuilder);
        //
        // AwsCredentials credentials = AwsSessionCredentials.create("some accesskey", "some secret", "some token");
        //
        // // Act
        // S3Client s3 = S3Client
        // .builder()
        // .region(Region.of("us-east-1"))
        // .credentialsProvider(StaticCredentialsProvider.create(credentials))
        // .build();
        //
        // // Assert
        // assertEquals(mockS3Client, s3);
        //
        // // Verify the interactions
        // verify(mockBuilder).region(Region.of("us-east-1"));
        // verify(mockBuilder).credentialsProvider(any(StaticCredentialsProvider.class));
        // verify(mockBuilder).build();
        // }
    }
}
