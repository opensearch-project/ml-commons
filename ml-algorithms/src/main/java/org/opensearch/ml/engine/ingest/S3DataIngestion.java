/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SESSION_TOKEN_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.fromJson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.engine.annotation.Ingester;

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
public class S3DataIngestion implements Ingestable {
    public static final String SOURCE = "source";
    private final Client client;

    public S3DataIngestion(Client client) {
        this.client = client;
    }

    @Override
    public double ingest(MLBatchIngestionInput mlBatchIngestionInput) {
        S3Client s3 = initS3Client(mlBatchIngestionInput);
        double successRate = 0;

        String s3Uri = mlBatchIngestionInput.getDataSources().get(SOURCE);
        String bucketName = getS3BucketName(s3Uri);
        String keyName = getS3KeyName(s3Uri);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(keyName).build();

        try {
            ResponseInputStream<GetObjectResponse> s3is = AccessController
                .doPrivileged((PrivilegedExceptionAction<ResponseInputStream<GetObjectResponse>>) () -> s3.getObject(getObjectRequest));
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3is, StandardCharsets.UTF_8));

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

                // Process every 100 lines
                if (lineCount == 100) {
                    // Create a CompletableFuture that will be completed by the bulkResponseListener
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    batchIngest(linesBuffer, mlBatchIngestionInput, getBulkResponseListener(successfulBatches, failedBatches, future));

                    futures.add(future);
                    linesBuffer.clear();
                    lineCount = 0;
                }
            }
            // Process any remaining lines in the buffer
            if (!linesBuffer.isEmpty()) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                batchIngest(linesBuffer, mlBatchIngestionInput, getBulkResponseListener(successfulBatches, failedBatches, future));
                futures.add(future);
            }

            reader.close();

            // Combine all futures and wait for completion
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            // Wait for all tasks to complete
            allFutures.join();

            int totalBatches = successfulBatches.get() + failedBatches.get();
            successRate = (double) successfulBatches.get() / totalBatches * 100;
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

    private ActionListener<BulkResponse> getBulkResponseListener(
        AtomicInteger successfulBatches,
        AtomicInteger failedBatches,
        CompletableFuture<Void> future
    ) {
        return ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                failedBatches.incrementAndGet();
                future.completeExceptionally(new RuntimeException(bulkResponse.buildFailureMessage()));  // Mark the future as completed
                                                                                                         // with an exception
            }
            log.debug("Batch Ingestion successfully");
            successfulBatches.incrementAndGet();
            future.complete(null); // Mark the future as completed successfully
        }, e -> {
            log.error("Failed to bulk update model state", e);
            failedBatches.incrementAndGet();
            future.completeExceptionally(e);  // Mark the future as completed with an exception
        });
    }

    private void batchIngest(
        List<String> sourceLines,
        MLBatchIngestionInput mlBatchIngestionInput,
        ActionListener<BulkResponse> bulkResponseListener
    ) {
        BulkRequest bulkRequest = new BulkRequest();
        sourceLines.stream().forEach(jsonStr -> {
            Map<String, Object> jsonMap = fromJson(jsonStr, "SageMakerOutput");
            processFieldMapping(jsonMap, mlBatchIngestionInput.getFieldMapping());
            IndexRequest indexRequest = new IndexRequest(mlBatchIngestionInput.getIndexName()).source(jsonMap);

            bulkRequest.add(indexRequest);
        });
        client.bulk(bulkRequest, bulkResponseListener);
    }

    private void processFieldMapping(Map<String, Object> jsonMap, Map<String, String> fieldMapping) {
        List<List> smOutput = (List<List>) jsonMap.get("SageMakerOutput");
        List<String> smInput = (List<String>) jsonMap.get("content");
        if (smInput.size() == smInput.size() && smInput.size() == fieldMapping.size()) {
            int index = 0;
            for (Map.Entry<String, String> mapping : fieldMapping.entrySet()) {
                // key is the field name for input String, value is the field name for embedded output
                jsonMap.put(mapping.getKey(), smInput.get(index));
                jsonMap.put(mapping.getValue(), smOutput.get(index));
                index++;
            }
            jsonMap.remove("content");
            jsonMap.remove("SageMakerOutput");
        } else {
            throw new IllegalArgumentException("the fieldMapping and source data do not match");
        }
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

    private S3Client initS3Client(MLBatchIngestionInput mlBatchIngestionInput) {
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
