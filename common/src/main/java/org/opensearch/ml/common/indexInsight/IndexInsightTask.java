package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.transport.client.Client;

import com.google.common.hash.Hashing;

import lombok.extern.log4j.Log4j2;

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
    default void execute() {
        String docId = generateDocId();
        GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, docId);
        
        getClient().get(getRequest, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse);
            } else {
                createGeneratingDoc(docId);
            }
        }, e -> {
            createGeneratingDoc(docId);
        }));
    }
    
    default void handleExistingDoc(GetResponse getResponse) {
        Map<String, Object> source = getResponse.getSourceAsMap();
        String currentStatus = (String) source.get("status");
        Long lastUpdateTime = (Long) source.get("last_updated_time");
        long currentTime = Instant.now().toEpochMilli();
        
        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);
        switch (status) {
            case GENERATING:
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > GENERATING_TIMEOUT) {
                    setGeneratingAndRun();
                }
                // If still generating and not timeout, do nothing
                break;
            case COMPLETED:
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                    setGeneratingAndRun();
                }
                // If completed and no update needed, do nothing
                break;
            case FAILED:
                // Retry failed task
                setGeneratingAndRun();
                break;
        }
    }
    
    default void createGeneratingDoc(String docId) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", IndexInsightTaskStatus.GENERATING.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.GENERATING);
            checkPrerequisitesAndRun();
        }, e -> {
            setStatus(IndexInsightTaskStatus.FAILED);
        }));
    }
    
    default void setGeneratingAndRun() {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("status", IndexInsightTaskStatus.GENERATING.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON);
        
        getClient().update(updateRequest, ActionListener.wrap(
            r -> checkPrerequisitesAndRun(),
            e -> setStatus(IndexInsightTaskStatus.FAILED)
        ));
    }
    
    default void checkPrerequisitesAndRun() {
        List<MLIndexInsightType> prerequisites = getPrerequisites();
        if (prerequisites.isEmpty()) {
            updateLastUpdatedTime();
            runTaskLogic();
            return;
        }
        
        // Check all prerequisites
        for (MLIndexInsightType prerequisite : prerequisites) {
            String prereqDocId = generateDocId(getTargetIndex(), prerequisite);
            GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, prereqDocId);
            
            try {
                GetResponse response = getClient().get(getRequest).actionGet();
                if (!response.isExists()) {
                    // Prerequisite not found, set failed status
                    saveFailedStatus();
                    return;
                }
                
                Map<String, Object> source = response.getSourceAsMap();
                String prereqStatus = (String) source.get("status");
                Long prereqLastUpdateTime = (Long) source.get("last_updated_time");
                long currentTime = Instant.now().toEpochMilli();
                
                if (IndexInsightTaskStatus.GENERATING.toString().equals(prereqStatus)) {
                    // If prerequisite is generating, wait for remaining time
                    if (prereqLastUpdateTime != null) {
                        long elapsedTime = currentTime - prereqLastUpdateTime;
                        long remainingTime = GENERATING_TIMEOUT - elapsedTime;
                        
                        if (remainingTime > 0) {
                            try {
                                Thread.sleep(remainingTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                saveFailedStatus();
                                return;
                            }
                        }
                        // Re-check status after waiting
                        GetResponse updatedResponse = getClient().get(getRequest).actionGet();
                        if (updatedResponse.isExists()) {
                            Map<String, Object> updatedSource = updatedResponse.getSourceAsMap();
                            String updatedStatus = (String) updatedSource.get("status");
                            if (!IndexInsightTaskStatus.COMPLETED.toString().equals(updatedStatus)) {
                                saveFailedStatus();
                                return;
                            }
                        } else {
                            saveFailedStatus();
                            return;
                        }
                    } else {
                        saveFailedStatus();
                        return;
                    }
                } else if (IndexInsightTaskStatus.FAILED.toString().equals(prereqStatus)) {
                    // Prerequisite failed, set failed status
                    saveFailedStatus();
                    return;
                }
            } catch (Exception e) {
                // Error checking prerequisite, set failed status
                saveFailedStatus();
                return;
            }
        }
        
        // All prerequisites satisfied, run task
        updateLastUpdatedTime();
        runTaskLogic();
    }
    
    default void saveResult(String content) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("content", content);
        docMap.put("status", IndexInsightTaskStatus.COMPLETED.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.COMPLETED);
        }, e -> {
            saveFailedStatus();
        }));
    }
    
    default void saveFailedStatus() {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", IndexInsightTaskStatus.FAILED.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.FAILED);
        }, e -> {
            setStatus(IndexInsightTaskStatus.FAILED);
        }));
    }
    
    default String generateDocId() {
        return generateDocId(getTargetIndex(), getTaskType());
    }
    
    default void updateLastUpdatedTime() {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {}, e -> {}));
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
    void runTaskLogic();
}