/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
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
    public static final String PROMPT_FIELD = "prompt";
    private static final String GENERATION_TYPE_FIELD = "generation_type";
    private static final String LLM_GENERATED_TYPE_FIELD = "llmGenerated";
    @Getter
    private final String generationType;
    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @VisibleForTesting
    static String DEFAULT_DESCRIPTION = "Use this tool to generate opensearch query dsl for a given natural language question.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    private String defaultQuery =
        "{ \"query\": { \"multi_match\" : { \"query\":    \"${parameters.query_text}\",  \"fields\": ${parameters.query_fields:-[\"*\"]} } } }";
    private String defaultPrompt =
        "You are an OpenSearch Query DSL generation assistant; try using the optional provided index mapping ${parameters.index_mapping:-}, specified fields ${parameters.query_fields:-}, and the given sample queries as examples, generate an OpenSearch Query DSL to retrieve the most relevant documents for the user provided natural language question: ${parameters.query_text}, please return the query dsl only in a string format, no other texts.\n";

    public QueryPlanningTool(String generationType, MLModelTool queryGenerationTool) {
        this.generationType = generationType;
        this.queryGenerationTool = queryGenerationTool;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {

        if (!validate(parameters)) {
            listener.onFailure(new IllegalArgumentException("Empty parameters for QueryPlanningTool: " + parameters));
            return;
        }
        if (!parameters.containsKey(PROMPT_FIELD)) {
            parameters.put(PROMPT_FIELD, defaultPrompt);
        }
        ActionListener<T> modelListener = ActionListener.wrap(r -> {
            try {
                String queryString = (String) r;
                if (queryString == null || queryString.isBlank() || queryString.equals("null")) {
                    StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                    String defaultQueryString = substitutor.replace(this.defaultQuery);
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
        public QueryPlanningTool create(Map<String, Object> map) {

            MLModelTool queryGenerationTool = MLModelTool.Factory.getInstance().create(map);

            String type = (String) map.get(GENERATION_TYPE_FIELD);
            if (type == null || type.isEmpty()) {
                type = LLM_GENERATED_TYPE_FIELD;
            }

            // TODO to add in SYSTEM_SEARCH_TEMPLATES_TYPE_FIELD, USER_SEARCH_TEMPLATES_TYPE_FIELD when searchTemplatesTool is
            // implemented.
            if (!LLM_GENERATED_TYPE_FIELD.equals(type)) {
                throw new IllegalArgumentException("Invalid generation type: " + type + ". The current supported types are llmGenerated.");
            }
            return new QueryPlanningTool(type, queryGenerationTool);
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
