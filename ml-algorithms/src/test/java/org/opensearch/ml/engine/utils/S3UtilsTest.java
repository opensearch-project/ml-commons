/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class S3UtilsTest {

    @Mock
    private S3Client s3Client;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testInitS3Client() {
        String accessKey = "test-access-key";
        String secretKey = "test-secret-key";
        String sessionToken = "test-session-token";
        String region = "us-west-2";

        S3Client client = S3Utils.initS3Client(accessKey, secretKey, sessionToken, region);
        assertNotNull(client);
    }

    @Test
    public void testInitS3ClientWithoutSessionToken() {
        String accessKey = "test-access-key";
        String secretKey = "test-secret-key";
        String region = "us-west-2";

        S3Client client = S3Utils.initS3Client(accessKey, secretKey, null, region);
        assertNotNull(client);
    }

    @Test
    public void testPutObject() {
        String bucketName = "test-bucket";
        String key = "test-key";
        String content = "test-content";

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());

        S3Utils.putObject(s3Client, bucketName, key, content);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testGetS3BucketName() {
        String s3Uri = "s3://test-bucket/path/to/file";
        assertEquals("test-bucket", S3Utils.getS3BucketName(s3Uri));

        s3Uri = "s3://test-bucket";
        assertEquals("test-bucket", S3Utils.getS3BucketName(s3Uri));
    }

    @Test
    public void testGetS3KeyName() {
        String s3Uri = "s3://test-bucket/path/to/file";
        assertEquals("path/to/file", S3Utils.getS3KeyName(s3Uri));

        s3Uri = "s3://test-bucket";
        assertEquals("", S3Utils.getS3KeyName(s3Uri));
    }
}
