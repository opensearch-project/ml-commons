/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_DATETIME_FORMAT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getCurrentDateTime;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY_PLANNING_USER_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_SEARCH_TEMPLATE;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_TEMPLATE_SELECTION_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_TEMPLATE_SELECTION_USER_PROMPT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.processor.ProcessorChain;
import org.opensearch.ml.engine.tools.parser.ToolParser;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.gson.reflect.TypeToken;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports different types of query planning,
 * llmGenerated, systemSearchTemplates or userSearchTemplates.
 */
@Log4j2
@ToolAnnotation(QueryPlanningTool.TYPE)
public class QueryPlanningTool implements WithModelTool {
    public static final String TYPE = "QueryPlanningTool";
    public static final String MODEL_ID_FIELD = "model_id";
    private final MLModelTool queryGenerationTool;
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    public static final String USER_PROMPT_FIELD = "user_prompt";
    public static final String QUERY_PLANNER_SYSTEM_PROMPT_FIELD = "query_planner_system_prompt";
    public static final String QUERY_PLANNER_USER_PROMPT_FIELD = "query_planner_user_prompt";
    public static final String TEMPLATE_SELECTION_SYSTEM_PROMPT_FIELD = "template_selection_system_prompt";
    public static final String TEMPLATE_SELECTION_USER_PROMPT_FIELD = "template_selection_user_prompt";
    public static final String INDEX_MAPPING_FIELD = "index_mapping";
    public static final String QUERY_FIELDS_FIELD = "query_fields";
    public static final String GENERATION_TYPE_FIELD = "generation_type";
    public static final String LLM_GENERATED_TYPE_FIELD = "llmGenerated";
    public static final String USER_SEARCH_TEMPLATES_TYPE_FIELD = "user_templates";
    public static final String SEARCH_TEMPLATES_FIELD = "search_templates";
    public static final String SAMPLE_DOCUMENT_FIELD = "sample_document";
    private static final String CURRENT_TIME_FIELD = "current_time";
    public static final String TEMPLATE_FIELD = "template";
    public static final String STRICT_FIELD = "strict";
    public static final String QUESTION_FIELD = "question";
    private static final String TEMPLATE_ID_FIELD = "template_id";
    private static final String TEMPLATE_DESCRIPTION_FIELD = "template_description";
    public static final String INDEX_NAME_FIELD = "index_name";
    private static final int MAX_TRUNCATE_CHARS = 250;
    private static final String TRUNC_PREFIX = "[truncated]";
    // Agent context parameter keys to ignore
    private static final String CHAT_HISTORY_FIELD = "_chat_history";
    private static final String TOOLS_FIELD = "_tools";
    private static final String INTERACTIONS_FIELD = "_interactions";
    private static final String TOOL_CONFIGS_FIELD = "tool_configs";
    private static final Set<String> AGENT_CONTEXT_EXCLUDED_PARAMS = Set
        .of(CHAT_HISTORY_FIELD, TOOLS_FIELD, INTERACTIONS_FIELD, TOOL_CONFIGS_FIELD);

    @Getter
    private final String generationType;
    @Getter
    private final String searchTemplates;
    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    static String DEFAULT_DESCRIPTION = "Use this tool to generate OpenSearch Query DSL from natural language queries."
        + "Provide a 'question' parameter containing the complete natural language query with all necessary context, requirements, filters, and constraints."
        + "The question should be self-contained with all information needed to generate the OpenSearch DSL."
        + "Provide 'index_name' to help generate more accurate queries based on the index structure."
        + "Optionally provide embedding model ID to be used for neural search "
        + "The tool will return a valid OpenSearch query that can be used to search your data.";

