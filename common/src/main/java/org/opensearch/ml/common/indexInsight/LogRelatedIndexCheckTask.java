package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_MODEL_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
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
    private final String indexName;
    private final Client client;
    private final ClusterService clusterService;
    private IndexInsightTaskStatus status = IndexInsightTaskStatus.GENERATING;

    private String sampleDocSting;
    private boolean isLogIndex;
    private String logMessageField;
    private String traceIdField;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RCA_TEMPLATE = """
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

    public LogRelatedIndexCheckTask(String indexName, Client client, ClusterService clusterService) {
        this.indexName = indexName;
        this.client = client;
        this.clusterService = clusterService;
    }

    @Override
    public void runTaskLogic() {
        status = IndexInsightTaskStatus.GENERATING;
        try {
            String modelId = clusterService.getClusterSettings().get(ML_COMMONS_INDEX_INSIGHT_MODEL_ID);
            if (modelId == null || modelId.isBlank()) {
                log.error("No model ID configured for RCA index check");
                saveFailedStatus();
                return;
            }

            // Fetch 3 sample docs
            collectSampleDocString();

            // Build prompt
            String prompt = RCA_TEMPLATE
                    .replace("{indexName}", indexName)
                    .replace("{samples}", sampleDocSting);

            // Call LLM
            RemoteInferenceInputDataSet input = RemoteInferenceInputDataSet.builder()
                    .parameters(Collections.singletonMap("prompt", prompt))
                    .build();
            MLPredictionTaskRequest req = new MLPredictionTaskRequest(
                    modelId,
                    MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(input).build(),
                    null,
                    null
            );

            client.execute(MLPredictionTaskAction.INSTANCE, req, ActionListener.wrap(mlResp -> {
                try {
                    ModelTensorOutput out = (ModelTensorOutput) mlResp.getOutput();
                    ModelTensors t = out.getMlModelOutputs().getFirst();
                    ModelTensor mt = t.getMlModelTensors().getFirst();
                    Map<String,Object> data = (Map<String, Object>) mt.getDataAsMap();
                    String text = extractModelResponse(data);
                    Map<String,Object> parsed = parseCheckResponse(text);

                    isLogIndex = Boolean.TRUE.equals(parsed.get("is_log_index"));
                    logMessageField = (String) parsed.get("log_message_field");
                    traceIdField = (String) parsed.get("trace_id_field");

                    saveResult(MAPPER.writeValueAsString(parsed));
                    log.info("Log related check completed for index {}", indexName);
                } catch (Exception e) {
                    log.error("Error parsing response of log related check for {}", indexName, e);
                    saveFailedStatus();
                }
            }, e -> {
                log.error("Failed to call LLM for log related check: {}", e.getMessage(), e);
                saveFailedStatus();
            }));
        } catch (Exception ex) {
            log.error("Failed log related check for {}", indexName, ex);
            saveFailedStatus();
        }
    }
    // Standard IndexInsightTask interface methods
    @Override public MLIndexInsightType getTaskType()    { return taskType; }
    @Override public String getTargetIndex()             { return indexName; }
    @Override public IndexInsightTaskStatus getStatus() { return status; }
    @Override public void setStatus(IndexInsightTaskStatus s) { status = s; }
    @Override public List<MLIndexInsightType> getPrerequisites() { return Collections.emptyList(); }
    @Override public Client getClient()                 { return client; }

    public String getSampleDocString() {
        return sampleDocSting;
    }

    private void collectSampleDocString(){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(3).query(new MatchAllQueryBuilder());
        SearchRequest searchRequest = new SearchRequest(new String[] { indexName }, searchSourceBuilder);

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            List<Map<String, Object>> samples = Arrays.stream(searchResponse.getHits().getHits())
                    .map(SearchHit::getSourceAsMap)
                    .toList();
            sampleDocSting = MAPPER.writeValueAsString(samples);
            log.info("Collected sample documents for index: {}", indexName);
        }, e -> {
            log.error("Failed to collect sample documents for index: {}", indexName, e);
            saveFailedStatus();
        }));
    }

    private String extractModelResponse(Map<String,Object> data) {
        if (data.containsKey("choices")) {
            return JsonPath.read(data, "$.choices[0].message.content");
        }
        if (data.containsKey("output")) {
            return JsonPath.read(data, "$.output.message.content[0].text");
        }
        return JsonPath.read(data, "$.response");
    }

    private Map<String,Object> parseCheckResponse(String resp) {
        try {
            String json = resp.split("<RCA_analysis>",2)[1]
                    .split("</RCA_analysis>",2)[0].trim();
            return MAPPER.readValue(json, new TypeReference<Map<String,Object>>(){});
        } catch (Exception e) {
            log.warn("Parsing log related check JSON failed, defaulting", e);
            Map<String,Object> def = new HashMap<>();
            def.put("is_log_index", false);
            def.put("log_message_field", null);
            def.put("trace_id_field", null);
            return def;
        }
    }
}