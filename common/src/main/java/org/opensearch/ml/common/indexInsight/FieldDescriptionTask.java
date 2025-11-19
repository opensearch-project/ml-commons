/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.ml.common.indexInsight.StatisticalDataTask.EXAMPLE_DOC_KEYWORD;
import static org.opensearch.ml.common.indexInsight.StatisticalDataTask.IMPORTANT_COLUMN_KEYWORD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Field Description Task: Generates descriptions for index fields using LLM.
 * This task analyzes index mapping and sample data to provide meaningful descriptions
 * for each field in the index, helping down-stream tasks understand the purpose and content of fields.
 */
@Log4j2
public class FieldDescriptionTask extends AbstractIndexInsightTask {

    private static final int BATCH_SIZE = 50; // Hard-coded value for now

    public FieldDescriptionTask(String sourceIndex, Client client, SdkClient sdkClient) {
        super(MLIndexInsightType.FIELD_DESCRIPTION, sourceIndex, client, sdkClient);
    }

    @Override
    public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
        try {
            getInsightContentFromContainer(MLIndexInsightType.STATISTICAL_DATA, tenantId, ActionListener.wrap(statisticalContentMap -> {
                getAgentIdToRun(
                    client,
                    tenantId,
                    ActionListener
                        .wrap(agentId -> { batchProcessFields(statisticalContentMap, agentId, tenantId, listener); }, listener::onFailure)
                );
            }, e -> handleError("Failed to get statistical content for index {}", e, tenantId, listener)));
        } catch (Exception e) {
            handleError("Failed to execute field description task for index {}", e, tenantId, listener);
        }
    }

    /**
     * Filter pattern-matched field descriptions to only include fields present in current index
     */
    private Map<String, Object> filterFieldDescriptions(
        Map<String, Object> patternFieldDescriptions,
        Map<String, Object> currentIndexFields
    ) {
        Map<String, Object> filteredDescriptions = new LinkedHashMap<>();

        if (patternFieldDescriptions == null || currentIndexFields == null) {
            return filteredDescriptions;
        }

        currentIndexFields
            .keySet()
            .stream()
            .filter(patternFieldDescriptions::containsKey)
            .forEach(fieldName -> filteredDescriptions.put(fieldName, patternFieldDescriptions.get(fieldName)));

        return filteredDescriptions;
    }

    @Override
    protected void handlePatternResult(Map<String, Object> patternSource, String tenantId, ActionListener<IndexInsight> listener) {
        try {
            String patternContent = (String) patternSource.get(IndexInsight.CONTENT_FIELD);
            Map<String, Object> patternFieldDescriptions = gson.fromJson(patternContent, Map.class);

            // Get current index mapping
            GetMappingsRequest getMappingsRequest = new GetMappingsRequest().indices(sourceIndex);

            client.admin().indices().getMappings(getMappingsRequest, ActionListener.wrap(getMappingsResponse -> {
                try {
                    Map<String, MappingMetadata> mappings = getMappingsResponse.getMappings();
                    if (mappings.isEmpty()) {
                        beginGeneration(tenantId, listener);
                        return;
                    }

                    // Extract field names from current index mapping
                    Map<String, String> currentFields = new HashMap<>();
                    for (MappingMetadata mappingMetadata : mappings.values()) {
                        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
                        if (mappingSource != null) {
                            extractFieldNamesTypes(mappingSource, currentFields, "", false);
                        }
                    }

                    Map<String, Object> currentFieldsMap = new HashMap<>(currentFields);

                    Map<String, Object> filteredDescriptions = filterFieldDescriptions(patternFieldDescriptions, currentFieldsMap);

                    // Create filtered result without storing
                    Long lastUpdateTime = (Long) patternSource.get(IndexInsight.LAST_UPDATE_FIELD);
                    IndexInsight insight = IndexInsight
                        .builder()
                        .index(sourceIndex)
                        .taskType(taskType)
                        .content(gson.toJson(filteredDescriptions))
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
                        .build();
                    listener.onResponse(insight);

                } catch (Exception e) {
                    log.error("Failed to process current index mapping for index {}", sourceIndex, e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to get current index mapping for index {}", sourceIndex, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Failed to filter field descriptions for index {}", sourceIndex, e);
            listener.onFailure(e);
        }
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.singletonList(MLIndexInsightType.STATISTICAL_DATA);
    }

    private void batchProcessFields(
        Map<String, Object> statisticalContentMap,
        String agentId,
        String tenantId,
        ActionListener<IndexInsight> listener
    ) {
        Map<String, Object> mappingSource;
        Object obj = statisticalContentMap.get(IMPORTANT_COLUMN_KEYWORD);
        if (!(obj instanceof Map)) {
            handleError(
                "No mapping properties found for index: {}",
                new IllegalStateException("No data distribution found for index: " + sourceIndex),
                tenantId,
                listener
            );
            return;
        }
        mappingSource = (Map<String, Object>) obj;

        List<String> allFields = List.of(mappingSource.keySet().toArray(new String[0]));

        if (allFields.isEmpty()) {
            log.warn("No important fields found for index: {}", sourceIndex);
            saveResult("", tenantId, ActionListener.wrap(insight -> {
                log.info("Empty field description completed for: {}", sourceIndex);
                listener.onResponse(insight);
            }, e -> handleError("Failed to save empty field description result for index {}", e, tenantId, listener)));
            return;
        }

        List<List<String>> batches = createBatches(allFields, BATCH_SIZE);
        CountDownLatch countDownLatch = new CountDownLatch(batches.size());
        Map<String, Object> resultsMap = new ConcurrentHashMap<>();
        AtomicBoolean hasErrors = new AtomicBoolean(false);
        ActionListener<Map<String, Object>> resultListener = ActionListener.wrap(batchResult -> {
            if (batchResult != null)
                resultsMap.putAll(batchResult);
        }, e -> {
            hasErrors.set(true);
            log.error("Batch processing failed for index {}: {}", sourceIndex, e.getMessage());
        });
        LatchedActionListener<Map<String, Object>> latchedActionListener = new LatchedActionListener<>(resultListener, countDownLatch);
        for (List<String> batch : batches) {
            processBatch(batch, statisticalContentMap, agentId, tenantId, latchedActionListener);
        }
        try {
            countDownLatch.await(60, SECONDS);
            if (!hasErrors.get()) {
                saveResult(gson.toJson(resultsMap), tenantId, ActionListener.wrap(insight -> {
                    log.info("Field description completed for: {}", sourceIndex);
                    listener.onResponse(insight);
                }, e -> handleError("Failed to save field description result for index {}", e, tenantId, listener)));
            } else {
                handleError("Batch processing failed for index {}", new Exception("Batch processing failed"), tenantId, listener);
            }
        } catch (InterruptedException e) {
            log.error("Batch processing interrupted for index: {}", sourceIndex);
            handleError("Batch processing interrupted for index {}", e, tenantId, listener);
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
        Map<String, Object> statisticalContentMap,
        String agentId,
        String tenantId,
        ActionListener<Map<String, Object>> listener
    ) {
        String prompt = generateBatchPrompt(batchFields, statisticalContentMap);

        callLLMWithAgent(client, agentId, prompt, sourceIndex, tenantId, ActionListener.wrap(response -> {
            try {
                log.info("Batch LLM call successful for {} fields in index {}", batchFields.size(), sourceIndex);
                Map<String, Object> batchResult = parseFieldDescription(response);
                listener.onResponse(batchResult);
            } catch (Exception e) {
                log.error("Error parsing response for batch in index {}: {}", sourceIndex, e.getMessage());
                listener.onFailure(e);
            }
        }, e -> { listener.onFailure(e); }));
    }

    private String generateBatchPrompt(List<String> batchFields, Map<String, Object> statisticalContentMap) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index fields and provide descriptions:\\n\\n");
        prompt.append("Index Name: ").append(sourceIndex).append("\\n\\n");

        prompt.append("Fields to describe:\\n");
        for (String field : batchFields) {
            prompt.append("- ").append(field).append("\\n");
        }
        prompt.append("\\n");
        // Filter statistical data based on current batch fields
        Map<String, Object> relevantStatisticalData = extractRelevantStatisticalData(statisticalContentMap, batchFields);
        if (!relevantStatisticalData.isEmpty()) {
            if (relevantStatisticalData.containsKey(IMPORTANT_COLUMN_KEYWORD)) {
                prompt.append("Some Field Distribution:\\n").append(relevantStatisticalData.get(IMPORTANT_COLUMN_KEYWORD)).append("\\n\\n");
            }
            if (relevantStatisticalData.containsKey(EXAMPLE_DOC_KEYWORD)) {
                prompt.append("Example Documents:\\n").append(relevantStatisticalData.get(EXAMPLE_DOC_KEYWORD)).append("\\n\\n");
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

    private Map<String, Object> extractRelevantStatisticalData(Map<String, Object> statisticalContentMap, List<String> batchFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (statisticalContentMap == null || statisticalContentMap.isEmpty() || batchFields.isEmpty()) {
            return result;
        }

        try {
            Map<String, Object> distribution = (Map<String, Object>) statisticalContentMap.get(IMPORTANT_COLUMN_KEYWORD);

            // Extract relevant mapping
            Map<String, Object> relevantMapping = new LinkedHashMap<>();
            for (String field : batchFields) {
                if (distribution != null && distribution.containsKey(field)) {
                    relevantMapping.put(field, distribution.get(field));
                }
            }
            if (!relevantMapping.isEmpty()) {
                result.put(IMPORTANT_COLUMN_KEYWORD, relevantMapping);
            }

            // Extract example docs from distribution
            List<Map<String, Object>> exampleDocs = (List<Map<String, Object>>) statisticalContentMap.get(EXAMPLE_DOC_KEYWORD);
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
                result.put(EXAMPLE_DOC_KEYWORD, filteredExampleDocs);
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
            return new StatisticalDataTask(sourceIndex, client, sdkClient);
        }
        throw new IllegalStateException("Unsupported prerequisite type: " + prerequisiteType);
    }

}
