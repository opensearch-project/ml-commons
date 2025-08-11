/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.getAgentIdToRun;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;


import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
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
 * Index Description Task: Generates comprehensive description of the entire index using LLM.
 * This task analyzes the overall index structure, field mappings, and sample data to provide
 * a thorough understanding of what the index stores and its intended purpose.
 */
@Log4j2
public class IndexDescriptionTask implements IndexInsightTask {

    private final MLIndexInsightType taskType = MLIndexInsightType.INDEX_DESCRIPTION;
    private final String indexName;
    private final MappingMetadata mappingMetadata;
    private final Client client;
    private final ClusterService clusterService;
    private IndexInsightTaskStatus status = IndexInsightTaskStatus.GENERATING;
    private String indexDescription;

    public IndexDescriptionTask(String indexName, MappingMetadata mappingMetadata, Client client, ClusterService clusterService) {
        this.indexName = indexName;
        this.mappingMetadata = mappingMetadata;
        this.client = client;
        this.clusterService = clusterService;
    }

    @Override
    public void runTaskLogic(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            String statisticalContent = getInsightContentFromContainer(storageIndex, MLIndexInsightType.STATISTICAL_DATA);
            String prompt = generateIndexDescriptionPrompt(statisticalContent);
            getAgentIdToRun(
                client,
                tenantId,
                ActionListener.wrap(agentId -> { callLLM(prompt, agentId, storageIndex, listener); }, listener::onFailure)
            );
        } catch (Exception e) {
            log.error("Failed to execute index description task for index {}", indexName, e);
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

    public String getIndexDescription() {
        return indexDescription;
    }

    private String getInsightContentFromContainer(String storageIndex, MLIndexInsightType taskType) {
        String docId = generateDocId(indexName, taskType);
        GetRequest getRequest = new GetRequest(storageIndex, docId);

        try {
            GetResponse response = client.get(getRequest).actionGet();
            if (response.isExists()) {
                return response.getSourceAsMap().get("content").toString();
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to get insight content for {} task of index {} from container {}", taskType, indexName, storageIndex, e);
            return "";
        }
    }



    private String generateIndexDescriptionPrompt(String statisticalContent) {
        Map<String, Object> mappingSource = (Map<String, Object>) mappingMetadata.getSourceAsMap().get("properties");
        if (mappingSource == null) {
            return "No mapping properties found for index: " + indexName;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Please analyze the following OpenSearch index structure and provide insights:\\n\\n");
        prompt.append("Index Name: ").append(indexName).append("\\n\\n");
        prompt.append("Index Mapping:\\n");

        StringJoiner mappingInfo = new StringJoiner("\\n");
        extractFieldsInfo(mappingSource, "", mappingInfo);
        prompt.append(mappingInfo.toString()).append("\\n\\n");

        if (statisticalContent != null && !statisticalContent.isEmpty()) {
            prompt.append("Statistical Data:\\n");
            prompt.append(statisticalContent).append("\\n\\n");
        }

        prompt.append("Please provide a concise description of what this index appears to store and its purpose.");

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

    private void callLLM(String prompt, String agentId, String storageIndex, ActionListener<IndexInsight> listener) {
        AgentMLInput agentInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(agentId)
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(Collections.singletonMap("prompt", prompt)).build())
            .build();

        MLExecuteTaskRequest executeRequest = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);

        client.execute(MLExecuteTaskAction.INSTANCE, executeRequest, ActionListener.wrap(mlResp -> {
            try {
                log.info("LLM call successful for index description: {}", indexName);
                ModelTensorOutput out = (ModelTensorOutput) mlResp.getOutput();
                ModelTensors t = out.getMlModelOutputs().get(0);
                ModelTensor mt = t.getMlModelTensors().get(0);
                Map<String, Object> data = (Map<String, Object>) mt.getDataAsMap();
                if (Objects.isNull(data)) {
                    data = gson.fromJson(mt.getResult(), Map.class);
                }
                String response = extractModelResponse(data);
                indexDescription = parseIndexDescription(response);
                saveResult(indexDescription, storageIndex, ActionListener.wrap(insight -> {
                    log.info("Index description completed for: {}", indexName);
                    listener.onResponse(insight);
                }, e -> {
                    log.error("Failed to save index description result for index {}", indexName, e);
                    saveFailedStatus(storageIndex);
                    listener.onFailure(e);
                }));
            } catch (Exception e) {
                log.error("Error parsing response for index description: {}", indexName, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to call LLM for index description: {}", indexName, e);
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }));
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

    private String parseIndexDescription(String modelResponse) {
        return modelResponse.trim();
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        if (prerequisiteType == MLIndexInsightType.STATISTICAL_DATA) {
            return new StatisticalDataTask(indexName, client);
        }
        throw new IllegalArgumentException("Unsupported prerequisite type: " + prerequisiteType);
    }
}
