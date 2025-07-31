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
    
    // Configurable intervals (in milliseconds)
    long GENERATING_TIMEOUT = 30 * 60 * 1000; // 30 minutes
    long UPDATE_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours
    
    /**
     * Execute the index insight task following the new design:
     * 1. Check if record exists in ML_INDEX_INSIGHT_INDEX
     * 2. Check status and timing
     * 3. Check prerequisites from storage
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
        
        switch (currentStatus) {
            case "pending":
                // Pending task, check if should start
                updateStatusToGenerating();
                checkPrerequisitesAndRun();
                break;
            case "generating":
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > GENERATING_TIMEOUT) {
                    checkPrerequisitesAndRun();
                } else {
                    setStatus("generating");
                }
                break;
            case "completed":
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                    updateStatusToGenerating();
                    checkPrerequisitesAndRun();
                } else {
                    setStatus("completed");
                }
                break;
            case "failed":
                // Retry failed task
                updateStatusToGenerating();
                checkPrerequisitesAndRun();
                break;
            default:
                // Unknown status, treat as pending
                updateStatusToGenerating();
                checkPrerequisitesAndRun();
        }
    }
    
    default void createGeneratingDoc(String docId) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", "pending");
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            updateStatusToGenerating();
            checkPrerequisitesAndRun();
        }, e -> {
            setStatus("failed");
        }));
    }
    
    default void updateStatusToGenerating() {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("status", "generating");
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {}, e -> {}));
    }
    
    default void checkPrerequisitesAndRun() {
        List<MLIndexInsightType> dependencies = getDependencies();
        if (dependencies.isEmpty()) {
            runTaskLogic();
            return;
        }
        
        // Check all dependencies
        checkAllDependencies(dependencies);
    }
    
    default void checkAllDependencies(List<MLIndexInsightType> dependencies) {
        for (MLIndexInsightType dependency : dependencies) {
            String depDocId = generateDocId(getTargetIndex(), dependency);
            GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, depDocId);
            
            try {
                GetResponse response = getClient().get(getRequest).actionGet();
                if (!response.isExists()) {
                    // Dependency not found, skip execution
                    return;
                }
                
                Map<String, Object> source = response.getSourceAsMap();
                String depStatus = (String) source.get("status");
                
                if (!"completed".equals(depStatus)) {
                    // Dependency not completed, skip execution
                    return;
                }
            } catch (Exception e) {
                // Error checking dependency, skip execution
                return;
            }
        }
        
        // All dependencies satisfied, run task
        runTaskLogic();
    }
    
    default void saveResult(String content) {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("content", content);
        docMap.put("status", "completed");
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus("completed");
        }, e -> {
            saveFailedStatus();
        }));
    }
    
    default void saveFailedStatus() {
        String docId = generateDocId();
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", getTargetIndex());
        docMap.put("task_type", getTaskType().toString());
        docMap.put("status", "failed");
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true);
        
        getClient().update(updateRequest, ActionListener.wrap(r -> {
            setStatus("failed");
        }, e -> {
            setStatus("failed");
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
     * Get the target index name
     * @return the index name
     */
    String getTargetIndex();
    
    /**
     * Get the current task status
     * @return the status string
     */
    String getStatus();
    
    /**
     * Set the current task status
     * @param status the status string
     */
    void setStatus(String status);
    
    /**
     * Get the dependencies of this task
     * @return list of task types that this task depends on
     */
    List<MLIndexInsightType> getDependencies();
    
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