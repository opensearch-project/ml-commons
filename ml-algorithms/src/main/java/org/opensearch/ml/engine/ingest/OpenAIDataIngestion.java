/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.engine.annotation.Ingester;
import org.opensearch.secure_sm.AccessController;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Ingester("openai")
public class OpenAIDataIngestion extends AbstractIngestion {
    private static final String API_KEY = "openAI_key";
    private static final String API_URL = "https://api.openai.com/v1/files/";
    public static final String SOURCE = "source";

    public OpenAIDataIngestion(Client client) {
        super(client);
    }

    @Override
    public double ingest(MLBatchIngestionInput mlBatchIngestionInput, int bulkSize) {
        List<String> sources = (List<String>) mlBatchIngestionInput.getDataSources().get(SOURCE);
        if (Objects.isNull(sources) || sources.isEmpty()) {
            return 100;
        }

        boolean isSoleSource = sources.size() == 1;
        List<Double> successRates = Collections.synchronizedList(new ArrayList<>());
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            successRates.add(ingestSingleSource(sources.get(sourceIndex), mlBatchIngestionInput, sourceIndex, isSoleSource, bulkSize));
        }

        return calculateSuccessRate(successRates);
    }

    private double ingestSingleSource(
        String fileId,
        MLBatchIngestionInput mlBatchIngestionInput,
        int sourceIndex,
        boolean isSoleSource,
        int bulkSize
    ) {
        double successRate = 0;
        try {
            String apiKey = mlBatchIngestionInput.getCredential().get(API_KEY);
            URL url = new URL(API_URL + fileId + "/content");

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            try (
                InputStreamReader inputStreamReader = AccessController
                    .doPrivilegedChecked(() -> new InputStreamReader(connection.getInputStream()));
                BufferedReader reader = new BufferedReader(inputStreamReader)
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
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new OpenSearchStatusException("Failed to batch ingest: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
        }

        return successRate;
    }
}
