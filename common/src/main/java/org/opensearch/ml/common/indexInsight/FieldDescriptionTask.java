/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.getAgentIdToRun;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.get.GetRequest;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

/**
 * Field Description Task: Generates descriptions for index fields using LLM.
 * This task analyzes index mapping and sample data to provide meaningful descriptions
 * for each field in the index, helping down-stream tasks understand the purpose and content of fields.
 */
@Log4j2
public class FieldDescriptionTask implements IndexInsightTask {

    private static final int BATCH_SIZE = 50; // Hard-coded value for now
    private final MLIndexInsightType taskType = MLIndexInsightType.FIELD_DESCRIPTION;
    private final String indexName;
    private final MappingMetadata mappingMetadata;
    private final Client client;
    private final ClusterService clusterService;
    private IndexInsightTaskStatus status = IndexInsightTaskStatus.GENERATING;

    public FieldDescriptionTask(String indexName, Client client, ClusterService clusterService) {
        this.indexName = indexName;
        this.mappingMetadata = clusterService.state().metadata().index(indexName).mapping();
        this.client = client;
        this.clusterService = clusterService;
    }

    @Override
    public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            getInsightContentFromContainer(storageIndex, MLIndexInsightType.STATISTICAL_DATA, ActionListener.wrap(statisticalContent -> {
                getAgentIdToRun(client, tenantId, ActionListener.wrap(agentId -> {
                    batchProcessFields(statisticalContent, agentId, storageIndex, listener);
                }, listener::onFailure));
            }, e -> {
                log.error("Failed to get statistical content for index {}", indexName, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to execute field description task for index {}", indexName, e);
            saveFailedStatus(storageIndex);
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

    private void getInsightContentFromContainer(String storageIndex, MLIndexInsightType taskType, ActionListener<String> listener) {
        String docId = generateDocId(indexName, taskType);
        GetRequest getRequest = new GetRequest(storageIndex, docId);

        client.get(getRequest, ActionListener.wrap(response -> {
            String content = response.isExists() ? response.getSourceAsMap().get("content").toString() : "";
            listener.onResponse(content);
        }, e -> {
            log.warn("Failed to get insight content for {} task of index {} from container {}", taskType, indexName, storageIndex, e);
            listener.onResponse("");
        }));
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

    private void batchProcessFields(String statisticalContent, String agentId, String storageIndex, ActionListener<IndexInsight> listener) {
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        if (mappingSource == null || mappingSource.isEmpty()) {
            log.error("No mapping properties found for index: {}", indexName);
            saveFailedStatus(storageIndex);
            return;
        }

        List<String> allFields = new ArrayList<>();
        extractAllFieldNames(mappingSource, "", allFields);

        if (allFields.isEmpty()) {
            log.warn("No fields found for index: {}", indexName);
            saveResult("", storageIndex, ActionListener.wrap(insight -> {
                log.info("Empty field description completed for: {}", indexName);
                listener.onResponse(insight);
            }, e -> {
                log.error("Failed to save empty field description result for index {}", indexName, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }));
            return;
        }

        List<List<String>> batches = createBatches(allFields, BATCH_SIZE);
        CountDownLatch countDownLatch = new CountDownLatch(batches.size());
        Map<String, Object> resultsMap = new ConcurrentHashMap<>();
        AtomicBoolean hasErrors = new AtomicBoolean(false);

        ActionListener<Map<String, Object>> batchListener = ActionListener.wrap(batchResult -> {
            if (batchResult != null) {
                resultsMap.putAll(batchResult);
            }
            countDownLatch.countDown();
            if (countDownLatch.getCount() == 0) {
                // All-or-nothing strategy: only save results if ALL batches succeed
                // If any batch fails, the entire task is marked as failed and no partial results are saved
                if (!hasErrors.get()) {
                    saveResult(gson.toJson(resultsMap), storageIndex, ActionListener.wrap(insight -> {
                        log.info("Field description completed for: {}", indexName);
                        listener.onResponse(insight);
                    }, e -> {
                        log.error("Failed to save field description result for index {}", indexName, e);
                        saveFailedStatus(storageIndex);
                        listener.onFailure(e);
                    }));
                } else {
                    saveFailedStatus(storageIndex);
                    listener.onFailure(new Exception("Batch processing failed"));
                }
            }
        }, e -> {
            countDownLatch.countDown();
            hasErrors.set(true);
            log.error("Batch processing failed for index {}: {}", indexName, e.getMessage());
            if (countDownLatch.getCount() == 0) {
                saveFailedStatus(storageIndex);
                listener.onFailure(new Exception("Batch processing failed"));
            }
        });

        for (List<String> batch : batches) {
            processBatch(batch, statisticalContent, agentId, batchListener);
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

    private void processBatch(
        List<String> batchFields,
        String statisticalContent,
        String agentId,
        ActionListener<Map<String, Object>> listener
    ) {
        String prompt = generateBatchPrompt(batchFields, statisticalContent);

        AgentMLInput agentInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(agentId)
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(Collections.singletonMap("prompt", prompt)).build())
            .build();

        MLExecuteTaskRequest executeRequest = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);

        client.execute(MLExecuteTaskAction.INSTANCE, executeRequest, ActionListener.wrap(mlResp -> {
            try {
                log.info("Batch LLM call successful for {} fields in index {}", batchFields.size(), indexName);
                ModelTensorOutput out = (ModelTensorOutput) mlResp.getOutput();
                ModelTensors t = out.getMlModelOutputs().get(0);
                ModelTensor mt = t.getMlModelTensors().get(0);
                Map<String, Object> data = (Map<String, Object>) mt.getDataAsMap();
                if (Objects.isNull(data)) {
                    data = gson.fromJson(mt.getResult(), Map.class);
                }
                String response = extractModelResponse(data);
                Map<String, Object> batchResult = parseFieldDescription(response);
                listener.onResponse(batchResult);
            } catch (Exception e) {
                log.error("Error parsing response for batch in index {}: {}", indexName, e.getMessage());
                listener.onFailure(e);
            }
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
        Map<String, Object> relevantStatisticalData = extractRelevantStatisticalData(statisticalContent, batchFields);
        if (!relevantStatisticalData.isEmpty()) {
            if (relevantStatisticalData.containsKey("mapping")) {
                prompt.append("Field Mapping:\\n").append(relevantStatisticalData.get("mapping")).append("\\n\\n");
            }
            if (relevantStatisticalData.containsKey("distribution")) {
                prompt.append("Field Distribution:\\n").append(relevantStatisticalData.get("distribution")).append("\\n\\n");
            }
            if (relevantStatisticalData.containsKey("example_docs")) {
                prompt.append("Example Documents:\\n").append(relevantStatisticalData.get("example_docs")).append("\\n\\n");
            }
        }

        prompt.append("For each field listed above, provide a brief description of what it contains and its purpose.\\n");
        prompt.append("For each field, provide description in the following format EXACTLY:\\n");
        prompt.append("field_name: description");

        return prompt.toString();
    }

    private Map<String, Object> extractRelevantStatisticalData(String statisticalContent, List<String> batchFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (statisticalContent == null || statisticalContent.isEmpty() || batchFields.isEmpty()) {
            return result;
        }

        try {
            Map<String, Object> statisticalData = JsonPath.read(statisticalContent, "$");
            Map<String, Object> mapping = (Map<String, Object>) statisticalData.get("mapping");
            Map<String, Object> distribution = (Map<String, Object>) statisticalData.get("distribution");

            // Extract relevant mapping
            Map<String, Object> relevantMapping = new LinkedHashMap<>();
            for (String field : batchFields) {
                if (mapping != null && mapping.containsKey(field)) {
                    relevantMapping.put(field, mapping.get(field));
                }
            }
            if (!relevantMapping.isEmpty()) {
                result.put("mapping", relevantMapping);
            }

            // Extract relevant distribution
            Map<String, Object> relevantDistribution = new LinkedHashMap<>();
            for (String field : batchFields) {
                if (distribution != null && distribution.containsKey(field)) {
                    relevantDistribution.put(field, distribution.get(field));
                }
            }
            if (!relevantDistribution.isEmpty()) {
                result.put("distribution", relevantDistribution);
            }

            // Extract example docs from distribution
            if (distribution != null && distribution.containsKey("example_docs")) {
                List<Map<String, Object>> exampleDocs = (List<Map<String, Object>>) distribution.get("example_docs");
                if (exampleDocs != null && !exampleDocs.isEmpty()) {
                    List<Map<String, Object>> relevantExampleDocs = new ArrayList<>();
                    for (Map<String, Object> doc : exampleDocs) {
                        Map<String, Object> relevantFields = new LinkedHashMap<>();
                        for (String field : batchFields) {
                            if (doc.containsKey(field)) {
                                relevantFields.put(field, doc.get(field));
                            }
                        }
                        if (!relevantFields.isEmpty()) {
                            relevantExampleDocs.add(relevantFields);
                        }
                    }
                    if (!relevantExampleDocs.isEmpty()) {
                        result.put("example_docs", relevantExampleDocs);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract relevant statistical data for batch fields: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Auto-detects LLM response format and extracts the response text.
     */
    private String extractModelResponse(Map<String, Object> data) {
        if (data.containsKey("choices")) {
            return JsonPath.read(data, "$.choices[0].message.content");
        }
        if (data.containsKey("content")) {
            return JsonPath.read(data, "$.content[0].text");
        }
        return JsonPath.read(data, "$.response");
    }

    private Map<String, Object> parseFieldDescription(String modelResponse) {
        Map<String, Object> field2Desc = new LinkedHashMap<>();
        String[] lines = modelResponse.trim().split("\\n");

        for (String line : lines) {
            line = line.trim();
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String desc = parts[1].trim();
                if (!desc.isEmpty()) {
                    field2Desc.put(name, desc);
                }
            }
        }

        return field2Desc;
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        if (prerequisiteType == MLIndexInsightType.STATISTICAL_DATA) {
            return new StatisticalDataTask(indexName, client);
        }
        throw new IllegalArgumentException("Unsupported prerequisite type: " + prerequisiteType);
    }
}
