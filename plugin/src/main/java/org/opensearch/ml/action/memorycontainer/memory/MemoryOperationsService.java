/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ACTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_AFTER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_BEFORE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
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
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryContainerHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryOperationsService {

    private final MemoryContainerHelper memoryContainerHelper;

    public MemoryOperationsService(MemoryContainerHelper memoryContainerHelper) {
        this.memoryContainerHelper = memoryContainerHelper;
    }

    public void executeMemoryOperations(
        List<MemoryDecision> decisions,
        MemoryConfiguration memoryConfig,
        Map<String, String> namespace,
        User user,
        MLAddMemoriesInput input,
        ActionListener<List<MemoryResult>> listener
    ) {
        String longTermMemoryIndex = memoryConfig.getLongMemoryIndexName();
        String longTermMemoryHistoryIndex = memoryConfig.getLongMemoryHistoryIndexName();

        List<MemoryResult> results = new ArrayList<>();
        List<IndexRequest> addRequests = new ArrayList<>();
        List<UpdateRequest> updateRequests = new ArrayList<>();
        List<DeleteRequest> deleteRequests = new ArrayList<>();

        Instant now = Instant.now();

        for (MemoryDecision decision : decisions) {
            switch (decision.getEvent()) {
                case ADD:
                    MLMemory newMemory = MLMemory
                        .builder()
                        .ownerId(input.getOwnerId())
                        .memory(decision.getText())
                        .memoryType(MemoryType.SEMANTIC)
                        .namespace(namespace)
                        .tags(input.getTags())
                        .createdTime(now)
                        .lastUpdatedTime(now)
                        .build();

                    IndexRequest addRequest = new IndexRequest(longTermMemoryIndex).source(newMemory.toIndexMap());
                    addRequests.add(addRequest);

                    MemoryResult memoryResult = MemoryResult
                        .builder()
                        .ownerId(input.getOwnerId())
                        .memoryId(null)
                        .memory(decision.getText())
                        .event(MemoryEvent.ADD)
                        .oldMemory(null)
                        .build();
                    results.add(memoryResult);
                    break;

                case UPDATE:
                    Map<String, Object> updateDoc = new HashMap<>();
                    updateDoc.put(MEMORY_FIELD, decision.getText());
                    updateDoc.put(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());

                    UpdateRequest updateRequest = new UpdateRequest(longTermMemoryIndex, decision.getId()).doc(updateDoc);
                    updateRequests.add(updateRequest);
                    memoryResult = MemoryResult
                        .builder()
                        .ownerId(input.getOwnerId())
                        .memoryId(decision.getId())
                        .memory(decision.getText())
                        .event(MemoryEvent.UPDATE)
                        .oldMemory(decision.getOldMemory())
                        .build();
                    results.add(memoryResult);
                    break;

                case DELETE:
                    DeleteRequest deleteRequest = new DeleteRequest(longTermMemoryIndex, decision.getId());
                    deleteRequests.add(deleteRequest);

                    memoryResult = MemoryResult
                        .builder()
                        .ownerId(input.getOwnerId())
                        .memoryId(decision.getId())
                        .memory(decision.getText())
                        .event(MemoryEvent.DELETE)
                        .oldMemory(null)
                        .build();
                    results.add(memoryResult);
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

        ActionListener<BulkResponse> bulkResponseActionListener = ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                log.error("Bulk memory operations had failures: {}", bulkResponse.buildFailureMessage());
            }

            log.debug("Executed {} memory operations successfully", bulkResponse.getItems().length);

            BulkItemResponse[] items = bulkResponse.getItems();

            for (int i = 0; i < bulkResponse.getItems().length; i++) {
                MemoryResult result = results.get(i);
                if (result.getEvent() == MemoryEvent.ADD && !items[i].isFailed()) {
                    String actualId = items[i].getId();
                    results.get(i).setMemoryId(actualId);
                }
            }

            if (memoryConfig.isDisableHistory()) {
                listener.onResponse(results);
                return;
            }

            BulkRequest bulkHistoryRequest = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            for (MemoryResult memoryResult : results) {
                bulkHistoryRequest.add(new IndexRequest(longTermMemoryHistoryIndex).source(createMemoryHistory(memoryResult)));
            }
            ActionListener<BulkResponse> bulkHistoryResponseListener = ActionListener.wrap(bulkHistoryResponse -> {
                if (bulkHistoryResponse.hasFailures()) {
                    log.error("Bulk memory history operations had failures: {}", bulkHistoryResponse.buildFailureMessage());
                }
                listener.onResponse(results);
            }, e -> {
                log.error("Failed to execute memory history operations", e);
                listener.onFailure(e);
            });
            memoryContainerHelper.bulkIngestData(memoryConfig, bulkHistoryRequest, bulkHistoryResponseListener);
        }, e -> {
            log.error("Failed to execute memory operations", e);
            listener.onFailure(e);
        });
        memoryContainerHelper.bulkIngestData(memoryConfig, bulkRequest, bulkResponseActionListener);
    }

    private Map<String, Object> createMemoryHistory(MemoryResult memoryResult) {
        Map<String, Object> history = new HashMap<>();
        if (memoryResult.getOwnerId() != null) {
            history.put(OWNER_ID_FIELD, memoryResult.getOwnerId());
        }
        history.put(MEMORY_ID_FIELD, memoryResult.getMemoryId());
        history.put(MEMORY_ACTION_FIELD, memoryResult.getEvent().getValue());
        if (memoryResult.getOldMemory() != null) {
            history.put(MEMORY_BEFORE_FIELD, Map.of(MEMORY_FIELD, memoryResult.getOldMemory()));// TODO: support other fields like namespace
        }
        if (memoryResult.getMemory() != null) {
            history.put(MEMORY_AFTER_FIELD, Map.of(MEMORY_FIELD, memoryResult.getMemory()));// TODO: support other fields like namespace
        }
        history.put(CREATED_TIME_FIELD, Instant.now());// TODO: support other fields like namespace
        return history;
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

}
