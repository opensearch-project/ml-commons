/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_GENERATING_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_UPDATE_INTERVAL;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_STORAGE_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.buildPatternSourceBuilder;
import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.matchPattern;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.*;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

/**
 * Interface representing an index insight execution task
 */

public interface IndexInsightTask {

    /**
     * Execute the index insight task:
     * 1. Check if record exists in storage
     * 2. Check status and last updated time
     * 3. Check prerequisites
     * 4. Run task logic
     * 5. Write back to storage
     */
    default void execute(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        getIndexInsight(generateDocId(), tenantId, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse.getSourceAsMap(), storageIndex, tenantId, listener);
            } else {
                SearchSourceBuilder patternSourceBuilder = buildPatternSourceBuilder(getTaskType().name());
                getSdkClient()
                    .searchDataObjectAsync(
                        SearchDataObjectRequest
                            .builder()
                            .tenantId(tenantId)
                            .indices(storageIndex)
                            .searchSourceBuilder(patternSourceBuilder)
                            .build()
                    )
                    .whenComplete((r, throwable) -> {
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            listener.onFailure(cause);
                        } else {
                            SearchResponse searchResponse = r.searchResponse();
                            SearchHit[] hits = searchResponse.getHits().getHits();
                            Map<String, Object> mappedPatternSource = matchPattern(hits, getSourceIndex());
                            if (Objects.isNull(mappedPatternSource)) {
                                beginGeneration(storageIndex, tenantId, listener);
                            } else {
                                handlePatternMatchedDoc(mappedPatternSource, storageIndex, tenantId, listener);
                            }
                        }
                    });
            }
        }, listener::onFailure));
    }

    default void handleExistingDoc(
        Map<String, Object> source,
        String storageIndex,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {

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
                        .tenantId(tenantId)
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
     * Handle pattern matched document
     */
    default void handlePatternMatchedDoc(
        Map<String, Object> patternSource,
        String storageIndex,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {
        String currentStatus = (String) patternSource.get(IndexInsight.STATUS_FIELD);
        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);

        if (status != IndexInsightTaskStatus.COMPLETED) {
            // If pattern source is not completed, fall back to normal generation
            beginGeneration(storageIndex, tenantId, listener);
            return;
        }

        // Handle pattern result
        handlePatternResult(patternSource, storageIndex, tenantId, listener);
    }

    /**
     * Begin the index insight generation process by updating task status to GENERATING and executing the task with prerequisites.
     */
    default void beginGeneration(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        IndexInsight indexInsight = IndexInsight
            .builder()
            .index(getSourceIndex())
            .tenantId(tenantId)
            .taskType(getTaskType())
            .status(IndexInsightTaskStatus.GENERATING)
            .lastUpdatedTime(Instant.now())
            .build();

        writeIndexInsight(
            indexInsight,
            tenantId,
            ActionListener.wrap(r -> { runWithPrerequisites(storageIndex, tenantId, listener); }, e -> {
                saveFailedStatus(tenantId, e, listener);
            })
        );
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
            }, e -> { saveFailedStatus(tenantId, new Exception("Failed to run prerequisite: " + prerequisite, e), listener); }));
        }
    }

    default void saveResult(String content, String tenantId, ActionListener<IndexInsight> listener) {
        IndexInsight insight = IndexInsight
            .builder()
            .index(getSourceIndex())
            .taskType(getTaskType())
            .content(content)
            .status(IndexInsightTaskStatus.COMPLETED)
            .lastUpdatedTime(Instant.now())
            .tenantId(tenantId)
            .build();

        writeIndexInsight(insight, tenantId, ActionListener.wrap(r -> { listener.onResponse(insight); }, e -> {
            saveFailedStatus(tenantId, e, listener);
        }));

    }

    default void saveFailedStatus(String tenantId, Exception error, ActionListener<IndexInsight> listener) {
        IndexInsight indexInsight = IndexInsight
            .builder()
            .tenantId(tenantId)
            .index(getSourceIndex())
            .taskType(getTaskType())
            .status(IndexInsightTaskStatus.FAILED)
            .build();
        writeIndexInsight(
            indexInsight,
            tenantId,
            ActionListener.wrap(r -> { listener.onFailure(error); }, e -> { listener.onFailure(e); })
        );
    }

    default String generateDocId() {
        return IndexInsightUtils.generateDocId(getSourceIndex(), getTaskType());
    }

    /**
     * Get insight content from storage for a specific task type
     */
    default void getInsightContentFromContainer(
        MLIndexInsightType taskType,
        String tenantId,
        ActionListener<Map<String, Object>> listener
    ) {
        String docId = IndexInsightUtils.generateDocId(getSourceIndex(), taskType);
        getIndexInsight(docId, tenantId, ActionListener.wrap(getResponse -> {
            try {
                String content = getResponse.isExists() ? getResponse.getSourceAsMap().get(IndexInsight.CONTENT_FIELD).toString() : "";
                Map<String, Object> contentMap = gson.fromJson(content, Map.class);
                listener.onResponse(contentMap);
            } catch (Exception e) {
                // Return empty content on JSON parsing failure
                listener.onResponse(new HashMap<>());
            }
        }, listener::onFailure));

    }

    private void getIndexInsight(String docId, String tenantId, ActionListener<GetResponse> listener) {
        try (ThreadContext.StoredContext context = getClient().threadPool().getThreadContext().stashContext()) {
            getSdkClient()
                .getDataObjectAsync(
                    GetDataObjectRequest.builder().tenantId(tenantId).index(ML_INDEX_INSIGHT_STORAGE_INDEX).id(docId).build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        listener.onFailure(cause);
                    } else {
                        try {
                            GetResponse getResponse = r.getResponse();
                            assert getResponse != null;
                            listener.onResponse(getResponse);
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void writeIndexInsight(IndexInsight indexInsight, String tenantId, ActionListener<Boolean> listener) {
        String docId = generateDocId();
        try (ThreadContext.StoredContext context = getClient().threadPool().getThreadContext().stashContext()) {
            getSdkClient()
                .putDataObjectAsync(
                    PutDataObjectRequest
                        .builder()
                        .tenantId(tenantId)
                        .index(ML_INDEX_INSIGHT_STORAGE_INDEX)
                        .dataObject(indexInsight)
                        .id(docId)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            assert indexResponse != null;
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED
                                || indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                                listener.onResponse(true);
                            } else {
                                listener.onFailure(new RuntimeException("Failed to put generating index insight doc"));
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                }

                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
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

    SdkClient getSdkClient();

    /**
     * Run the specific task logic (to be implemented by each task)
     */
    void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Create prerequisite task instance (to be implemented by each task)
     */
    IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType);

    /**
     * Handle pattern result
     */
    default void handlePatternResult(
        Map<String, Object> patternSource,
        String storageIndex,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {
        // Default implementation: return pattern result as-is
        Long lastUpdateTime = (Long) patternSource.get(IndexInsight.LAST_UPDATE_FIELD);
        IndexInsight insight = IndexInsight
            .builder()
            .index(getSourceIndex())
            .taskType(getTaskType())
            .content((String) patternSource.get(IndexInsight.CONTENT_FIELD))
            .status(IndexInsightTaskStatus.COMPLETED)
            .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
            .tenantId(tenantId)
            .build();
        listener.onResponse(insight);
    }

}
