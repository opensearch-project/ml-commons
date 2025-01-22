/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.connector.AbstractConnector.*;
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
import org.opensearch.ml.common.utils.S3Utils;
import org.opensearch.ml.engine.annotation.Ingester;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.ResponseInputStream;
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
        String accessKey = mlBatchIngestionInput.getCredential().get(ACCESS_KEY_FIELD);
        String secretKey = mlBatchIngestionInput.getCredential().get(SECRET_KEY_FIELD);
        String sessionToken = mlBatchIngestionInput.getCredential().get(SESSION_TOKEN_FIELD);
        String region = mlBatchIngestionInput.getCredential().get(REGION_FIELD);

        S3Client s3 = S3Utils.initS3Client(accessKey, secretKey, region, sessionToken);

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
        String bucketName = S3Utils.getS3BucketName(s3Uri);
        String keyName = S3Utils.getS3KeyName(s3Uri);
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
}
