/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_AGENT_NAME;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_GENERATING_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_UPDATE_INTERVAL;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_STORAGE_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.regex.Regex;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
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
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.common.hash.Hashing;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

/**
 * Abstract base class providing default implementation for IndexInsightTask
 */
@Log4j2
public abstract class AbstractIndexInsightTask implements IndexInsightTask {

    protected final MLIndexInsightType taskType;
    protected final String sourceIndex;
    protected final Client client;
    protected final SdkClient sdkClient;

    protected AbstractIndexInsightTask(MLIndexInsightType taskType, String sourceIndex, Client client, SdkClient sdkClient) {
        this.taskType = taskType;
        this.sourceIndex = sourceIndex;
        this.client = client;
        this.sdkClient = sdkClient;
    }

    /**
     * Execute the index insight task:
     * 1. Check if record exists in storage
     * 2. Check status and last updated time
     * 3. Check prerequisites
     * 4. Run task logic
     * 5. Write back to storage
     */
    @Override
    public void execute(String tenantId, ActionListener<IndexInsight> listener) {
        getIndexInsight(generateDocId(), tenantId, ActionListener.wrap(getResponse -> {
            if (getResponse.isExists()) {
                handleExistingDoc(getResponse.getSourceAsMap(), tenantId, listener);
            } else {
                SearchSourceBuilder patternSourceBuilder = buildPatternSourceBuilder(taskType.name());
                sdkClient
                    .searchDataObjectAsync(
                        SearchDataObjectRequest
                            .builder()
                            .tenantId(tenantId)
                            .indices(ML_INDEX_INSIGHT_STORAGE_INDEX)
                            .searchSourceBuilder(patternSourceBuilder)
                            .build()
                    )
                    .whenComplete((r, throwable) -> {
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            handleError("Failed to search pattern matched documents for index: {}", cause, tenantId, listener, false);
                        } else {
                            SearchResponse searchResponse = r.searchResponse();
                            SearchHit[] hits = searchResponse.getHits().getHits();
                            Map<String, Object> mappedPatternSource = matchPattern(hits, sourceIndex);
                            if (Objects.isNull(mappedPatternSource)) {
                                beginGeneration(tenantId, listener);
                            } else {
                                handlePatternMatchedDoc(mappedPatternSource, tenantId, listener);
                            }
                        }
                    });
            }
        }, listener::onFailure));
    }

    protected void handleExistingDoc(Map<String, Object> source, String tenantId, ActionListener<IndexInsight> listener) {
        String currentStatus = (String) source.get(IndexInsight.STATUS_FIELD);
        Long lastUpdateTime = (Long) source.get(IndexInsight.LAST_UPDATE_FIELD);
        long currentTime = Instant.now().toEpochMilli();

        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);
        switch (status) {
            case GENERATING:
                // Check if generating timeout
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > INDEX_INSIGHT_GENERATING_TIMEOUT) {
                    beginGeneration(tenantId, listener);
                } else {
                    // If still generating and not timeout, task is already running
                    listener
                        .onFailure(
                            new OpenSearchStatusException("Index insight is being generated, please wait...", RestStatus.TOO_MANY_REQUESTS)
                        );
                }
                break;
            case COMPLETED:
                // Check if needs update
                if (lastUpdateTime != null && (currentTime - lastUpdateTime) > INDEX_INSIGHT_UPDATE_INTERVAL) {
                    beginGeneration(tenantId, listener);
                } else {
                    // Return existing result
                    IndexInsight insight = IndexInsight
                        .builder()
                        .index((String) source.get(IndexInsight.INDEX_NAME_FIELD))
                        .taskType(MLIndexInsightType.valueOf((String) source.get(IndexInsight.TASK_TYPE_FIELD)))
                        .content((String) source.get(IndexInsight.CONTENT_FIELD))
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
                        .tenantId(tenantId)
                        .build();
                    listener.onResponse(insight);
                }
                break;
            case FAILED:
                // Retry failed task
                beginGeneration(tenantId, listener);
                break;
        }
    }

    /**
     * Handle pattern matched document
     */
    protected void handlePatternMatchedDoc(Map<String, Object> patternSource, String tenantId, ActionListener<IndexInsight> listener) {
        String currentStatus = (String) patternSource.get(IndexInsight.STATUS_FIELD);
        IndexInsightTaskStatus status = IndexInsightTaskStatus.fromString(currentStatus);

        // If pattern result is not completed, fall back to normal generation
        if (status != IndexInsightTaskStatus.COMPLETED) {
            beginGeneration(tenantId, listener);
            return;
        }

        // If pattern result is completed but expired, fall back to normal generation
        Long lastUpdateTime = (Long) patternSource.get(IndexInsight.LAST_UPDATE_FIELD);
        long currentTime = Instant.now().toEpochMilli();
        if (lastUpdateTime != null && (currentTime - lastUpdateTime) > INDEX_INSIGHT_UPDATE_INTERVAL) {
            beginGeneration(tenantId, listener);
            return;
        }

        // Pattern result is completed and valid
        handlePatternResult(patternSource, tenantId, listener);
    }

    /**
     * Begin the index insight generation process by updating task status to GENERATING and executing the task with prerequisites.
     */
    protected void beginGeneration(String tenantId, ActionListener<IndexInsight> listener) {
        IndexInsight indexInsight = IndexInsight
            .builder()
            .index(sourceIndex)
            .tenantId(tenantId)
            .taskType(taskType)
            .status(IndexInsightTaskStatus.GENERATING)
            .lastUpdatedTime(Instant.now())
            .build();

        writeIndexInsight(
            indexInsight,
            tenantId,
            ActionListener.wrap(r -> { runWithPrerequisites(tenantId, listener); }, listener::onFailure)
        );
    }

    protected void runWithPrerequisites(String tenantId, ActionListener<IndexInsight> listener) {
        List<MLIndexInsightType> prerequisites = getPrerequisites();
        AtomicInteger completedCount = new AtomicInteger(0);
        if (prerequisites.isEmpty()) {
            runTask(tenantId, listener);
            return;
        }

        // Run all prerequisites
        for (MLIndexInsightType prerequisite : prerequisites) {
            IndexInsightTask prerequisiteTask = createPrerequisiteTask(prerequisite);
            prerequisiteTask.execute(tenantId, ActionListener.wrap(prereqInsight -> {
                if (completedCount.incrementAndGet() == prerequisites.size()) {
                    runTask(tenantId, listener);
                }
            }, e -> { saveFailedStatus(tenantId, new Exception("Failed to run prerequisite: " + prerequisite, e), listener); }));
        }
    }

    protected void saveResult(String content, String tenantId, ActionListener<IndexInsight> listener) {
        IndexInsight insight = IndexInsight
            .builder()
            .index(sourceIndex)
            .taskType(taskType)
            .content(content)
            .status(IndexInsightTaskStatus.COMPLETED)
            .lastUpdatedTime(Instant.now())
            .tenantId(tenantId)
            .build();

        writeIndexInsight(insight, tenantId, ActionListener.wrap(r -> { listener.onResponse(insight); }, e -> {
            handleError("Failed to save completed result for index: {}", e, tenantId, listener);
        }));
    }

    protected void saveFailedStatus(String tenantId, Exception error, ActionListener<IndexInsight> listener) {
        IndexInsight indexInsight = IndexInsight
            .builder()
            .tenantId(tenantId)
            .index(sourceIndex)
            .taskType(taskType)
            .status(IndexInsightTaskStatus.FAILED)
            .lastUpdatedTime(Instant.now())
            .build();
        writeIndexInsight(
            indexInsight,
            tenantId,
            ActionListener.wrap(r -> { listener.onFailure(error); }, e -> { listener.onFailure(e); })
        );
    }

    /**
     * Generate document ID for current task
     */
    protected String generateDocId() {
        return generateDocId(taskType);
    }

    /**
     * Generate document ID for specific task type
     */
    protected String generateDocId(MLIndexInsightType taskType) {
        String combined = sourceIndex + "_" + taskType.toString();
        return Hashing.sha256().hashString(combined, StandardCharsets.UTF_8).toString();
    }

    /**
     * Get insight content from storage for a specific task type
     */
    protected void getInsightContentFromContainer(
        MLIndexInsightType taskType,
        String tenantId,
        ActionListener<Map<String, Object>> listener
    ) {
        String docId = generateDocId(taskType);
        getIndexInsight(docId, tenantId, ActionListener.wrap(getResponse -> {
            try {
                String content = getResponse.isExists() ? getResponse.getSourceAsMap().get(IndexInsight.CONTENT_FIELD).toString() : "";
                Map<String, Object> contentMap = gson.fromJson(content, Map.class);
                listener.onResponse(contentMap);
            } catch (Exception e) {
                // Return empty content on JSON parsing failure
                listener.onResponse(new HashMap<>());
            }
        }, listener::onFailure));
    }

    protected void handlePatternResult(Map<String, Object> patternSource, String tenantId, ActionListener<IndexInsight> listener) {
        // Default implementation: return pattern result as-is
        Long lastUpdateTime = (Long) patternSource.get(IndexInsight.LAST_UPDATE_FIELD);
        IndexInsight insight = IndexInsight
            .builder()
            .index(sourceIndex)
            .taskType(taskType)
            .content((String) patternSource.get(IndexInsight.CONTENT_FIELD))
            .status(IndexInsightTaskStatus.COMPLETED)
            .lastUpdatedTime(Instant.ofEpochMilli(lastUpdateTime))
            .tenantId(tenantId)
            .build();
        listener.onResponse(insight);
    }

    private void getIndexInsight(String docId, String tenantId, ActionListener<GetResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .getDataObjectAsync(
                    GetDataObjectRequest.builder().tenantId(tenantId).index(ML_INDEX_INSIGHT_STORAGE_INDEX).id(docId).build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to get index insight document", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            GetResponse getResponse = r.getResponse();
                            assert getResponse != null;
                            listener.onResponse(getResponse);
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void writeIndexInsight(IndexInsight indexInsight, String tenantId, ActionListener<Boolean> listener) {
        String docId = generateDocId();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .putDataObjectAsync(
                    PutDataObjectRequest
                        .builder()
                        .tenantId(tenantId)
                        .index(ML_INDEX_INSIGHT_STORAGE_INDEX)
                        .dataObject(indexInsight)
                        .id(docId)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to write index insight document", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            assert indexResponse != null;
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED
                                || indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                                listener.onResponse(true);
                            } else {
                                listener.onFailure(new RuntimeException("Failed to put generating index insight doc"));
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    protected static void getAgentIdToRun(Client client, String tenantId, ActionListener<String> actionListener) {
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
    protected static void extractFieldNamesTypes(
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
                        fieldsToType.put(prefix + n, fieldType);
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

    private static SearchSourceBuilder buildPatternSourceBuilder(String taskType) {
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
    private static String extractModelResponse(Map<String, Object> data) {
        if (data.containsKey("choices")) {
            return JsonPath.read(data, "$.choices[0].message.content");
        }
        if (data.containsKey("content")) {
            return JsonPath.read(data, "$.content[0].text");
        }
        return JsonPath.read(data, "$.response");
    }

    private static Map<String, Object> matchPattern(SearchHit[] hits, String targetIndex) {
        for (SearchHit hit : hits) {
            Map<String, Object> source = hit.getSourceAsMap();
            String pattern = (String) source.get(INDEX_NAME_FIELD);
            if (Regex.simpleMatch(pattern, targetIndex)) {
                return source;
            }
        }
        return null;
    }

    /**
     * Common method to call LLM with agent and handle response parsing
     */
    protected static void callLLMWithAgent(
        Client client,
        String agentId,
        String prompt,
        String sourceIndex,
        ActionListener<String> listener
    ) {
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
                if (result.startsWith("{")) {
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

    protected void handleError(String message, Exception e, String tenantId, ActionListener<IndexInsight> listener, boolean shouldStore) {
        log.error(message, sourceIndex, e);
        if (shouldStore) {
            saveFailedStatus(tenantId, e, listener);
        } else {
            listener.onFailure(e);
        }
    }

    protected void handleError(String message, Exception e, String tenantId, ActionListener<IndexInsight> listener) {
        handleError(message, e, tenantId, listener, true);
    }
}
