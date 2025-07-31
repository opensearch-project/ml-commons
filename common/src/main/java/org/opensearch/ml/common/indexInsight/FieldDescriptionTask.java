package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_MODEL_ID;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
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

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FieldDescriptionTask implements IndexInsightTask {
    
    private final String targetIndex;
    private final MappingMetadata mappingMetadata;
    private final Client client;
    private final ClusterService clusterService;
    private String status = "pending";
    private Map<String, Object> fieldDescriptions;
    private SearchHit[] sampleDocuments;
    
    public FieldDescriptionTask(String targetIndex, MappingMetadata mappingMetadata, Client client, ClusterService clusterService) {
        this.targetIndex = targetIndex;
        this.mappingMetadata = mappingMetadata;
        this.client = client;
        this.clusterService = clusterService;
    }
    
    @Override
    public void runTaskLogic() {
        status = "generating";
        try {
            String statisticalContent = getInsightContent(MLIndexInsightType.STATISTICAL_DATA);
            
            String modelId = clusterService.getClusterSettings().get(ML_COMMONS_INDEX_INSIGHT_MODEL_ID);
            if (modelId == null || modelId.trim().isEmpty()) {
                log.error("No model ID configured for index insight");
                saveFailedStatus();
                return;
            }
            
            String prompt = generateFieldDescriptionPrompt(statisticalContent);
            callLLM(prompt, modelId);
        } catch (Exception e) {
            log.error("Failed to execute field description task for index {}", targetIndex, e);
            saveFailedStatus();
        }
    }
    
    @Override
    public MLIndexInsightType getTaskType() {
        return MLIndexInsightType.FIELD_DESCRIPTION;
    }
    
    @Override
    public String getTargetIndex() {
        return targetIndex;
    }
    
    @Override
    public String getStatus() {
        return status;
    }
    
    @Override
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public Client getClient() {
        return client;
    }
    
    @Override
    public List<MLIndexInsightType> getDependencies() {
        return Collections.singletonList(MLIndexInsightType.STATISTICAL_DATA);
    }
    
    public Map<String, Object> getFieldDescriptions() {
        return fieldDescriptions;
    }
    
    public void setSampleDocuments(SearchHit[] sampleDocuments) {
        this.sampleDocuments = sampleDocuments;
    }
    
    private String getInsightContent(MLIndexInsightType taskType) {
        String docId = generateDocId(targetIndex, taskType);
        GetRequest getRequest = new GetRequest(ML_INDEX_INSIGHT_INDEX, docId);
        
        try {
            GetResponse response = client.get(getRequest).actionGet();
            if (response.isExists()) {
                return response.getSourceAsMap().get("content").toString();
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to get insight content for {} task of index {}", taskType, targetIndex, e);
            return "";
        }
    }
    
    private String generateFieldDescriptionPrompt(String statisticalContent) {
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        if (mappingSource == null) {
            return "No mapping properties found for index: " + targetIndex;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index structure and provide insights:\\n\\n");
        prompt.append("Index Name: ").append(targetIndex).append("\\n\\n");
        prompt.append("Index Mapping:\\n");

        StringJoiner mappingInfo = new StringJoiner("\\n");
        extractFieldsInfo(mappingSource, "", mappingInfo);
        prompt.append(mappingInfo.toString()).append("\\n\\n");

        if (statisticalContent != null && !statisticalContent.isEmpty()) {
            prompt.append("Statistical Data:\\n");
            prompt.append(statisticalContent).append("\\n\\n");
        }

        prompt.append("Please provide:\\n");
        prompt.append("<column_summarization>For each field, provide a brief description of what it contains and its purpose\\n");
        prompt.append("Format as: field_name: description</column_summarization>");

        return prompt.toString();
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
    
    private void callLLM(String prompt, String modelId) {
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
            log.info("LLM call successful for field description: {}", targetIndex);
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
            ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            Map<String, Object> dataAsMap = (Map<String, Object>) modelTensor.getDataAsMap();

            String response = extractModelResponse(dataAsMap);
            fieldDescriptions = parseFieldDescription(response);
            saveResult(response);
            log.info("Field description completed for: {}", targetIndex);
        }, e -> {
            log.error("Failed to call LLM for field description: {}", targetIndex, e);
            saveFailedStatus();
        }));
    }
    
    private String extractModelResponse(Map<String, Object> dataAsMap) {
        if (dataAsMap == null) {
            throw new IllegalStateException("Model inference failed");
        }
        
        Map<String, Object> output = (Map<String, Object>) dataAsMap.get("output");
        if (output == null) {
            return (String) dataAsMap.get("response");
        }
        
        Map<String, Object> message = (Map<String, Object>) output.get("message");
        if (message == null) {
            throw new IllegalStateException("Model inference failed, incorrect message format");
        }
        
        java.util.ArrayList<?> content = (java.util.ArrayList<?>) message.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Model inference failed, content is empty");
        }
        
        Map<String, Object> firstContent = (Map<String, Object>) content.get(0);
        return (String) firstContent.get("text");
    }
    
    private Map<String, Object> parseFieldDescription(String modelResponse) {
        String[] tmpList = modelResponse.split("<column_summarization>");
        if (tmpList.length < 2) {
            return Collections.emptyMap();
        }

        String[] contentParts = tmpList[tmpList.length - 1].split("</column_summarization>");
        if (contentParts.length < 1) {
            return Collections.emptyMap();
        }

        String content = contentParts[0].trim();
        Map<String, Object> field2Desc = new HashMap<>();
        String[] lines = content.split("\\n");

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