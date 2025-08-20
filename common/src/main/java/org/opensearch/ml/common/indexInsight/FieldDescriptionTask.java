/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.callLLMWithAgent;
import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.getAgentIdToRun;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.core.action.ActionListener;
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
    private static final MLIndexInsightType TASK_TYPE = MLIndexInsightType.FIELD_DESCRIPTION;
    private final String sourceIndex;
    private final Client client;

    public FieldDescriptionTask(String sourceIndex, Client client) {
        this.sourceIndex = sourceIndex;
        this.client = client;
    }

    @Override
    public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        try {
            getInsightContentFromContainer(storageIndex, MLIndexInsightType.STATISTICAL_DATA, ActionListener.wrap(statisticalContent -> {
                getAgentIdToRun(client, tenantId, ActionListener.wrap(agentId -> {
                    batchProcessFields(statisticalContent, agentId, storageIndex, listener);
                }, listener::onFailure));
            }, e -> {
                log.error("Failed to get statistical content for index {}", sourceIndex, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to execute field description task for index {}", sourceIndex, e);
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }
    }

    @Override
    public MLIndexInsightType getTaskType() {
        return TASK_TYPE;
    }

    @Override
    public String getSourceIndex() {
        return sourceIndex;
    }

    @Override
    public Client getClient() {
        return client;
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.singletonList(MLIndexInsightType.STATISTICAL_DATA);
    }

    private void batchProcessFields(String statisticalContent, String agentId, String storageIndex, ActionListener<IndexInsight> listener) {
        Map<String, Object> mappingSource;
        try {
            mappingSource = (Map<String, Object>) ((Map<String, Object>) JsonPath.read(statisticalContent, "$")).get("mapping");
        } catch (Exception e) {
            listener.onFailure(new RuntimeException("Failed to parse statistic content for field description task to get mappings"));
            return;
        }

        if (mappingSource == null || mappingSource.isEmpty()) {
            log.error("No mapping properties found for index: {}", sourceIndex);
            saveFailedStatus(storageIndex);
            listener.onFailure(new IllegalStateException("No mapping properties found for index: " + sourceIndex));
            return;
        }

        List<String> allFields = new ArrayList<>();
        extractAllFieldNames(mappingSource, "", allFields);

        if (allFields.isEmpty()) {
            log.warn("No fields found for index: {}", sourceIndex);
            saveResult("", storageIndex, ActionListener.wrap(insight -> {
                log.info("Empty field description completed for: {}", sourceIndex);
                listener.onResponse(insight);
            }, e -> {
                log.error("Failed to save empty field description result for index {}", sourceIndex, e);
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
                        log.info("Field description completed for: {}", sourceIndex);
                        listener.onResponse(insight);
                    }, e -> {
                        log.error("Failed to save field description result for index {}", sourceIndex, e);
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
            log.error("Batch processing failed for index {}: {}", sourceIndex, e.getMessage());
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

        callLLMWithAgent(client, agentId, prompt, sourceIndex, ActionListener.wrap(response -> {
            try {
                log.info("Batch LLM call successful for {} fields in index {}", batchFields.size(), sourceIndex);
                Map<String, Object> batchResult = parseFieldDescription(response);
                listener.onResponse(batchResult);
            } catch (Exception e) {
                log.error("Error parsing response for batch in index {}: {}", sourceIndex, e.getMessage());
                listener.onFailure(e);
            }
        }, listener::onFailure));
    }

    private String generateBatchPrompt(List<String> batchFields, String statisticalContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index fields and provide descriptions:\\n\\n");
        prompt.append("Index Name: ").append(sourceIndex).append("\\n\\n");

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

        prompt
            .append(
                "For each field listed above, provide a brief description of what it contains and its purpose. The description should not mention specific values from any example documents or include specific examples.\\n"
            );
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
                    List<Map<String, Object>> filteredExampleDocs = new ArrayList<>();
                    for (Map<String, Object> doc : exampleDocs) {
                        Map<String, Object> filteredDoc = new LinkedHashMap<>();
                        for (String field : batchFields) {
                            if (doc.containsKey(field)) {
                                filteredDoc.put(field, doc.get(field));
                            }
                        }
                        filteredExampleDocs.add(filteredDoc);
                    }
                    result.put("example_docs", filteredExampleDocs);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract relevant statistical data for batch fields: {}", e.getMessage());
        }
        return result;
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
            return new StatisticalDataTask(sourceIndex, client);
        }
        throw new IllegalArgumentException("Unsupported prerequisite type: " + prerequisiteType);
    }
}
