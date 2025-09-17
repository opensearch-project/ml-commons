/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_QUERY;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_SEARCH_TEMPLATE;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.DEFAULT_USER_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.TEMPLATE_SELECTION_SYSTEM_PROMPT;
import static org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate.TEMPLATE_SELECTION_USER_PROMPT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;

/**
 * This tool supports different types of query planning,
 * llmGenerated, systemSearchTemplates or userSearchTemplates.
 * //TODO only support llmGenerated for now.
 * //TODO to add in systemSearchTemplates or userSearchTemplates when searchTemplatesTool is implemented.
 */

@ToolAnnotation(QueryPlanningTool.TYPE)
public class QueryPlanningTool implements WithModelTool {
    public static final String TYPE = "QueryPlanningTool";
    public static final String MODEL_ID_FIELD = "model_id";
    private final MLModelTool queryGenerationTool;
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    public static final String USER_PROMPT_FIELD = "user_prompt";
    public static final String INDEX_MAPPING_FIELD = "index_mapping";
    public static final String QUERY_FIELDS_FIELD = "query_fields";
    private static final String GENERATION_TYPE_FIELD = "generation_type";
    private static final String LLM_GENERATED_TYPE_FIELD = "llmGenerated";
    private static final String USER_SEARCH_TEMPLATES_TYPE_FIELD = "user_templates";
    private static final String SEARCH_TEMPLATES_FIELD = "search_templates";
    public static final String TEMPLATE_FIELD = "template";
    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are an OpenSearch Query DSL generation assistant, translating natural language questions to OpenSeach DSL Queries";
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
    static String DEFAULT_DESCRIPTION = "Use this tool to generate opensearch query dsl for a given natural language question.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    private final Client client;

    public QueryPlanningTool(String generationType, MLModelTool queryGenerationTool, Client client) {
        this(generationType, queryGenerationTool, client, null);
    }

    public QueryPlanningTool(String generationType, MLModelTool queryGenerationTool, Client client, String searchTemplates) {
        this.generationType = generationType;
        this.queryGenerationTool = queryGenerationTool;
        this.client = client;
        this.searchTemplates = searchTemplates;
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
        if (!validate(parameters)) {
            listener.onFailure(new IllegalArgumentException("Empty parameters for QueryPlanningTool: " + parameters));
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
        templateSelectionParameters.put(SYSTEM_PROMPT_FIELD, TEMPLATE_SELECTION_SYSTEM_PROMPT);
        templateSelectionParameters.put(USER_PROMPT_FIELD, TEMPLATE_SELECTION_USER_PROMPT);
        templateSelectionParameters.put(SEARCH_TEMPLATES_FIELD, searchTemplates);

        ActionListener<T> templateSelectionListener = ActionListener.wrap(r -> {
            try {
                String templateId = (String) r;
                if (templateId == null || templateId.isBlank() || templateId.equals("null")) {
                    // Default search template if LLM does not choose
                    parameters.put(TEMPLATE_FIELD, DEFAULT_SEARCH_TEMPLATE);
                    executeQueryPlanning(parameters, listener);
                } else {
                    // Retrieve search template by ID
                    GetStoredScriptRequest getStoredScriptRequest = new GetStoredScriptRequest(templateId);
                    client.admin().cluster().getStoredScript(getStoredScriptRequest, ActionListener.wrap(getStoredScriptResponse -> {
                        parameters.put(TEMPLATE_FIELD, gson.toJson(getStoredScriptResponse.getSource().getSource()));
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
    }

    private <T> void executeQueryPlanning(Map<String, String> parameters, ActionListener<T> listener) {
        // Execute Query Planning, replace System and User prompt fields
        if (!parameters.containsKey(SYSTEM_PROMPT_FIELD)) {
            parameters.put(SYSTEM_PROMPT_FIELD, DEFAULT_SYSTEM_PROMPT);
        }
        if (!parameters.containsKey(USER_PROMPT_FIELD)) {
            parameters.put(USER_PROMPT_FIELD, DEFAULT_USER_PROMPT);
        }
        if (parameters.containsKey(INDEX_MAPPING_FIELD)) {
            parameters.put(INDEX_MAPPING_FIELD, gson.toJson(parameters.get(INDEX_MAPPING_FIELD)));
        }
        if (parameters.containsKey(QUERY_FIELDS_FIELD)) {
            parameters.put(QUERY_FIELDS_FIELD, gson.toJson(parameters.get(QUERY_FIELDS_FIELD)));
        }
        ActionListener<T> modelListener = ActionListener.wrap(r -> {
            try {
                String queryString = (String) r;
                if (queryString == null || queryString.isBlank() || queryString.equals("null")) {
                    StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                    String defaultQueryString = substitutor.replace(DEFAULT_QUERY);
                    listener.onResponse((T) defaultQueryString);
                } else {
                    listener.onResponse((T) queryString);
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
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    public static class Factory implements WithModelTool.Factory<QueryPlanningTool> {
        private Client client;
        private static volatile Factory INSTANCE;
        private static MLFeatureEnabledSetting mlFeatureEnabledSetting;

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

        public void init(Client client, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
            this.client = client;
            this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        }

        @Override
        public QueryPlanningTool create(Map<String, Object> map) {

            if (!mlFeatureEnabledSetting.isAgenticSearchEnabled()) {
                throw new OpenSearchException(ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE);
            }

            MLModelTool queryGenerationTool = MLModelTool.Factory.getInstance().create(map);

            String type = (String) map.get(GENERATION_TYPE_FIELD);

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
                if (!map.containsKey(SEARCH_TEMPLATES_FIELD)) {
                    throw new IllegalArgumentException("search_templates field is required when generation_type is 'user_templates'");
                } else {
                    // array is parsed as a json string
                    searchTemplates = gson.toJson((String) map.get(SEARCH_TEMPLATES_FIELD));

                }
            }

            return new QueryPlanningTool(type, queryGenerationTool, client, searchTemplates);
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
