/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SESSION_TOKEN_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.engine.annotation.Ingester;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Log4j2
@Ingester("s3")
public class S3DataIngestion extends AbstractIngestion {
    public static final String SOURCE = "source";

    public S3DataIngestion(Client client) {
        super(client);
    }

    @Override
    public double ingest(MLBatchIngestionInput mlBatchIngestionInput, int bulkSize) {
        S3Client s3 = initS3Client(mlBatchIngestionInput);

        List<String> s3Uris = (List<String>) mlBatchIngestionInput.getDataSources().get(SOURCE);
        if (Objects.isNull(s3Uris) || s3Uris.isEmpty()) {
            return 100;
        }
        boolean isSoleSource = s3Uris.size() == 1;
        List<Double> successRates = Collections.synchronizedList(new ArrayList<>());
        for (int sourceIndex = 0; sourceIndex < s3Uris.size(); sourceIndex++) {
            successRates.add(ingestSingleSource(s3, s3Uris.get(sourceIndex), mlBatchIngestionInput, sourceIndex, isSoleSource, bulkSize));
        }

        return calculateSuccessRate(successRates);
    }

    public double ingestSingleSource(
        S3Client s3,
        String s3Uri,
        MLBatchIngestionInput mlBatchIngestionInput,
        int sourceIndex,
        boolean isSoleSource,
        int bulkSize
    ) {
        String bucketName = getS3BucketName(s3Uri);
        String keyName = getS3KeyName(s3Uri);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(keyName).build();
        double successRate = 0;

        try (
            ResponseInputStream<GetObjectResponse> s3is = AccessController
                .doPrivileged((PrivilegedExceptionAction<ResponseInputStream<GetObjectResponse>>) () -> s3.getObject(getObjectRequest));
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3is, StandardCharsets.UTF_8))
        ) {
            List<String> linesBuffer = new ArrayList<>();
            String line;
            int lineCount = 0;
            // Atomic counters for tracking success and failure
            AtomicInteger successfulBatches = new AtomicInteger(0);
            AtomicInteger failedBatches = new AtomicInteger(0);
            // List of CompletableFutures to track batch ingestion operations
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                linesBuffer.add(line);
                lineCount++;

                // Process every bulkSize lines
                if (lineCount % bulkSize == 0) {
                    // Create a CompletableFuture that will be completed by the bulkResponseListener
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    batchIngest(
                        linesBuffer,
                        mlBatchIngestionInput,
                        getBulkResponseListener(successfulBatches, failedBatches, future),
                        sourceIndex,
                        isSoleSource
                    );

                    futures.add(future);
                    linesBuffer.clear();
                }
            }
            // Process any remaining lines in the buffer
            if (!linesBuffer.isEmpty()) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                batchIngest(
                    linesBuffer,
                    mlBatchIngestionInput,
                    getBulkResponseListener(successfulBatches, failedBatches, future),
                    sourceIndex,
                    isSoleSource
                );
                futures.add(future);
            }

            reader.close();

            // Combine all futures and wait for completion
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            // Wait for all tasks to complete
            allFutures.join();

            int totalBatches = successfulBatches.get() + failedBatches.get();
            successRate = (totalBatches == 0) ? 100 : (double) successfulBatches.get() / totalBatches * 100;
        } catch (S3Exception e) {
            log.error("Error reading from S3: " + e.awsErrorDetails().errorMessage());
            throw e;
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to get S3 Object: ", e);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new OpenSearchStatusException("Failed to batch ingest: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
        } finally {
            s3.close();
        }

        return successRate;
    }

    private String getS3BucketName(String s3Uri) {
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

    private String getS3KeyName(String s3Uri) {
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

    @VisibleForTesting
    public S3Client initS3Client(MLBatchIngestionInput mlBatchIngestionInput) {
        String accessKey = mlBatchIngestionInput.getCredential().get(ACCESS_KEY_FIELD);
        String secretKey = mlBatchIngestionInput.getCredential().get(SECRET_KEY_FIELD);
        String sessionToken = mlBatchIngestionInput.getCredential().get(SESSION_TOKEN_FIELD);
        String region = mlBatchIngestionInput.getCredential().get(REGION_FIELD);

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
}
