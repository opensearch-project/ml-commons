/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_GENERATING_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_UPDATE_INTERVAL;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.transport.client.Client;

/**
 * Interface representing an index insight execution task
 */
public interface IndexInsightTask {

    /**
     * Execute the index insight task following the new design:
     * 1. Check if record exists in storage
     * 2. Check status and last updated time
     * 3. Check prerequisites
     * 4. Run task logic
     * 5. Write back to storage
     */
    default void execute(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        GetRequest getRequest = new GetRequest(storageIndex, docId);

        getClient().get(getRequest, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse, storageIndex, tenantId, listener);
            } else {
                beginGeneration(storageIndex, tenantId, listener);
            }
        }, e -> { listener.onFailure(new Exception("Failed to check existing document", e)); }));
    }

    default void handleExistingDoc(GetResponse getResponse, String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        Map<String, Object> source = getResponse.getSourceAsMap();
        String currentStatus = (String) source.get(IndexInsight.STATUS_FIELD);
        Long lastUpdateTime = (Long) source.get(IndexInsight.LAST_UPDATE_FIELD);
        long currentTime = Instant.now().toEpochMilli();

        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);
        switch (status) {
            case GENERATING:
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > INDEX_INSIGHT_GENERATING_TIMEOUT) {
                    beginGeneration(storageIndex, tenantId, listener);
                } else {
                    // If still generating and not timeout, task is already running
                    listener
                        .onFailure(
                            new OpenSearchStatusException("Index insight is being generated, please wait...", RestStatus.TOO_MANY_REQUESTS)
                        );
                }
                break;
            case COMPLETED:
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > INDEX_INSIGHT_UPDATE_INTERVAL) {
                    beginGeneration(storageIndex, tenantId, listener);
                } else {
                    // Return existing result
                    IndexInsight insight = IndexInsight
                        .builder()
                        .index((String) source.get(IndexInsight.INDEX_NAME_FIELD))
                        .taskType(MLIndexInsightType.valueOf((String) source.get(IndexInsight.TASK_TYPE_FIELD)))
                        .content((String) source.get(IndexInsight.CONTENT_FIELD))
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
                        .build();
                    listener.onResponse(insight);
                }
                break;
            case FAILED:
                // Retry failed task
                beginGeneration(storageIndex, tenantId, listener);
                break;
        }
    }

    /**
     * Begin the index insight generation process by updating task status to GENERATING and executing the task with prerequisites.
     */
    default void beginGeneration(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put(IndexInsight.INDEX_NAME_FIELD, getSourceIndex());
        docMap.put(IndexInsight.TASK_TYPE_FIELD, getTaskType().toString());
        docMap.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.GENERATING.toString());
        docMap.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli());

        UpdateRequest updateRequest = new UpdateRequest(storageIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> { runWithPrerequisites(storageIndex, tenantId, listener); }, e -> {
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }));
    }

    default void runWithPrerequisites(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        List<MLIndexInsightType> prerequisites = getPrerequisites();
        AtomicInteger completedCount = new AtomicInteger(0);
        if (prerequisites.isEmpty()) {
            runTask(storageIndex, tenantId, listener);
            return;
        }

        // Run all prerequisites
        for (MLIndexInsightType prerequisite : prerequisites) {
            IndexInsightTask prerequisiteTask = createPrerequisiteTask(prerequisite);
            prerequisiteTask.execute(storageIndex, tenantId, ActionListener.wrap(prereqInsight -> {
                if (completedCount.incrementAndGet() == prerequisites.size()) {
                    runTask(storageIndex, tenantId, listener);
                }
            }, e -> {
                saveFailedStatus(storageIndex);
                listener.onFailure(new Exception("Failed to run prerequisite: " + prerequisite, e));
            }));
        }
    }

    default void saveResult(String content, String storageIndex, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        long currentTime = Instant.now().toEpochMilli();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put(IndexInsight.INDEX_NAME_FIELD, getSourceIndex());
        docMap.put(IndexInsight.TASK_TYPE_FIELD, getTaskType().toString());
        docMap.put(IndexInsight.CONTENT_FIELD, content);
        docMap.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.COMPLETED.toString());
        docMap.put(IndexInsight.LAST_UPDATE_FIELD, currentTime);

        UpdateRequest updateRequest = new UpdateRequest(storageIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> {
            // Construct IndexInsight object directly instead of querying again
            IndexInsight insight = IndexInsight
                .builder()
                .index(getSourceIndex())
                .taskType(getTaskType())
                .content(content)
                .status(IndexInsightTaskStatus.COMPLETED)
                .lastUpdatedTime(Instant.ofEpochMilli(currentTime))
                .build();
            listener.onResponse(insight);
        }, e -> {
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }));
    }

    default void saveFailedStatus(String storageIndex) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put(IndexInsight.INDEX_NAME_FIELD, getSourceIndex());
        docMap.put(IndexInsight.TASK_TYPE_FIELD, getTaskType().toString());
        docMap.put(IndexInsight.STATUS_FIELD, IndexInsightTaskStatus.FAILED.toString());
        docMap.put(IndexInsight.LAST_UPDATE_FIELD, Instant.now().toEpochMilli());

        UpdateRequest updateRequest = new UpdateRequest(storageIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient()
            .update(updateRequest, ActionListener.wrap(r -> {/* Status saved to storage */}, e -> {/* Silently ignore storage failure */}));
    }

    default String generateDocId() {
        return IndexInsightUtils.generateDocId(getSourceIndex(), getTaskType());
    }

    /**
     * Get insight content from storage for a specific task type
     */
    default void getInsightContentFromContainer(String storageIndex, MLIndexInsightType taskType, ActionListener<String> listener) {
        String docId = IndexInsightUtils.generateDocId(getSourceIndex(), taskType);
        GetRequest getRequest = new GetRequest(storageIndex, docId);

        getClient().get(getRequest, ActionListener.wrap(response -> {
            String content = response.isExists() ? response.getSourceAsMap().get(IndexInsight.CONTENT_FIELD).toString() : "";
            listener.onResponse(content);
        }, e -> {
            // Return empty content on failure instead of propagating error
            listener.onResponse("");
        }));
    }

    /**
     * Get the task type
     * @return the MLIndexInsightType
     */
    MLIndexInsightType getTaskType();

    /**
     * Get the source index
     * @return the source index
     */
    String getSourceIndex();

    /**
     * Get the prerequisites of this task
     * @return list of task types that this task depends on
     */
    List<MLIndexInsightType> getPrerequisites();

    /**
     * Get the client instance
     * @return the client
     */
    Client getClient();

    /**
     * Run the specific task logic (to be implemented by each task)
     */
    void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Create prerequisite task instance (to be implemented by each task)
     */
    IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType);
}
