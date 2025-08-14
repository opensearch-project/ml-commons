/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.indexInsight.IndexInsightUtils.getAgentIdToRun;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

/** Check whether the index is log-related for downstream Task：Log-based RCA analysis
1. Judge whether the index is related to log
2. Whether there is a column containing the whole log message
3. Whether there is a column serve as trace id which combine a set of logs into one flow
4. The Whole return is a fixed format which can be parsed in the following process
 */
@Log4j2
public class LogRelatedIndexCheckTask implements IndexInsightTask {
    private final MLIndexInsightType taskType = MLIndexInsightType.LOG_RELATED_INDEX_CHECK;
    private final String sourceIndex;
    private final Client client;
    private final ClusterService clusterService;

    private String sampleDocSting;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RCA_TEMPLATE =
        """
            I will provide you an index with the types and statistics of each field, and a few sample documents.

            Your task is to analyze the structure and semantics of this index, and determine whether it is suitable for Root Cause Analysis (RCA) on logs.

            Please help me answer the following 3 questions based on the provided information:

            1. Is this index related to **log data**?
            2. Is there any **field that contains full log messages** (e.g., raw log lines or unstructured log content)?
            3. Is there any **field that can serve as a trace ID**, i.e., grouping multiple logs into the same logical execution or transaction flow?

            The index name is:
            {indexName}

            Here are 3 sample documents from this index:
            {samples}

            You should infer your answer **based on both field names, their data types, value examples, and overall context**.
            Avoid simply repeating the input values. Think logically about what each field represents and how it might be used.

            Return your result in the **following strict JSON format** inside tags, so that it can be parsed later. Only include fields that you are confident about.

            <RCA_analysis>
            {
              "is_log_index": true/false,
              "log_message_field": "field_name" or null,
              "trace_id_field": "field_name" or null
            }
            </RCA_analysis>

            Rules:
            - If you cannot confidently find a log message field or trace ID field, use `null`.
            - Your judgment should be based on both semantics and field patterns (e.g., field names like "message", "log", "trace", "span", etc).
            """;

    public LogRelatedIndexCheckTask(String sourceIndex, Client client, ClusterService clusterService) {
        this.sourceIndex = sourceIndex;
        this.client = client;
        this.clusterService = clusterService;
    }

    @Override
    public void runTask(String storageIndex, String tenantId, ActionListener<IndexInsight> listener) {
        try {
            collectSampleDocString(ActionListener.wrap(sampleDocs -> {
                getAgentIdToRun(
                    client,
                    tenantId,
                    ActionListener.wrap(agentId -> callLLM(agentId, storageIndex, listener), listener::onFailure)
                );
            }, listener::onFailure));
        } catch (Exception ex) {
            log.error("Failed log related check for {}", sourceIndex, ex);
            saveFailedStatus(storageIndex);
            listener.onFailure(ex);
        }
    }

    // Standard IndexInsightTask interface methods
    @Override
    public MLIndexInsightType getTaskType() {
        return taskType;
    }

    @Override
    public String getSourceIndex() {
        return sourceIndex;
    }

    @Override
    public List<MLIndexInsightType> getPrerequisites() {
        return Collections.emptyList();
    }

    @Override
    public Client getClient() {
        return client;
    }

    public String getSampleDocString() {
        return sampleDocSting;
    }

    private void collectSampleDocString(ActionListener<String> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(3).query(new MatchAllQueryBuilder());
        SearchRequest searchRequest = new SearchRequest(new String[] { sourceIndex }, searchSourceBuilder);

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            try {
                List<Map<String, Object>> samples = Arrays
                    .stream(searchResponse.getHits().getHits())
                    .map(SearchHit::getSourceAsMap)
                    .toList();
                sampleDocSting = gson.toJson(samples);
                log.info("Collected sample documents for index: {}", sourceIndex);
                listener.onResponse(sampleDocSting);
            } catch (Exception e) {
                log.error("Failed to process sample documents for index: {}", sourceIndex, e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", sourceIndex, e);
            listener.onFailure(e);
        }));
    }

    private void callLLM(String agentId, String storageIndex, ActionListener<IndexInsight> listener) {
        // Build prompt
        String prompt = RCA_TEMPLATE.replace("{indexName}", sourceIndex).replace("{samples}", sampleDocSting);

        AgentMLInput agentInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(agentId)
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(Collections.singletonMap("prompt", prompt)).build())
            .build();

        MLExecuteTaskRequest executeRequest = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);

        client.execute(MLExecuteTaskAction.INSTANCE, executeRequest, ActionListener.wrap(mlResp -> {
            try {
                ModelTensorOutput out = (ModelTensorOutput) mlResp.getOutput();
                ModelTensors t = out.getMlModelOutputs().get(0);
                ModelTensor mt = t.getMlModelTensors().get(0);
                Map<String, Object> data = (Map<String, Object>) mt.getDataAsMap();
                if (Objects.isNull(data)) {
                    data = gson.fromJson(mt.getResult(), Map.class);
                }
                String text = extractModelResponse(data);
                Map<String, Object> parsed = parseCheckResponse(text);

                saveResult(MAPPER.writeValueAsString(parsed), storageIndex, ActionListener.wrap(insight -> {
                    log.info("Log related check completed for index {}", sourceIndex);
                    listener.onResponse(insight);
                }, e -> {
                    log.error("Failed to save log related check result for index {}", sourceIndex, e);
                    saveFailedStatus(storageIndex);
                    listener.onFailure(e);
                }));
            } catch (Exception e) {
                log.error("Error parsing response of log related check for {}", sourceIndex, e);
                saveFailedStatus(storageIndex);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to call LLM for log related check: {}", e.getMessage(), e);
            saveFailedStatus(storageIndex);
            listener.onFailure(e);
        }));
    }

    private Map<String, Object> parseCheckResponse(String resp) {
        try {
            String json = resp.split("<RCA_analysis>", 2)[1].split("</RCA_analysis>", 2)[0].trim();
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse RCA analysis response, returning default values", e);
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("is_log_index", false);
            defaultResult.put("log_message_field", null);
            defaultResult.put("trace_id_field", null);
            return defaultResult;
        }
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        throw new IllegalArgumentException("LogRelatedIndexCheckTask has no prerequisites");
    }
}
