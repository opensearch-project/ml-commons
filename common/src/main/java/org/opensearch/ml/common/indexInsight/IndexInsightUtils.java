/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_AGENT_NAME;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RegexpQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.common.hash.Hashing;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class IndexInsightUtils {
    public static void getAgentIdToRun(Client client, String tenantId, ActionListener<String> actionListener) {
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(INDEX_INSIGHT_AGENT_NAME, tenantId);
        client.execute(MLConfigGetAction.INSTANCE, mlConfigGetRequest, ActionListener.wrap(r -> {
            MLConfig mlConfig = r.getMlConfig();
            actionListener.onResponse(mlConfig.getConfiguration().getAgentId());
        }, actionListener::onFailure));
    }

    /**
     * Flatten all the fields in the mappings, insert the field to fieldType mapping to a map
     * @param mappingSource the mappings of an index
     * @param fieldsToType the result containing the field to fieldType mapping
     * @param prefix the parent field path
     * @param includeFields whether include the `fields` in a text type field, for some use case like PPLTool, `fields` in a text type field
     *                      cannot be included, but for CreateAnomalyDetectorTool, `fields` must be included.
     */
    public static void extractFieldNamesTypes(
        Map<String, Object> mappingSource,
        Map<String, String> fieldsToType,
        String prefix,
        boolean includeFields
    ) {
        if (prefix.length() > 0) {
            prefix += ".";
        }

        for (Map.Entry<String, Object> entry : mappingSource.entrySet()) {
            String n = entry.getKey();
            Object v = entry.getValue();

            if (v instanceof Map) {
                Map<String, Object> vMap = (Map<String, Object>) v;
                if (vMap.containsKey("type")) {
                    String fieldType = (String) vMap.getOrDefault("type", "");
                    // no need to extract alias into the result, and for object field, extract the subfields only
                    if (!fieldType.equals("alias") && !fieldType.equals("object")) {
                        fieldsToType.put(prefix + n, (String) vMap.get("type"));
                    }
                }
                if (vMap.containsKey("properties")) {
                    extractFieldNamesTypes((Map<String, Object>) vMap.get("properties"), fieldsToType, prefix + n, includeFields);
                }
                if (includeFields && vMap.containsKey("fields")) {
                    extractFieldNamesTypes((Map<String, Object>) vMap.get("fields"), fieldsToType, prefix + n, true);
                }
            }
        }
    }

    /**
     * Generate document ID for index insight task
     */
    public static String generateDocId(String sourceIndex, MLIndexInsightType taskType) {
        String combined = sourceIndex + "_" + taskType.toString();
        return Hashing.sha256().hashString(combined, StandardCharsets.UTF_8).toString();
    }

    public static SearchSourceBuilder buildPatternSourceBuilder(String taskType) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(100);

        RegexpQueryBuilder regexpQuery = QueryBuilders.regexpQuery(INDEX_NAME_FIELD, ".*[*?,].*");

        TermQueryBuilder termQuery = QueryBuilders.termQuery(TASK_TYPE_FIELD, taskType);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().filter(regexpQuery).filter(termQuery);

        sourceBuilder.query(boolQuery);
        return sourceBuilder;
    }

    /**
     * Auto-detects LLM response format and extracts the response text if response_filter is not configured
     */
    public static String extractModelResponse(Map<String, Object> data) {
        if (data.containsKey("choices")) {
            return JsonPath.read(data, "$.choices[0].message.content");
        }
        if (data.containsKey("content")) {
            return JsonPath.read(data, "$.content[0].text");
        }
        return JsonPath.read(data, "$.response");
    }

    public static Map<String, Object> matchPattern(SearchHit[] hits, String targetIndex) {
        for (SearchHit hit : hits) {
            Map<String, Object> source = hit.getSourceAsMap();
            String pattern = (String) source.get(INDEX_NAME_FIELD);
            if (targetIndex.matches(pattern)) {
                return source;
            }
        }
        return null;
    }

    /**
     * Common method to call LLM with agent and handle response parsing
     */
    public static void callLLMWithAgent(Client client, String agentId, String prompt, String sourceIndex, ActionListener<String> listener) {
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
                String result = mt.getResult();
                String response;
                // response_filter is not configured in the LLM connector
                if (result.startsWith("{") || result.startsWith("[")) {
                    Map<String, Object> data = gson.fromJson(result, Map.class);
                    response = extractModelResponse(data);
                } else {
                    // response_filter is configured in the LLM connector
                    response = result;
                }
                listener.onResponse(response);
            } catch (Exception e) {
                log.error("Error parsing LLM response for index {}: {}", sourceIndex, e.getMessage());
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to call LLM for index {}: {}", sourceIndex, e.getMessage());
            listener.onFailure(e);
        }));
    }
}
