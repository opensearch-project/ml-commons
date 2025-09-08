/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.utils.StringUtils.MAPPER;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.log4j.Log4j2;

/** Check whether the index is log-related for downstream task：Log-based RCA analysis
1. Judge whether the index is related to log
2. Whether there is a column containing the whole log message
3. Whether there is a column serve as trace id which combine a set of logs into one flow
4. The Whole return is a fixed format which can be parsed in the following process
 */
@Log4j2
public class LogRelatedIndexCheckTask extends AbstractIndexInsightTask {
    private final String sourceIndex;
    private final Client client;
    private final SdkClient sdkClient;

    private String sampleDocString;

    private static final Map<String, Object> DEFAULT_RCA_RESULT;

    static {
        DEFAULT_RCA_RESULT = new HashMap<>();
        DEFAULT_RCA_RESULT.put("is_log_index", false);
        DEFAULT_RCA_RESULT.put("log_message_field", null);
        DEFAULT_RCA_RESULT.put("trace_id_field", null);
    }

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

    public LogRelatedIndexCheckTask(String sourceIndex, Client client, SdkClient sdkClient) {
        this.sourceIndex = sourceIndex;
        this.client = client;
        this.sdkClient = sdkClient;
    }

    @Override
    public void runTask(String tenantId, ActionListener<IndexInsight> listener) {
        try {
            collectSampleDocString(ActionListener.wrap(sampleDocs -> {
                getAgentIdToRun(
                    client,
                    tenantId,
                    ActionListener.wrap(agentId -> performLogAnalysis(agentId, tenantId, listener), listener::onFailure)
                );
            }, listener::onFailure));
        } catch (Exception e) {
            handleError("Failed log related check for {}", e, tenantId, listener);
        }
    }

    // Standard IndexInsightTask interface methods
    @Override
    public MLIndexInsightType getTaskType() {
        return MLIndexInsightType.LOG_RELATED_INDEX_CHECK;
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

    @Override
    public SdkClient getSdkClient() {
        return sdkClient;
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
                sampleDocString = gson.toJson(samples);
                log.info("Collected sample documents for index: {}", sourceIndex);
                listener.onResponse(sampleDocString);
            } catch (Exception e) {
                log.error("Failed to process sample documents for index: {}", sourceIndex, e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", sourceIndex, e);
            listener.onFailure(e);
        }));
    }

    private void performLogAnalysis(String agentId, String tenantId, ActionListener<IndexInsight> listener) {
        String prompt = RCA_TEMPLATE.replace("{indexName}", sourceIndex).replace("{samples}", sampleDocString);

        callLLMWithAgent(client, agentId, prompt, sourceIndex, ActionListener.wrap(response -> {
            try {
                Map<String, Object> parsed = parseCheckResponse(response);
                saveResult(MAPPER.writeValueAsString(parsed), tenantId, ActionListener.wrap(insight -> {
                    log.info("Log related check completed for index {}", sourceIndex);
                    listener.onResponse(insight);
                }, e -> handleError("Failed to save log related check result for index {}", e, tenantId, listener)));
            } catch (Exception e) {
                handleError("Error parsing response of log related check for {}", e, tenantId, listener);
            }
        }, e -> handleError("Failed to call LLM for log related check: {}", e, tenantId, listener)));
    }

    private Map<String, Object> parseCheckResponse(String resp) {
        try {
            String json = resp.split("<RCA_analysis>", 2)[1].split("</RCA_analysis>", 2)[0].trim();
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse RCA analysis response, returning default values", e);
            return DEFAULT_RCA_RESULT;
        }
    }

    @Override
    public IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType) {
        throw new IllegalStateException("LogRelatedIndexCheckTask has no prerequisites");
    }

}