    public static final String DEFAULT_INPUT_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"question\":{\"type\":\"string\",\"description\":\"Complete natural language query with all necessary context to generate OpenSearch DSL. Include the question, any specific requirements, filters, or constraints. Examples: 'Find all products with price greater than 100 dollars', 'Show me documents about machine learning published in 2023', 'Search for users with status active and age between 25 and 35'\"},"
        + "\"index_name\":{\"type\":\"string\",\"description\":\"the name of the index against which the query needs to be generated.\"},"
        + "\"embedding_model_id\":{\"type\":\"string\",\"description\":\"the model id to perform neural search.\"}"
        + "},"
        + "\"required\":[\"question\", \"index_name\"],"
        + "\"additionalProperties\":false"
        + "}";

    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);

    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    private final Client client;

    @Setter
    @Getter
    private Parser outputParser;

    public QueryPlanningTool(String generationType, MLModelTool queryGenerationTool, Client client, String searchTemplates) {
        this.generationType = generationType;
        this.queryGenerationTool = queryGenerationTool;
        this.client = client;
        this.searchTemplates = searchTemplates;
        this.attributes = new HashMap<>(DEFAULT_ATTRIBUTES);
    }

    private Map<String, String> stripAgentContextParameters(Map<String, String> originalParameters) {
        // Drop agent-specific metadata that can bias or slow query planning; keep all other non-null params.
        // This enables using the same LLM for both the agent and the Query Planning Tool.
        // Excluded keys: _chat_history, _tools, _interactions, tool_configs

        return originalParameters
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null && !AGENT_CONTEXT_EXCLUDED_PARAMS.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        try {
            Map<String, String> parameters = stripAgentContextParameters(ToolUtils.extractInputParameters(originalParameters, attributes));
            if (!validate(parameters)) {
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            String
                                .format(
                                    "Validation error: missing or empty required parameters — %s, %s.",
                                    INDEX_NAME_FIELD,
                                    QUESTION_FIELD
                                )
                        )
                    );
                return;
            }

            if (!generationType.equals(USER_SEARCH_TEMPLATES_TYPE_FIELD)) {
                // Use default search template, skip template selection
                parameters.put(TEMPLATE_FIELD, DEFAULT_SEARCH_TEMPLATE);
                executeQueryPlanning(parameters, listener);
                return;
            }

            // Template Selection, replace user and system prompts
            Map<String, String> templateSelectionParameters = new HashMap<>(parameters);
            templateSelectionParameters
                .put(
                    SYSTEM_PROMPT_FIELD,
                    templateSelectionParameters
                        .getOrDefault(TEMPLATE_SELECTION_SYSTEM_PROMPT_FIELD, DEFAULT_TEMPLATE_SELECTION_SYSTEM_PROMPT)
                );

            templateSelectionParameters
                .put(
                    USER_PROMPT_FIELD,
                    templateSelectionParameters.getOrDefault(TEMPLATE_SELECTION_USER_PROMPT_FIELD, DEFAULT_TEMPLATE_SELECTION_USER_PROMPT)
                );

            templateSelectionParameters.put(SEARCH_TEMPLATES_FIELD, searchTemplates);

            ActionListener<T> templateSelectionListener = ActionListener.wrap(r -> {
                // Default search template if LLM does not choose or if returned search template is null
                parameters.put(TEMPLATE_FIELD, DEFAULT_SEARCH_TEMPLATE);
                try {
                    String templateId = (String) r;
                    if (templateId == null || templateId.isBlank() || templateId.equals("null")) {
                        executeQueryPlanning(parameters, listener);
                    } else {
                        // Retrieve search template by ID
                        GetStoredScriptRequest getStoredScriptRequest = new GetStoredScriptRequest(templateId);
                        client.admin().cluster().getStoredScript(getStoredScriptRequest, ActionListener.wrap(getStoredScriptResponse -> {
                            if (getStoredScriptResponse.getSource() != null) {
                                parameters.put(TEMPLATE_FIELD, gson.toJson(getStoredScriptResponse.getSource().getSource()));
                            }
                            executeQueryPlanning(parameters, listener);
                        }, e -> { listener.onFailure(e); }));
                    }
                } catch (Exception e) {
                    IllegalArgumentException parsingException = new IllegalArgumentException(
                        "Error processing search template: " + r + ". Try using response_filter in agent registration if needed.",
                        e
                    );
                    listener.onFailure(parsingException);
                }
            }, listener::onFailure);
            queryGenerationTool.run(templateSelectionParameters, templateSelectionListener);
        } catch (Exception e) {
            log.error("Failed to run QueryPlannerTool", e);
            listener.onFailure(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void executeQueryPlanning(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            // Execute Query Planning, replace System and User prompt fields
            parameters
                .put(SYSTEM_PROMPT_FIELD, parameters.getOrDefault(QUERY_PLANNER_SYSTEM_PROMPT_FIELD, DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT));

            parameters.put(USER_PROMPT_FIELD, parameters.getOrDefault(QUERY_PLANNER_USER_PROMPT_FIELD, DEFAULT_QUERY_PLANNING_USER_PROMPT));

            if (parameters.containsKey(QUERY_FIELDS_FIELD)) {
                parameters.put(QUERY_FIELDS_FIELD, gson.toJson(parameters.get(QUERY_FIELDS_FIELD)));
            }

            String currentDateTime = getCurrentDateTime(DEFAULT_DATETIME_FORMAT);
            parameters.put(CURRENT_TIME_FIELD, gson.toJson(currentDateTime));

            // async chain: getIndexMapping -> getSampleDoc -> call model
            getIndexMappingAsync(parameters.get(INDEX_NAME_FIELD), ActionListener.wrap(indexMapping -> {
                parameters.put(INDEX_MAPPING_FIELD, gson.toJson(indexMapping));
                getSampleDocAsync(parameters.get(INDEX_NAME_FIELD), ActionListener.wrap(sampleDoc -> {
                    parameters.put(SAMPLE_DOCUMENT_FIELD, gson.toJson(sampleDoc));

                    // Now call the model
                    ActionListener<T> modelListener = ActionListener.wrap(r -> {
                        try {
                            String queryString = (String) r;
                            if (queryString == null || queryString.isBlank() || queryString.equals("null")) {
                                log.debug("Model failed to generate the DSL query, returning the Default match all query");
                                StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                                String defaultQueryString = substitutor.replace(DEFAULT_QUERY);
                                listener.onResponse((T) defaultQueryString);
                            } else {
                                listener.onResponse((T) (outputParser != null ? outputParser.parse(queryString) : queryString));
                            }
                        } catch (Exception e) {
                            IllegalArgumentException parsingException = new IllegalArgumentException(
                                "Error processing query string: " + r + ". Try using response_filter in agent registration if needed.",
                                e
                            );
                            listener.onFailure(parsingException);
                        }
                    }, listener::onFailure);
                    queryGenerationTool.run(parameters, modelListener);

                }, listener::onFailure));
            }, listener::onFailure));
        } catch (Exception e) {
            log.error("Failed to run QueryPlannerTool", e);
            listener.onFailure(e);
        }
    }

    private void getSampleDocAsync(String indexName, ActionListener<String> listener) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .size(1)
                .query(QueryBuilders.matchAllQuery())
                .trackTotalHits(false)
                .fetchSource(true)
                .explain(false)
                .profile(false)
                .sort("_doc");
            SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(indexName);

            ActionListener<SearchResponse> searchListener = new ActionListener<>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        SearchHit[] hits = searchResponse.getHits().getHits();
                        if (hits == null || hits.length == 0) {
                            listener.onResponse(null);
                            return;
                        }

                        Map<String, Object> sourceMap = hits[0].getSourceAsMap();
                        if (sourceMap == null || sourceMap.isEmpty()) {
                            listener.onResponse(null);
                            return;
                        }

                        Map<String, String> truncatedSourceMap = new HashMap<>();
                        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
                            String key = entry.getKey();
                            String value = String.valueOf(entry.getValue());
                            // safely process strings with special chars
                            int cpCount = value.codePointCount(0, value.length());
                            if (cpCount > MAX_TRUNCATE_CHARS) {
                                int end = value.offsetByCodePoints(0, MAX_TRUNCATE_CHARS);
                                truncatedSourceMap.put(key, TRUNC_PREFIX + value.substring(0, end));
                            } else {
                                truncatedSourceMap.put(key, value);
                            }
                        }
                        listener.onResponse(gson.toJson(truncatedSourceMap));
                    } catch (Exception e) {
                        log.error("Failed to process sample document");
                        listener.onFailure(new IOException("Failed to process sample document", e));
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to get sample document");
                    listener.onFailure(new IOException("Failed to get sample document", e));
                }
            };

            client.search(searchRequest, searchListener);
        } catch (Exception e) {
            log.error("Failed to get sample document");
            listener.onFailure(new IOException("Failed to get sample document", e));
        }
    }

    private void getIndexMappingAsync(String indexName, ActionListener<String> listener) {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest()
                .indices(indexName)
                .indicesOptions(IndicesOptions.strictExpand())
                .local(false)
                .clusterManagerNodeTimeout(DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT);

            client.admin().indices().getIndex(getIndexRequest, new ActionListener<GetIndexResponse>() {
                @Override
                public void onResponse(GetIndexResponse getIndexResponse) {
                    try {
                        MappingMetadata mapping = getIndexResponse.mappings().get(indexName);
                        listener.onResponse(mapping.source().toString());
                    } catch (Exception e) {
                        listener.onFailure(new IllegalStateException("Failed to extract index mapping", e));
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (e instanceof IndexNotFoundException) {
                        log.warn("Index does not exist or is not available");
                        listener.onFailure(new IllegalArgumentException("Index does not exist or is not available", e));
                    } else {
                        log.warn("Failed to extract index mapping");
                        listener.onFailure(new IllegalStateException("Failed to extract index mapping", e));
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to extract index mapping");
            listener.onFailure(new IllegalStateException("Failed to extract index mapping", e));
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null
            || parameters.size() == 0
            || !parameters.containsKey(QUESTION_FIELD)
            || !parameters.containsKey(INDEX_NAME_FIELD)) {
            return false;
        }
        return true;
    }

    public static class Factory implements WithModelTool.Factory<QueryPlanningTool> {
        private Client client;
        private static volatile Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (QueryPlanningTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public QueryPlanningTool create(Map<String, Object> params) {

            MLModelTool queryGenerationTool = MLModelTool.Factory.getInstance().create(params);

            String type = (String) params.get(GENERATION_TYPE_FIELD);

            // defaulted to llmGenerated
            if (type == null || type.isEmpty()) {
                type = LLM_GENERATED_TYPE_FIELD;
            }

            // type validation
            if (!(LLM_GENERATED_TYPE_FIELD.equals(type) || USER_SEARCH_TEMPLATES_TYPE_FIELD.equals(type))) {
                throw new IllegalArgumentException(
                    "Invalid generation type: " + type + ". The current supported types are llmGenerated and user_templates."
                );
            }

            // Parse search templates if generation type is user_templates
            String searchTemplates = null;
            if (USER_SEARCH_TEMPLATES_TYPE_FIELD.equals(type)) {
                if (!params.containsKey(SEARCH_TEMPLATES_FIELD)) {
                    throw new IllegalArgumentException("search_templates field is required when generation_type is 'user_templates'");
                } else {
                    // array is parsed as a json string
                    String searchTemplatesJson = (String) params.get(SEARCH_TEMPLATES_FIELD);
                    validateSearchTemplates(searchTemplatesJson);
                    searchTemplates = gson.toJson(searchTemplatesJson);
                }
            }

            QueryPlanningTool queryPlanningTool = new QueryPlanningTool(type, queryGenerationTool, client, searchTemplates);

            // Create parser with default extract_json processor + any custom processors
            queryPlanningTool.setOutputParser(createParserWithDefaultExtractJson(params));

            return queryPlanningTool;
        }

        /**
         * Create a parser with a default extract_json processor prepended to any custom processors.
         * This ensures that JSON is extracted from the LLM response before applying any custom processing.
         * 
         * @param params Tool parameters that may contain custom output_processors
         * @return Parser with extract_json as first processor, followed by any custom processors
         */
        private Parser createParserWithDefaultExtractJson(Map<String, Object> params) {
            // Extract any existing custom processors from params
            List<Map<String, Object>> customProcessorConfigs = ProcessorChain.extractProcessorConfigs(params);

            // Create the default extract_json processor config
            Map<String, Object> extractJsonConfig = new HashMap<>();
            extractJsonConfig.put("type", "extract_json");
            extractJsonConfig.put("extract_type", "object"); // Extract JSON objects only
            extractJsonConfig.put("default", DEFAULT_QUERY); // Return default match all query if no JSON found

            // Combine: default extract_json first, then any custom processors
            List<Map<String, Object>> combinedProcessorConfigs = new ArrayList<>();
            combinedProcessorConfigs.add(extractJsonConfig);
            combinedProcessorConfigs.addAll(customProcessorConfigs);

            // Create parser using the combined processor configs
            return ToolParser.createProcessingParser(null, combinedProcessorConfigs);
        }

        private void validateSearchTemplates(Object searchTemplatesObj) {
            List<Map<String, String>> templates = gson.fromJson(searchTemplatesObj.toString(), new TypeToken<List<Map<String, String>>>() {
            }.getType());

            for (Map<String, String> template : templates) {
                validateTemplateFields(template);
            }
        }

        private void validateTemplateFields(Map<String, String> template) {
            // Validate templateId
            String templateId = template.get(TEMPLATE_ID_FIELD);
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("search_templates field entries must have a template_id");
            }

            // Validate templateDescription
            String templateDescription = template.get(TEMPLATE_DESCRIPTION_FIELD);
            if (templateDescription == null || templateDescription.isBlank()) {
                throw new IllegalArgumentException("search_templates field entries must have a template_description");
            }
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }

        @Override
        public List<String> getAllModelKeys() {
            return List.of(MODEL_ID_FIELD);
        }
    }
}
