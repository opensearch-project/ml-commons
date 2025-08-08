package org.opensearch.ml.common.indexInsight;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.transport.client.Client;

import com.google.common.hash.Hashing;

/**
 * Interface representing an index insight execution task
 */
public interface IndexInsightTask {

    long GENERATING_TIMEOUT = 3 * 60 * 1000; // 3 minutes - temporary setting
    long UPDATE_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours - temporary setting

    /**
     * Execute the index insight task following the new design:
     * 1. Check if record exists in storage
     * 2. Check status and last updated time
     * 3. Check prerequisites
     * 4. Run task logic
     * 5. Write back to storage
     */
    default void execute(String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        GetRequest getRequest = new GetRequest(targetIndex, docId);

        getClient().get(getRequest, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse, targetIndex, tenantId, listener);
            } else {
                createGeneratingDoc(docId, targetIndex, tenantId, listener);
            }
        }, e -> { createGeneratingDoc(docId, targetIndex, tenantId, listener); }));
    }

    default void handleExistingDoc(GetResponse getResponse, String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        Map<String, Object> source = getResponse.getSourceAsMap();
        String currentStatus = (String) source.get("status");
        Long lastUpdateTime = (Long) source.get("last_updated_time");
        long currentTime = Instant.now().toEpochMilli();

        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);
        switch (status) {
            case GENERATING:
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > GENERATING_TIMEOUT) {
                    setGeneratingAndRun(targetIndex, tenantId, listener);
                } else {
                    // If still generating and not timeout, task is already running
                    listener
                        .onFailure(new OpenSearchStatusException("Index insight is being generated, please wait...", RestStatus.ACCEPTED));
                }
                break;
            case COMPLETED:
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                    setGeneratingAndRun(targetIndex, tenantId, listener);
                } else {
                    // Return existing result
                    IndexInsight insight = IndexInsight
                        .builder()
                        .index((String) source.get("index_name"))
                        .taskType(MLIndexInsightType.valueOf((String) source.get("task_type")))
                        .content((String) source.get("content"))
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
                        .build();
                    listener.onResponse(insight);
                }
                break;
            case FAILED:
                // Retry failed task
                setGeneratingAndRun(targetIndex, tenantId, listener);
                break;
        }
    }

    default void createGeneratingDoc(String docId, String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", IndexInsightTaskStatus.GENERATING.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());

        UpdateRequest updateRequest = new UpdateRequest(targetIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.GENERATING);
            checkPrerequisitesAndRun(targetIndex, tenantId, listener);
        }, e -> {
            setStatus(IndexInsightTaskStatus.FAILED);
            listener.onFailure(e);
        }));
    }

    default void setGeneratingAndRun(String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("status", IndexInsightTaskStatus.GENERATING.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());

        UpdateRequest updateRequest = new UpdateRequest(targetIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> checkPrerequisitesAndRun(targetIndex, tenantId, listener), e -> {
            setStatus(IndexInsightTaskStatus.FAILED);
            listener.onFailure(e);
        }));
    }

    default void checkPrerequisitesAndRun(String targetIndex, String tenantId, ActionListener<IndexInsight> listener) {
        List<MLIndexInsightType> prerequisites = getPrerequisites();
        if (prerequisites.isEmpty()) {
            runTaskLogic(targetIndex, tenantId, listener);
            return;
        }

        checkAndRunPrerequisites(prerequisites, 0, targetIndex, tenantId, listener);
    }

    default void checkAndRunPrerequisites(
        List<MLIndexInsightType> prerequisites,
        int index,
        String storageIndex,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {
        if (index >= prerequisites.size()) {
            // All prerequisites satisfied, run current task
            runTaskLogic(storageIndex, tenantId, listener);
            return;
        }

        MLIndexInsightType prerequisite = prerequisites.get(index);
        String prereqDocId = generateDocId(getTargetIndex(), prerequisite);
        GetRequest getRequest = new GetRequest(storageIndex, prereqDocId);

        getClient().get(getRequest, ActionListener.wrap(response -> {
            if (!response.isExists()) {
                // Prerequisite not found - run it
                runPrerequisite(
                    prerequisite,
                    storageIndex,
                    tenantId,
                    ActionListener
                        .wrap(prereqInsight -> checkAndRunPrerequisites(prerequisites, index + 1, storageIndex, tenantId, listener), e -> {
                            saveFailedStatus(storageIndex);
                            listener.onFailure(new Exception("Failed to run prerequisite: " + prerequisite, e));
                        })
                );
                return;
            }

            Map<String, Object> source = response.getSourceAsMap();
            String prereqStatus = (String) source.get("status");

            if (IndexInsightTaskStatus.FAILED.toString().equals(prereqStatus)
                || IndexInsightTaskStatus.GENERATING.toString().equals(prereqStatus)) {
                saveFailedStatus(storageIndex);
                listener.onFailure(new Exception("Prerequisite failed or still generating: " + prerequisite));
                return;
            }

            if (IndexInsightTaskStatus.COMPLETED.toString().equals(prereqStatus)) {
                // Check if prerequisite is expired
                Long prereqLastUpdateTime = (Long) source.get("last_updated_time");
                long currentTime = Instant.now().toEpochMilli();

                if (prereqLastUpdateTime != null && (currentTime - prereqLastUpdateTime) > UPDATE_INTERVAL) {
                    // Prerequisite expired - run it again
                    runPrerequisite(
                        prerequisite,
                        storageIndex,
                        tenantId,
                        ActionListener
                            .wrap(
                                prereqInsight -> checkAndRunPrerequisites(prerequisites, index + 1, storageIndex, tenantId, listener),
                                e -> {
                                    saveFailedStatus(storageIndex);
                                    listener.onFailure(new Exception("Failed to run expired prerequisite: " + prerequisite, e));
                                }
                            )
                    );
                } else {
                    // This prerequisite is satisfied, check next one
                    checkAndRunPrerequisites(prerequisites, index + 1, storageIndex, tenantId, listener);
                }
            } else {
                saveFailedStatus(storageIndex);
                listener.onFailure(new Exception("Unknown prerequisite status: " + prereqStatus));
            }
        }, e -> {
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }));
    }

    default void runPrerequisite(
        MLIndexInsightType prerequisiteType,
        String targetIndex,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {
        IndexInsightTask prerequisiteTask = createPrerequisiteTask(prerequisiteType);
        prerequisiteTask.execute(targetIndex, tenantId, listener);
    }

    default void saveResult(String content, String storageIndex, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        long currentTime = Instant.now().toEpochMilli();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("content", content);
        docMap.put("status", IndexInsightTaskStatus.COMPLETED.toString());
        docMap.put("last_updated_time", currentTime);

        UpdateRequest updateRequest = new UpdateRequest(storageIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.COMPLETED);
            // Construct IndexInsight object directly instead of querying again
            IndexInsight insight = IndexInsight
                .builder()
                .index(getTargetIndex())
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

    default void saveFailedStatus(String targetIndex) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", IndexInsightTaskStatus.FAILED.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());

        UpdateRequest updateRequest = new UpdateRequest(targetIndex, docId).doc(docMap, MediaTypeRegistry.JSON).docAsUpsert(true);

        getClient().update(updateRequest, ActionListener.wrap(r -> { setStatus(IndexInsightTaskStatus.FAILED); }, e -> {
            // Log error but don't propagate further
        }));
    }

    default String generateDocId() {
        return generateDocId(getTargetIndex(), getTaskType());
    }

    default String generateDocId(String indexName, MLIndexInsightType taskType) {
        String combined = indexName + "_" + taskType.toString();
        return Hashing.sha256().hashString(combined, StandardCharsets.UTF_8).toString();
    }

    /**
     * Get the task type
     * @return the MLIndexInsightType
     */
    MLIndexInsightType getTaskType();

    /**
     * Get the index name
     * @return the index name
     */
    String getTargetIndex();

    /**
     * Get the current task status
     * @return the status enum
     */
    IndexInsightTaskStatus getStatus();

    /**
     * Set the current task status
     * @param status the status enum
     */
    void setStatus(IndexInsightTaskStatus status);

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
    void runTaskLogic(String targetIndex, String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Create prerequisite task instance (to be implemented by each task)
     */
    IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType);
}
