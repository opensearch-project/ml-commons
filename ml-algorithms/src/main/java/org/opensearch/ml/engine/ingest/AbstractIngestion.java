/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.utils.StringUtils.getJsonPath;
import static org.opensearch.ml.common.utils.StringUtils.obtainFieldNameFromJsonPath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AbstractIngestion implements Ingestable {

    private final Client client;

    public AbstractIngestion(Client client) {
        this.client = client;
    }

    protected ActionListener<BulkResponse> getBulkResponseListener(
        AtomicInteger successfulBatches,
        AtomicInteger failedBatches,
        CompletableFuture<Void> future
    ) {
        return ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                failedBatches.incrementAndGet();
                future.completeExceptionally(new RuntimeException(bulkResponse.buildFailureMessage()));  // Mark the future as completed
                // with an exception
                return;
            }
            log.debug("Batch Ingestion successfully");
            successfulBatches.incrementAndGet();
            future.complete(null); // Mark the future as completed successfully
        }, e -> {
            log.error("Failed to Batch Ingestion", e);
            failedBatches.incrementAndGet();
            future.completeExceptionally(e);  // Mark the future as completed with an exception
        });
    }

    protected double calculateSuccessRate(List<Double> successRates) {
        return successRates
            .stream()
            .min(Double::compare)
            .orElseThrow(
                () -> new OpenSearchStatusException(
                    "Failed to batch ingest data as not success rate is returned",
                    RestStatus.INTERNAL_SERVER_ERROR
                )
            );
    }

    /**
     * Filters fields in the map where the value contains the specified source index as a prefix.
     *
     * @param mlBatchIngestionInput The MLBatchIngestionInput.
     * @param indexInFieldMap    The source index to filter by.
     * @return A new map with only the entries that match the specified source index and correctly mapped to JsonPath.
     */
    protected Map<String, Object> filterFieldMapping(MLBatchIngestionInput mlBatchIngestionInput, int indexInFieldMap) {
        Map<String, Object> fieldMap = mlBatchIngestionInput.getFieldMapping();
        String prefix = "source[" + indexInFieldMap + "]";

        Map<String, Object> filteredFieldMap = fieldMap.entrySet().stream().filter(entry -> {
            Object value = entry.getValue();
            if (value instanceof String) {
                return ((String) value).contains(prefix);
            } else if (value instanceof List) {
                return ((List<String>) value).stream().anyMatch(val -> val.contains(prefix));
            }
            return false;
        }).collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Object value = entry.getValue();
            if (value instanceof String) {
                return getJsonPath((String) value);
            } else if (value instanceof List) {
                return ((List<String>) value)
                    .stream()
                    .filter(val -> val.contains(prefix))
                    .map(StringUtils::getJsonPath)
                    .collect(Collectors.toList());
            }
            return null;
        }));

        String[] ingestFields = mlBatchIngestionInput.getIngestFields();
        if (ingestFields != null) {
            Arrays
                .stream(ingestFields)
                .filter(Objects::nonNull)
                .filter(val -> val.contains(prefix))
                .map(StringUtils::getJsonPath)
                .forEach(jsonPath -> {
                    filteredFieldMap.put(obtainFieldNameFromJsonPath(jsonPath), jsonPath);
                });
        }

        return filteredFieldMap;
    }

    /**
     * Produce the source as a Map to be ingested in to OpenSearch.
     *
     * @param jsonStr The MLBatchIngestionInput.
     * @param fieldMapping  The field mapping that includes all the field name and Json Path for the data.
     * @return A new map that contains all the fields and data for ingestion.
     */
    protected Map<String, Object> processFieldMapping(String jsonStr, Map<String, Object> fieldMapping) {
        Map<String, Object> jsonMap = new HashMap<>();
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return jsonMap;
        }

        fieldMapping.entrySet().stream().forEach(entry -> {
            Object value = entry.getValue();
            if (value instanceof String) {
                String jsonPath = (String) value;
                jsonMap.put(entry.getKey(), JsonPath.read(jsonStr, jsonPath));
            } else if (value instanceof List) {
                ((List<String>) value).stream().forEach(jsonPath -> { jsonMap.put(entry.getKey(), JsonPath.read(jsonStr, jsonPath)); });
            }
        });

        return jsonMap;
    }

    protected void batchIngest(
        List<String> sourceLines,
        MLBatchIngestionInput mlBatchIngestionInput,
        ActionListener<BulkResponse> bulkResponseListener,
        int sourceIndex,
        boolean isSoleSource
    ) {
        BulkRequest bulkRequest = new BulkRequest();
        sourceLines.stream().forEach(jsonStr -> {
            Map<String, Object> filteredMapping = isSoleSource
                ? mlBatchIngestionInput.getFieldMapping()
                : filterFieldMapping(mlBatchIngestionInput, sourceIndex);
            Map<String, Object> jsonMap = processFieldMapping(jsonStr, filteredMapping);
            if (jsonMap.isEmpty()) {
                return;
            }
            if (isSoleSource && !jsonMap.containsKey("_id")) {
                IndexRequest indexRequest = new IndexRequest(mlBatchIngestionInput.getIndexName());
                indexRequest.source(jsonMap);
                bulkRequest.add(indexRequest);
            } else {
                // bulk update docs as they were partially ingested
                if (!jsonMap.containsKey("_id")) {
                    throw new IllegalArgumentException("The id filed must be provided to match documents for multiple sources");
                }
                String id = (String) jsonMap.remove("_id");
                UpdateRequest updateRequest = new UpdateRequest(mlBatchIngestionInput.getIndexName(), id).doc(jsonMap).upsert(jsonMap);
                bulkRequest.add(updateRequest);
            }
        });
        if (bulkRequest.numberOfActions() == 0) {
            bulkResponseListener
                .onFailure(
                    new IllegalArgumentException("the bulk ingestion is empty: please check your field mapping to match your sources")
                );
            return;
        }
        client.bulk(bulkRequest, bulkResponseListener);
    }

    private void populateJsonMap(Map<String, Object> jsonMap, List<String> fieldNames, List<?> modelData) {
        if (modelData != null) {
            if (modelData.size() != fieldNames.size()) {
                throw new IllegalArgumentException("The fieldMapping and source data do not match");
            }

            for (int index = 0; index < modelData.size(); index++) {
                jsonMap.put(fieldNames.get(index), modelData.get(index));
            }
        }
    }
}
