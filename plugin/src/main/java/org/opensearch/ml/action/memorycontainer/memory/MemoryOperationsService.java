/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
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
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryOperationsService {

    private final Client client;
    private final MemoryEmbeddingHelper memoryEmbeddingHelper;

    public MemoryOperationsService(Client client, MemoryEmbeddingHelper memoryEmbeddingHelper) {
        this.client = client;
        this.memoryEmbeddingHelper = memoryEmbeddingHelper;
    }

    public void executeMemoryOperations(
        List<MemoryDecision> decisions,
        String indexName,
        String sessionId,
        User user,
        MLAddMemoriesInput input,
        MemoryStorageConfig storageConfig,
        ActionListener<List<MemoryResult>> listener
    ) {
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
                        .sessionId(sessionId)
                        .memory(decision.getText())
                        .memoryType(MemoryType.FACT)
                        .userId(user != null ? user.getName() : null)
                        .agentId(input.getAgentId())
                        .tags(input.getTags())
                        .createdTime(now)
                        .lastUpdatedTime(now)
                        .build();

                    IndexRequest addRequest = new IndexRequest(indexName).source(newMemory.toIndexMap());
                    addRequests.add(addRequest);

                    results
                        .add(
                            MemoryResult.builder().memoryId(null).memory(decision.getText()).event(MemoryEvent.ADD).oldMemory(null).build()
                        );
                    break;

                case UPDATE:
                    Map<String, Object> updateDoc = new HashMap<>();
                    updateDoc.put(MEMORY_FIELD, decision.getText());
                    updateDoc.put(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());

                    UpdateRequest updateRequest = new UpdateRequest(indexName, decision.getId()).doc(updateDoc);
                    updateRequests.add(updateRequest);

                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.UPDATE)
                                .oldMemory(decision.getOldMemory())
                                .build()
                        );
                    break;

                case DELETE:
                    DeleteRequest deleteRequest = new DeleteRequest(indexName, decision.getId());
                    deleteRequests.add(deleteRequest);

                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.DELETE)
                                .oldMemory(null)
                                .build()
                        );
                    break;

                case NONE:
                    results
                        .add(
                            MemoryResult
                                .builder()
                                .memoryId(decision.getId())
                                .memory(decision.getText())
                                .event(MemoryEvent.NONE)
                                .oldMemory(null)
                                .build()
                        );
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

            if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
                updateEmbeddingsForOperations(results, indexName, storageConfig, listener);
            } else {
                listener.onResponse(results);
            }
        }, e -> {
            log.error("Failed to execute memory operations", e);
            listener.onFailure(e);
        }));
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
        MLAddMemoriesInput input,
        String indexName,
        String sessionId,
        User user,
        Instant now,
        List<IndexRequest> indexRequests,
        List<MemoryInfo> memoryInfos
    ) {
        for (String fact : facts) {
            MLMemory factMemory = MLMemory
                .builder()
                .sessionId(sessionId)
                .memory(fact)
                .memoryType(MemoryType.FACT)
                .userId(user != null ? user.getName() : null)
                .agentId(input.getAgentId())
                .role("assistant")
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

    private void updateEmbeddingsForOperations(
        List<MemoryResult> results,
        String indexName,
        MemoryStorageConfig storageConfig,
        ActionListener<List<MemoryResult>> listener
    ) {
        List<String> textsToEmbed = new ArrayList<>();
        List<String> memoryIdsToUpdate = new ArrayList<>();

        for (MemoryResult result : results) {
            if ((result.getEvent() == MemoryEvent.ADD || result.getEvent() == MemoryEvent.UPDATE) && result.getMemoryId() != null) {
                textsToEmbed.add(result.getMemory());
                memoryIdsToUpdate.add(result.getMemoryId());
            }
        }

        if (!textsToEmbed.isEmpty()) {
            memoryEmbeddingHelper.generateEmbeddingsForMultipleTexts(textsToEmbed, storageConfig, ActionListener.wrap(embeddings -> {
                List<UpdateRequest> embeddingUpdates = new ArrayList<>();
                for (int i = 0; i < memoryIdsToUpdate.size() && i < embeddings.size(); i++) {
                    Map<String, Object> embeddingUpdate = new HashMap<>();
                    embeddingUpdate.put(MEMORY_EMBEDDING_FIELD, embeddings.get(i));

                    UpdateRequest updateRequest = new UpdateRequest(indexName, memoryIdsToUpdate.get(i)).doc(embeddingUpdate);
                    embeddingUpdates.add(updateRequest);
                }

                if (!embeddingUpdates.isEmpty()) {
                    BulkRequest embeddingBulk = new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    for (UpdateRequest request : embeddingUpdates) {
                        embeddingBulk.add(request);
                    }

                    client.bulk(embeddingBulk, ActionListener.wrap(embeddingResponse -> {
                        if (embeddingResponse.hasFailures()) {
                            log.error("Failed to update embeddings: {}", embeddingResponse.buildFailureMessage());
                        }
                        listener.onResponse(results);
                    }, e -> {
                        log.error("Failed to update embeddings", e);
                        listener.onResponse(results);
                    }));
                } else {
                    listener.onResponse(results);
                }
            }, e -> {
                log.error("Failed to generate embeddings for memory operations", e);
                listener.onResponse(results);
            }));
        } else {
            listener.onResponse(results);
        }
    }
}
