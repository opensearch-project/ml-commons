/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.utils.StringUtils.obtainFieldNameFromJsonPath;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.INGESTFIELDS;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.OUTPUT;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.OUTPUTIELDS;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Ingester("openai")
public class OpenAIDataIngestion implements Ingestable {
    private static final String API_KEY = "openAI_key";
    private static final String API_URL = "https://api.openai.com/v1/files/";

    public static final String SOURCE = "source";
    private final Client client;

    public OpenAIDataIngestion(Client client) {
        this.client = client;
    }

    @Override
    public double ingest(MLBatchIngestionInput mlBatchIngestionInput) {
        double successRate = 0;
        try {
            String apiKey = mlBatchIngestionInput.getCredential().get(API_KEY);
            String fileId = mlBatchIngestionInput.getDataSources().get(SOURCE);
            URL url = new URL(API_URL + fileId + "/content");

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            InputStreamReader inputStreamReader = AccessController
                .doPrivileged((PrivilegedExceptionAction<InputStreamReader>) () -> new InputStreamReader(connection.getInputStream()));
            BufferedReader reader = new BufferedReader(inputStreamReader);

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
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to read from OpenAI file API: ", e);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new OpenSearchStatusException("Failed to batch ingest: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
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
            Map<String, Object> jsonMap = processFieldMapping(jsonStr, mlBatchIngestionInput.getFieldMapping());
            IndexRequest indexRequest = new IndexRequest(mlBatchIngestionInput.getIndexName()).source(jsonMap);

            bulkRequest.add(indexRequest);
        });
        client.bulk(bulkRequest, bulkResponseListener);
    }

    private Map<String, Object> processFieldMapping(String jsonStr, Map<String, Object> fieldMapping) {
        String outputJsonPath = (String) fieldMapping.get(OUTPUT);
        List<List> outputs = (List<List>) JsonPath.read(jsonStr, outputJsonPath);
        List<String> outputFields = (List<String>) fieldMapping.get(OUTPUTIELDS);
        List<String> ingestFieldsJsonPath = (List<String>) fieldMapping.get(INGESTFIELDS);

        Map<String, Object> jsonMap = new HashMap<>();
        if (outputs.size() != outputFields.size()) {
            throw new IllegalArgumentException("the fieldMapping and source data do not match");
        }
        for (int index = 0; index < outputs.size(); index++) {
            jsonMap.put(outputFields.get(index), outputs.get(index));
        }

        for (String fieldPath : ingestFieldsJsonPath) {
            jsonMap.put(obtainFieldNameFromJsonPath(fieldPath), JsonPath.read(jsonStr, fieldPath));
        }

        return jsonMap;
    }
}
