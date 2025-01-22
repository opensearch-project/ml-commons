/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.connector.AbstractConnector.*;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Log4j2
public class S3Utils {
    @VisibleForTesting
    public static S3Client initS3Client(String accessKey, String secretKey, String sessionToken, String region) {
        AwsCredentials credentials = sessionToken == null
            ? AwsBasicCredentials.create(accessKey, secretKey)
            : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);

        try {
            S3Client s3 = AccessController
                .doPrivileged(
                    (PrivilegedExceptionAction<S3Client>) () -> S3Client
                        .builder()
                        .region(Region.of(region))  // Specify the region here
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build()
                );
            return s3;
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Can't load credentials", e);
        }
    }

    public static void putObject(S3Client s3Client, String bucketName, String key, String content) {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key).build();

                s3Client.putObject(request, RequestBody.fromString(content));
                log.debug("Successfully uploaded file to S3: s3://{}/{}", bucketName, key);
                return null; // Void return type for doPrivileged
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to upload file to S3: s3://" + bucketName + "/" + key, e);
        }
    }

    public static String getS3BucketName(String s3Uri) {
        // Remove the "s3://" prefix
        String uriWithoutPrefix = s3Uri.substring(5);
        // Find the first slash after the bucket name
        int slashIndex = uriWithoutPrefix.indexOf('/');
        // If there is no slash, the entire remaining string is the bucket name
        if (slashIndex == -1) {
            return uriWithoutPrefix;
        }
        // Otherwise, the bucket name is the substring up to the first slash
        return uriWithoutPrefix.substring(0, slashIndex);
    }

    public static String getS3KeyName(String s3Uri) {
        String uriWithoutPrefix = s3Uri.substring(5);
        // Find the first slash after the bucket name
        int slashIndex = uriWithoutPrefix.indexOf('/');
        // If there is no slash, it means there is no key, return an empty string or handle as needed
        if (slashIndex == -1) {
            return "";
        }
        // The key name is the substring after the first slash
        return uriWithoutPrefix.substring(slashIndex + 1);
    }

}
