/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryOperationsService {

    private final Client client;

    public MemoryOperationsService(Client client) {
        this.client = client;
    }

    public void executeMemoryOperations(
        List<MemoryDecision> decisions,
        MemoryConfiguration memoryConfig,
        Map<String, String> namespace,
        User user,
        MLAddMemoriesInput input,
        MemoryConfiguration configuration,
        ActionListener<List<MemoryResult>> listener
    ) {
        String longTermMemoryIndex = memoryConfig.getLongMemoryIndexName();
        String longTermMemoryHistoryIndex = memoryConfig.getLongMemoryHistoryIndexName();

        List<MemoryResult> results = new ArrayList<>();
        List<IndexRequest> addRequests = new ArrayList<>();
        List<UpdateRequest> updateRequests = new ArrayList<>();
        List<DeleteRequest> deleteRequests = new ArrayList<>();

        List<IndexRequest> historyAddRequests = new ArrayList<>();

        Instant now = Instant.now();

        for (MemoryDecision decision : decisions) {
            switch (decision.getEvent()) {
                case ADD:
                    MLMemory newMemory = MLMemory
                        .builder()
                        .memory(decision.getText())
                        .memoryType(MemoryType.SEMANTIC)
                        .namespace(namespace)
                        .tags(input.getTags())
                        .createdTime(now)
                        .lastUpdatedTime(now)
                        .build();

                    IndexRequest addRequest = new IndexRequest(longTermMemoryIndex).source(newMemory.toIndexMap());
                    addRequests.add(addRequest);

                    MemoryResult memoryResult = MemoryResult.builder().memoryId(null).memory(decision.getText()).event(MemoryEvent.ADD).oldMemory(null).build();
                    results.add(memoryResult);
                    historyAddRequests.add(new IndexRequest(longTermMemoryHistoryIndex).source(createMemoryHistory(memoryResult)));
                    break;

                case UPDATE:
                    Map<String, Object> updateDoc = new HashMap<>();
                    updateDoc.put(MEMORY_FIELD, decision.getText());
                    updateDoc.put(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());

                    UpdateRequest updateRequest = new UpdateRequest(longTermMemoryIndex, decision.getId()).doc(updateDoc);
                    updateRequests.add(updateRequest);
                    memoryResult = MemoryResult.builder().memoryId(decision.getId()).memory(decision.getText()).event(MemoryEvent.UPDATE).oldMemory(decision.getOldMemory()).build();
                    results.add(memoryResult);
                    historyAddRequests.add(new IndexRequest(longTermMemoryHistoryIndex).source(createMemoryHistory(memoryResult)));
                    break;

                case DELETE:
                    DeleteRequest deleteRequest = new DeleteRequest(longTermMemoryIndex, decision.getId());
                    deleteRequests.add(deleteRequest);

                    memoryResult = MemoryResult.builder().memoryId(decision.getId()).memory(decision.getText()).event(MemoryEvent.DELETE).oldMemory(null).build();
                    results.add(memoryResult);
                    historyAddRequests.add(new IndexRequest(longTermMemoryHistoryIndex).source(createMemoryHistory(memoryResult)));
                    break;

                case NONE:
                    // NONE events are not included in the response
                    // They represent facts that didn't change and don't need user visibility
                    break;
            }
        }

        BulkRequest bulkRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        for (IndexRequest request : addRequests) {
            bulkRequest.add(request);
        }
        for (UpdateRequest request : updateRequests) {
            bulkRequest.add(request);
        }
        for (DeleteRequest request : deleteRequests) {
            bulkRequest.add(request);
        }

        if (bulkRequest.requests().isEmpty()) {
            log.debug("No memory operations to execute");
            listener.onResponse(results);
            return;
        }

        client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                log.error("Bulk memory operations had failures: {}", bulkResponse.buildFailureMessage());
            }

            log.debug("Executed {} memory operations successfully", bulkResponse.getItems().length);

            BulkItemResponse[] items = bulkResponse.getItems();
            int itemIndex = 0;

            for (int i = 0; i < results.size(); i++) {
                MemoryResult result = results.get(i);
                if (result.getEvent() == MemoryEvent.ADD && itemIndex < items.length) {
                    while (itemIndex < items.length && items[itemIndex].getOpType() != DocWriteRequest.OpType.INDEX) {
                        itemIndex++;
                    }

                    if (itemIndex < items.length && !items[itemIndex].isFailed()) {
                        String actualId = items[itemIndex].getId();
                        results
                            .set(
                                i,
                                MemoryResult
                                    .builder()
                                    .memoryId(actualId)
                                    .memory(result.getMemory())
                                    .event(MemoryEvent.ADD)
                                    .oldMemory(null)
                                    .build()
                            );
                    }
                    itemIndex++;
                }
            }

            BulkRequest bulkHistoryRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            for (IndexRequest request : historyAddRequests) {
                bulkHistoryRequest.add(request);
            }
            client.bulk(bulkHistoryRequest, ActionListener.wrap(bulkHistoryResponse -> {
                if (bulkHistoryResponse.hasFailures()) {
                    log.error("Bulk memory history operations had failures: {}", bulkHistoryResponse.buildFailureMessage());
                }
                listener.onResponse(results);
            }, e -> {
                log.error("Failed to execute memory history operations", e);
                listener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to execute memory operations", e);
            listener.onFailure(e);
        }));
    }

    private Map<String, Object> createMemoryHistory(MemoryResult memoryResult) {
        Map<String, Object> history = new HashMap<>();
        history.put("memory_id", memoryResult.getMemoryId());
        history.put("action", memoryResult.getEvent().getValue());
        if (memoryResult.getOldMemory() != null) {
            history.put("before", Map.of("memory", memoryResult.getOldMemory()));//TODO: support other fields like namespace
        }
        if (memoryResult.getMemory() != null) {
            history.put("after", Map.of("memory", memoryResult.getMemory()));//TODO: support other fields like namespace
        }
        history.put("created_time", Instant.now());//TODO: support other fields like namespace
        return history;
    }

    public void bulkIndexMemoriesWithResults(
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        String sessionId,
        String indexName,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        if (indexRequests.isEmpty()) {
            log.warn("No memories to index");
            actionListener.onFailure(new IllegalStateException("No memories to index"));
            return;
        }

        indexMemoriesSequentiallyWithResults(indexRequests, memoryInfos, 0, sessionId, indexName, new ArrayList<>(), actionListener);
    }

    public void createFactMemoriesFromList(
        List<String> facts,
        String indexName,
        MLAddMemoriesInput input,
        Map<String, String> strategyNameSpace,
        User user,
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos
    ) {
        Instant now = Instant.now();
        for (String fact : facts) {
            MLMemory factMemory = MLMemory
                .builder()
                .memory(fact)
                .memoryType(MemoryType.SEMANTIC)
                .namespace(strategyNameSpace)
                .tags(input.getTags())
                .createdTime(now)
                .lastUpdatedTime(now)
                .build();

            IndexRequest request = new IndexRequest(indexName).source(factMemory.toIndexMap());
            indexRequests.add(request);

            memoryInfos.add(new MemoryInfo(null, factMemory.getMemory(), factMemory.getMemoryType(), true));
        }
    }

    private void indexMemoriesSequentiallyWithResults(
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos,
        int currentIndex,
        String sessionId,
        String indexName,
        List<MemoryResult> results,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        if (currentIndex >= indexRequests.size()) {
            log.debug("Successfully indexed {} memories in index {}", indexRequests.size(), indexName);
            MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(results).sessionId(sessionId).build();
            actionListener.onResponse(response);
            return;
        }

        IndexRequest currentRequest = indexRequests.get(currentIndex);
        client.index(currentRequest, ActionListener.wrap(indexResponse -> {
            String memoryId = indexResponse.getId();

            MemoryInfo info = memoryInfos.get(currentIndex);
            info.setMemoryId(memoryId);

            if (info.isIncludeInResponse()) {
                results
                    .add(
                        MemoryResult.builder().memoryId(memoryId).memory(info.getContent()).event(MemoryEvent.ADD).oldMemory(null).build()
                    );
            }

            indexMemoriesSequentiallyWithResults(
                indexRequests,
                memoryInfos,
                currentIndex + 1,
                sessionId,
                indexName,
                results,
                actionListener
            );
        }, actionListener::onFailure));
    }
}
