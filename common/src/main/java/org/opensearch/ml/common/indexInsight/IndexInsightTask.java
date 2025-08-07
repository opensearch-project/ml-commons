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
    default void execute(ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, docId);
        
        getClient().get(getRequest, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse, listener);
            } else {
                createGeneratingDoc(docId, listener);
            }
        }, e -> {
            createGeneratingDoc(docId, listener);
        }));
    }
    
    default void handleExistingDoc(GetResponse getResponse, ActionListener<IndexInsight> listener) {
        Map<String, Object> source = getResponse.getSourceAsMap();
        String currentStatus = (String) source.get("status");
        Long lastUpdateTime = (Long) source.get("last_updated_time");
        long currentTime = Instant.now().toEpochMilli();
        
        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);
        switch (status) {
            case GENERATING:
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > GENERATING_TIMEOUT) {
                    setGeneratingAndRun(listener);
                } else {
                    // If still generating and not timeout, task is already running
                    listener.onFailure(new Exception("Task is already running"));
                }
                break;
            case COMPLETED:
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                    setGeneratingAndRun(listener);
                } else {
                    // Return existing result
                    IndexInsight insight = IndexInsight.builder()
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
                setGeneratingAndRun(listener);
                break;
        }
    }
    
    default void createGeneratingDoc(String docId, ActionListener<IndexInsight> listener) {
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
            checkPrerequisitesAndRun(listener);
        }, e -> {
            setStatus(IndexInsightTaskStatus.FAILED);
            listener.onFailure(e);
        }));
    }
    
    default void setGeneratingAndRun(ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("status", IndexInsightTaskStatus.GENERATING.toString());
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(
            r -> checkPrerequisitesAndRun(listener),
            e -> {
                setStatus(IndexInsightTaskStatus.FAILED);
                listener.onFailure(e);
            }
        ));
    }
    
    default void checkPrerequisitesAndRun(ActionListener<IndexInsight> listener) {
        List<MLIndexInsightType> prerequisites = getPrerequisites();
        if (prerequisites.isEmpty()) {
            runTaskLogic(listener);
            return;
        }
        
        // Check prerequisites synchronously for single node scenario
        try {
            for (MLIndexInsightType prerequisite : prerequisites) {
                String prereqDocId = generateDocId(getTargetIndex(), prerequisite);
                GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, prereqDocId);
                
                GetResponse response = getClient().get(getRequest).actionGet();
                if (!response.isExists()) {
                    saveFailedStatus();
                    listener.onFailure(new Exception("Prerequisite not found: " + prerequisite));
                    return;
                }
                
                Map<String, Object> source = response.getSourceAsMap();
                String prereqStatus = (String) source.get("status");
                
                if (IndexInsightTaskStatus.FAILED.toString().equals(prereqStatus)) {
                    saveFailedStatus();
                    listener.onFailure(new Exception("Prerequisite failed: " + prerequisite));
                    return;
                }
                
                if (IndexInsightTaskStatus.GENERATING.toString().equals(prereqStatus)) {
                    saveFailedStatus();
                    listener.onFailure(new Exception("Prerequisite still generating: " + prerequisite));
                    return;
                }
            }
            
            // All prerequisites satisfied, run task
            runTaskLogic(listener);
        } catch (Exception e) {
            saveFailedStatus();
            listener.onFailure(e);
        }
    }
    
    default void saveResult(String content, ActionListener<IndexInsight> listener) {
        String docId = generateDocId();
        long currentTime = Instant.now().toEpochMilli();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("content", content);
        docMap.put("status", IndexInsightTaskStatus.COMPLETED.toString());
        docMap.put("last_updated_time", currentTime);
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus(IndexInsightTaskStatus.COMPLETED);
            // Construct IndexInsight object directly instead of querying again
            IndexInsight insight = IndexInsight.builder()
                .index(getTargetIndex())
                .taskType(getTaskType())
                .content(content)
                .status(IndexInsightTaskStatus.COMPLETED)
                .lastUpdatedTime(Instant.ofEpochMilli(currentTime))
                .build();
            listener.onResponse(insight);
        }, e -> {
            saveFailedStatus();
            listener.onFailure(e);
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
    void runTaskLogic(ActionListener<IndexInsight> listener);
}