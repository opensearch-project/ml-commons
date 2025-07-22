/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_MODEL_ID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.Requests;

import com.google.common.hash.Hashing;

import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@Log4j2
public class MLIndexInsightJobProcessor extends MLJobProcessor {

    private static MLIndexInsightJobProcessor INSTANCE;
    private final MLIndicesHandler mlIndicesHandler;

    public static MLIndexInsightJobProcessor getInstance(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        MLIndicesHandler mlIndicesHandler
    ) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (MLIndexInsightJobProcessor.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            INSTANCE = new MLIndexInsightJobProcessor(clusterService, client, threadPool, mlIndicesHandler);
            return INSTANCE;
        }
    }

    private MLIndexInsightJobProcessor(
        ClusterService clusterService,
        Client client,
        ThreadPool threadPool,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(clusterService, client, threadPool);
        this.mlIndicesHandler = mlIndicesHandler;
    }

    @Override
    public void run() {
        // First ensure the index insight index is created
        mlIndicesHandler.initMLIndexInsightIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                log.error("Failed to create ML index insight index");
                return;
            }
            
            log.info("Successfully initialized ML index insight index, now processing indices");
            
            // Get all indices in the cluster
            String[] indices = clusterService.state().metadata().indices().keySet().toArray(new String[0]);
            
            // Filter out system indices (starting with .)
            for (String indexName : indices) {
                if (!indexName.startsWith(".")) {
                    processIndexData(indexName);
                }
            }
        }, e -> {
            log.error("Failed to initialize ML index insight index", e);
        }));
    }
    
    /**
     * Process index data after the insight index has been created
     */
    private void processIndexData(String indexName) {
        log.info("Processing index data: {}", indexName);
        
        // Generate doc ID for this index
        String docId = generateDocId(indexName);
        
        // Check if index already exists
        checkInsightExists(docId, indexName, exists -> {
            if (exists) {
                log.info("Index insight already exists for index: {}, skipping processing", indexName);
                return;
            }
            
            // Get model ID from cluster settings (check both persistent and transient settings)
            String modelId = clusterService.getClusterSettings().get(ML_COMMONS_INDEX_INSIGHT_MODEL_ID);
            if (modelId == null || modelId.trim().isEmpty()) {
                log.error("No model ID configured for index insight, skipping processing for index: {}", indexName);
                return;
            }
                // Continue with processing if document doesn't exist
                // Get mapping for the index
                GetMappingsRequest getMappingsRequest = new GetMappingsRequest().indices(indexName);
                client.admin().indices().getMappings(getMappingsRequest, ActionListener.wrap(getMappingsResponse -> {
                    Map<String, MappingMetadata> mappings = getMappingsResponse.getMappings();
                    if (mappings.isEmpty()) {
                        log.warn("Fail to find index mapping: {}", indexName);
                        return;
                    }
                    
                    String firstIndexName = (String) mappings.keySet().toArray()[0];
                    MappingMetadata mappingMetadata = mappings.get(firstIndexName);
                    
                    // Get 5 sample documents from the index
                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                    searchSourceBuilder.size(5).query(new MatchAllQueryBuilder());
                    SearchRequest searchRequest = new SearchRequest(new String[] { indexName }, searchSourceBuilder);
                    
                    client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                        SearchHit[] searchHits = searchResponse.getHits().getHits();
                        
                        // Generate prompt for the LLM
                        String prompt = generatePrompt(indexName, mappingMetadata, searchHits);
                        
                        // Call LLM to analyze the index
                        callLLM(indexName, prompt, modelId);
                    }, e -> {
                        log.error("Failed to search index: {}", indexName, e);
                    }));
                }, e -> {
                    log.error("Failed to get index mapping: {}", indexName, e);
                }));
        });
    }
    
    /**
     * Check if a document with the given ID already exists in the ML_INDEX_INSIGHT_INDEX
     * 
     * @param docId The document ID to check
     * @param indexName The index name (for logging purposes)
     * @param callback Callback function that receives a boolean indicating if the document exists
     */
    private void checkInsightExists(String docId, String indexName, java.util.function.Consumer<Boolean> callback) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0).query(org.opensearch.index.query.QueryBuilders.idsQuery().addIds(docId));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(ML_INDEX_INSIGHT_INDEX);
        searchRequest.source(searchSourceBuilder);
        
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            long totalHits = searchResponse.getHits().getTotalHits().value();
            boolean exists = totalHits > 0;
            log.debug("Document existence check for index {}, docId {}: exists = {}, totalHits = {}", indexName, docId, exists, totalHits);
            callback.accept(exists);
        }, e -> {
            log.error("Failed to check if document exists for index: {} with docId: {}", indexName, docId, e);
            // Assume document doesn't exist in case of error, to allow processing to continue
            callback.accept(false);
        }));
    }

    

    
    private String generatePrompt(String indexName, MappingMetadata mappingMetadata, SearchHit[] samples) {
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        if (mappingSource == null) {
            return "No mapping properties found for index: " + indexName;
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index structure and provide insights:\\n\\n");
        prompt.append("Index Name: ").append(indexName).append("\\n\\n");
        prompt.append("Index Mapping:\\n");
        
        // Format mapping information
        StringJoiner mappingInfo = new StringJoiner("\\n");
        extractFieldsInfo(mappingSource, "", mappingInfo);
        prompt.append(mappingInfo.toString()).append("\\n\\n");
        
        // Add sample document if available
        if (samples != null && samples.length > 0) {
            prompt.append("Sample Document:\\n");
            prompt.append(samples[0].getSourceAsString()).append("\\n\\n");
        }
        
        prompt.append("Please provide:\\n");
        prompt.append("1. <total_summarization>A concise description of what this index appears to store and its purpose</total_summarization>\\n");
        prompt.append("2. <column_summarization>For each field, provide a brief description of what it contains and its purpose\\n");
        prompt.append("Format as: field_name: description</column_summarization>");
        
        return prompt.toString();
    }
    
    private void extractFieldsInfo(Map<String, Object> properties, String prefix, StringJoiner joiner) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Map<String, Object> fieldProperties = (Map<String, Object>) entry.getValue();
            
            String type = fieldProperties.containsKey("type") ? fieldProperties.get("type").toString() : "object";
            joiner.add("- " + fieldName + ": " + type);
            
            // Handle nested properties
            if (fieldProperties.containsKey("properties")) {
                extractFieldsInfo((Map<String, Object>) fieldProperties.get("properties"), fieldName, joiner);
            }
        }
    }
    
    private void callLLM(String indexName, String prompt, String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            log.error("Model ID is not configured for Index Insight Job");
            return;
        }
        
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Collections.singletonMap("prompt", prompt))
            .build();
            
        MLPredictionTaskRequest request = new MLPredictionTaskRequest(
            modelId,
            MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(),
            null, null
        );

        client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(mlTaskResponse -> {
            log.info("LLM call successful for index: {}", indexName);
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlTaskResponse.getOutput();
            ModelTensors modelTensors = modelTensorOutput.getMlModelOutputs().get(0);
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            Map<String, Object> dataAsMap = (Map<String, Object>) modelTensor.getDataAsMap();
            
            extractModelResponse(dataAsMap, ActionListener.wrap(response -> {
                log.debug("LLM response for index {}: {}", indexName, response);
                
                // Parse the response
                String indexDescription = parseIndexDescription(response);
                Map<String, Object> fieldDescription = parseFieldDescription(response);

                log.debug("Parsed results for index {}: indexDescription: {}, fieldDescription: {}", indexName, indexDescription, fieldDescription);
                
                // Save the insights
                saveInsights(indexName, indexDescription, fieldDescription);
            }, e -> {
                log.error("Failed to process LLM response for index: {}", indexName, e);
            }));
        }, e -> {
            log.error("Failed to call LLM for index: {} with modelId: {}", indexName, modelId, e);
        }));
    }
    
    /**
     * Safely extracts model response text from the model output data structure.
     * 
     * @param dataAsMap Data mapping from model output
     * @param listener ActionListener to handle success or failure
     */
    private void extractModelResponse(Map<String, Object> dataAsMap, ActionListener<String> listener) {
        if (dataAsMap == null) {
            listener.onFailure(new IllegalStateException("Model inference failed"));
            return;
        }
        
        try {
            Map<String, Object> output = (Map<String, Object>) dataAsMap.get("output");
            if (output == null) {
                // Fallback to old format
                String response = (String) dataAsMap.get("response");
                if (response == null || response.isEmpty()) {
                    listener.onFailure(new IllegalStateException("Model inference failed, no response returned"));
                    return;
                }
                listener.onResponse(response);
                return;
            }
            
            Map<String, Object> message = (Map<String, Object>) output.get("message");
            if (message == null) {
                listener.onFailure(new IllegalStateException("Model inference failed, incorrect message format"));
                return;
            }
            
            ArrayList<?> content = (ArrayList<?>) message.get("content");
            if (content == null || content.isEmpty()) {
                listener.onFailure(new IllegalStateException("Model inference failed, content is empty"));
                return;
            }
            
            Map<String, Object> firstContent = (Map<String, Object>) content.get(0);
            String response = (String) firstContent.get("text");
            
            if (response == null || response.isEmpty()) {
                listener.onFailure(new IllegalStateException("Model inference failed, no response returned"));
                return;
            }
            
            listener.onResponse(response);
        } catch (ClassCastException | IndexOutOfBoundsException e) {
            listener.onFailure(new IllegalStateException("Model inference failed, incorrect response format", e));
        }
    }
    
    private String parseIndexDescription(String modelResponse) {
        String[] tmpList = modelResponse.split("<total_summarization>");
        if (tmpList.length < 2) {
            return "No index description provided";
        }
        
        String[] descParts = tmpList[tmpList.length - 1].split("</total_summarization>");
        if (descParts.length < 1) {
            return "No index description provided";
        }
        
        return descParts[0].trim();
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
    
    private void saveInsights(String indexName, String indexDescription, Map<String, Object> fieldDescription) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("index_name", indexName);
        docMap.put("index_description", indexDescription);
        docMap.put("field_description", fieldDescription);
        docMap.put("last_updated_time", Instant.now().toEpochMilli());
        
        String docId = generateDocId(indexName);
        
        UpdateRequest updateRequest = new UpdateRequest(ML_INDEX_INSIGHT_INDEX, docId)
            .doc(docMap, MediaTypeRegistry.JSON)
            .docAsUpsert(true)
            .retryOnConflict(3);
        
        client.update(updateRequest, ActionListener.wrap(r -> {
            log.info("Successfully saved insights for index: {} with result: {}", indexName, r.getResult());
        }, e -> {
            log.error("Failed to save index insights for index: {} with docId: {}", indexName, docId, e);
        }));
    }
    
    private String generateDocId(String indexName) {
        String docId = Hashing.sha256().hashString(indexName, StandardCharsets.UTF_8).toString();
        log.debug("Generated docId for index {}: {}", indexName, docId);
        return docId;
    }
}