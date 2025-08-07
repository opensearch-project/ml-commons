package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_MODEL_ID;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.transport.client.Client;

import com.google.common.hash.Hashing;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FieldDescriptionTask implements IndexInsightTask {
    
    private static final int BATCH_SIZE = 50; // Hard-coded value for now
    private final MLIndexInsightType taskType = MLIndexInsightType.FIELD_DESCRIPTION;
    private final String indexName;
    private final MappingMetadata mappingMetadata;
    private final Client client;
    private final ClusterService clusterService;
    private IndexInsightTaskStatus status = IndexInsightTaskStatus.GENERATING;
    private Map<String, Object> fieldDescriptions;
    
    public FieldDescriptionTask(String indexName, MappingMetadata mappingMetadata, Client client, ClusterService clusterService) {
        this.indexName = indexName;
        this.mappingMetadata = mappingMetadata;
        this.client = client;
        this.clusterService = clusterService;
    }
    
    @Override
    public void runTaskLogic(ActionListener<IndexInsight> listener) {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            String statisticalContent = getInsightContent(MLIndexInsightType.STATISTICAL_DATA);
            
            String modelId = clusterService.getClusterSettings().get(ML_COMMONS_INDEX_INSIGHT_MODEL_ID);
            if (modelId == null || modelId.trim().isEmpty()) {
                log.error("No model ID configured for index insight");
                saveFailedStatus();
                listener.onFailure(new Exception("No model ID configured"));
                return;
            }
            
            batchProcessFields(statisticalContent, modelId, listener);
        } catch (Exception e) {
            log.error("Failed to execute field description task for index {}", indexName, e);
            saveFailedStatus();
            listener.onFailure(e);
        }
    }
    
    @Override
    public MLIndexInsightType getTaskType() {
        return taskType;
    }
    
    @Override
    public String getTargetIndex() {
        return indexName;
    }
    
    @Override
    public IndexInsightTaskStatus getStatus() {
        return status;
    }
    
    @Override
    public void setStatus(IndexInsightTaskStatus status) {
        this.status = status;
    }
    
    @Override
    public Client getClient() {
        return client;
    }
    
    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.singletonList(MLIndexInsightType.STATISTICAL_DATA);
    }
    
    public Map<String, Object> getFieldDescriptions() {
        return fieldDescriptions;
    }
    
    private String getInsightContent(MLIndexInsightType taskType) {
        String docId = generateDocId(indexName, taskType);
        GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, docId);
        
        try {
            GetResponse response = client.get(getRequest).actionGet();
            if (response.isExists()) {
                return response.getSourceAsMap().get("content").toString();
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to get insight content for {} task of index {}", taskType, indexName, e);
            return "";
        }
    }
    

    
    private void extractFieldsInfo(Map<String, Object> properties, String prefix, StringJoiner joiner) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Map<String, Object> fieldProperties = (Map<String, Object>) entry.getValue();

            String type = fieldProperties.containsKey("type") ? fieldProperties.get("type").toString() : "object";
            joiner.add("- " + fieldName + ": " + type);

            if (fieldProperties.containsKey("properties")) {
                extractFieldsInfo((Map<String, Object>) fieldProperties.get("properties"), fieldName, joiner);
            }
        }
    }
    
    private void batchProcessFields(String statisticalContent, String modelId, ActionListener<IndexInsight> listener) {
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        if (mappingSource == null) {
            log.error("No mapping properties found for index: {}", indexName);
            saveFailedStatus();
            return;
        }

        List<String> allFields = new ArrayList<>();
        extractAllFieldNames(mappingSource, "", allFields);
        
        if (allFields.isEmpty()) {
            log.warn("No fields found for index: {}", indexName);
            fieldDescriptions = Collections.emptyMap();
            saveResult("", ActionListener.wrap(
                insight -> {
                    log.info("Empty field description completed for: {}", indexName);
                    listener.onResponse(insight);
                },
                e -> {
                    log.error("Failed to save empty field description result for index {}", indexName, e);
                    saveFailedStatus();
                    listener.onFailure(e);
                }
            ));
            return;
        }

        List<List<String>> batches = createBatches(allFields, BATCH_SIZE);
        CountDownLatch countDownLatch = new CountDownLatch(batches.size());
        ConcurrentHashMap<String, Object> resultsMap = new ConcurrentHashMap<>();
        AtomicBoolean hasErrors = new AtomicBoolean(false);
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        ActionListener<Map<String, Object>> batchListener = ActionListener.wrap(batchResult -> {
            if (batchResult != null) {
                resultsMap.putAll(batchResult);
            }
            countDownLatch.countDown();
            if (countDownLatch.getCount() == 0 && isCompleted.compareAndSet(false, true)) {
                // All-or-nothing strategy: only save results if ALL batches succeed
                // If any batch fails, the entire task is marked as failed and no partial results are saved in ML_INDEX_INSIGHT_INDEX
                if (!hasErrors.get()) {
                    fieldDescriptions = resultsMap;
                    saveResult(resultsMap.toString(), ActionListener.wrap(
                        insight -> {
                            log.info("Field description completed for: {}", indexName);
                            listener.onResponse(insight);
                        },
                        e -> {
                            log.error("Failed to save field description result for index {}", indexName, e);
                            saveFailedStatus();
                            listener.onFailure(e);
                        }
                    ));
                } else {
                    saveFailedStatus();
                    listener.onFailure(new Exception("Batch processing failed"));
                }
            }
        }, e -> {
            countDownLatch.countDown();
            hasErrors.set(true);
            log.error("Batch processing failed for index {}: {}", indexName, e.getMessage());
            if (countDownLatch.getCount() == 0 && isCompleted.compareAndSet(false, true)) {
                saveFailedStatus();
                listener.onFailure(new Exception("Batch processing failed"));
            }
        });

        for (List<String> batch : batches) {
            processBatch(batch, statisticalContent, modelId, batchListener);
        }
    }

    private void extractAllFieldNames(Map<String, Object> properties, String prefix, List<String> fieldNames) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Map<String, Object> fieldProperties = (Map<String, Object>) entry.getValue();

            if (fieldProperties.containsKey("type")) {
                fieldNames.add(fieldName);
            }

            if (fieldProperties.containsKey("properties")) {
                extractAllFieldNames((Map<String, Object>) fieldProperties.get("properties"), fieldName, fieldNames);
            }
        }
    }

    private List<List<String>> createBatches(List<String> fields, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < fields.size(); i += batchSize) {
            int end = Math.min(i + batchSize, fields.size());
            batches.add(fields.subList(i, end));
        }
        return batches;
    }

    private void processBatch(List<String> batchFields, String statisticalContent, String modelId, ActionListener<Map<String, Object>> listener) {
        String prompt = generateBatchPrompt(batchFields, statisticalContent);
        
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Collections.singletonMap("prompt", prompt))
            .build();

        MLPredictionTaskRequest request = new MLPredictionTaskRequest(
            modelId,
            MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(),
            null,
            null
        );

        client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(mlTaskResponse -> {
            log.info("Batch LLM call successful for {} fields in index {}", batchFields.size(), indexName);
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
            ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            Map<String, Object> dataAsMap = (Map<String, Object>) modelTensor.getDataAsMap();

            String response = extractModelResponse(dataAsMap);
            Map<String, Object> batchResult = parseFieldDescription(response);
            listener.onResponse(batchResult);
        }, e -> {
            log.error("Failed to call LLM for batch in index {}: {}", indexName, e.getMessage());
            listener.onFailure(e);
        }));
    }

    private String generateBatchPrompt(List<String> batchFields, String statisticalContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index fields and provide descriptions:\\n\\n");
        prompt.append("Index Name: ").append(indexName).append("\\n\\n");
        
        prompt.append("Fields to describe:\\n");
        for (String field : batchFields) {
            prompt.append("- ").append(field).append("\\n");
        }
        prompt.append("\\n");
        // Filter statistical data based on current batch fields
        String relevantStatisticalData = extractRelevantStatisticalData(statisticalContent, batchFields);
        if (relevantStatisticalData != null && !relevantStatisticalData.isEmpty()) {
            prompt.append("Statistical Data:\\n");
            prompt.append(relevantStatisticalData).append("\\n\\n");
        }

        prompt.append("For each field listed above, provide a brief description of what it contains and its purpose.\\n");
        prompt.append("Format as: field_name: description");

        return prompt.toString();
    }
    
    private String extractRelevantStatisticalData(String statisticalContent, List<String> batchFields) {
        if (statisticalContent == null || statisticalContent.isEmpty() || batchFields.isEmpty()) {
            return "";
        }
        
        try {
            // Extract sample document from statistical content (format: line1=count, line2=Sample document: {json})
            String[] lines = statisticalContent.split("\\n");
            if (lines.length < 2 || !lines[1].startsWith("Sample document: ")) {
                return "";
            }
            
            String sampleDocJson = lines[1].substring("Sample document: ".length());
            // Parse JSON and extract only relevant fields
            Map<String, Object> sampleDoc = JsonPath.read(sampleDocJson, "$");
            Map<String, Object> relevantData = new HashMap<>();
            
            for (String field : batchFields) {
                try {
                    Object value = JsonPath.read(sampleDoc, "$." + field);
                    relevantData.put(field, value);
                } catch (Exception e) {
                    // Field not found in sample document, skip
                }
            }
            
            if (relevantData.isEmpty()) {
                return "";
            }
            
            StringBuilder result = new StringBuilder();
            result.append("Sample data for relevant fields:\\n");
            for (Map.Entry<String, Object> entry : relevantData.entrySet()) {
                result.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.warn("Failed to extract relevant statistical data for batch fields: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Auto-detects LLM response format and extracts the response text.
     */
    private String extractModelResponse(Map<String, Object> dataAsMap) {
        // Try OpenAI format
        if (dataAsMap.containsKey("choices")) {
            return JsonPath.read(dataAsMap, "$.choices[0].message.content");
        }

        // Try Bedrock Claude format
        if (dataAsMap.containsKey("output")) {
            return JsonPath.read(dataAsMap, "$.output.message.content[0].text");
        }

        // Fallback to generic response field
        return JsonPath.read(dataAsMap, "$.response");
    }
    
    private Map<String, Object> parseFieldDescription(String modelResponse) {
        Map<String, Object> field2Desc = new HashMap<>();
        String[] lines = modelResponse.trim().split("\\n");

        for (String line : lines) {
            line = line.trim();
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String desc = parts[1].trim();
                field2Desc.put(name, desc);
            }
        }

        return field2Desc;
    }
}