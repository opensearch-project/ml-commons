/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports different types of query planning,
 * llmGenerated, systemSearchTemplates or userSearchTemplates.
 * //TODO only support llmGenerated for now.
 * //TODO to add in systemSearchTemplates or userSearchTemplates when searchTemplatesTool is implemented.
 */
@Log4j2
@ToolAnnotation(QueryPlanningTool.TYPE)
public class QueryPlanningTool implements WithModelTool {
    public static final String TYPE = "QueryPlanningTool";
    public static final String MODEL_ID_FIELD = "model_id";
    private final MLModelTool queryGenerationTool;
    public static final String PROMPT_FIELD = "prompt";
    private static final String GENERATION_TYPE_FIELD = "generation_type";
    private static final String LLM_GENERATED_TYPE_FIELD = "llmGenerated";
    private final String generationType;
    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @VisibleForTesting
    static String DEFAULT_DESCRIPTION = "Use this tool to generate query plans for a given query text.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    private String defaultPrompt =
        "You are an OpenSearch Query DSL generation assistant; using the provided index mapping ${parameters.ListIndexTool.output:-}, specified fields ${parameters.fields:-}, and the given sample queries as examples, generate an OpenSearch Query DSL to retrieve the most relevant documents for the user provided natural language question: ${parameters.query_text}\n";
    @Getter
    private Client client;
    @Getter
    private String modelId;
    @Setter
    @Getter
    @VisibleForTesting
    private Parser outputParser;
    @Setter
    @Getter
    private String responseField;

    public QueryPlanningTool(Client client, String modelId, String generationType, MLModelTool queryGenerationTool) {
        this.client = client;
        this.modelId = modelId;
        this.generationType = generationType;
        this.queryGenerationTool = queryGenerationTool;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        if (!parameters.containsKey(PROMPT_FIELD)) {
            parameters.put(PROMPT_FIELD, defaultPrompt);
        }
        ActionListener<List<ModelTensors>> modelListener = ActionListener.wrap(r -> {
            try {
                @SuppressWarnings("unchecked")
                T result = (T) outputParser.parse(r);
                listener.onResponse(result);
            } catch (Exception e) {
                listener.onFailure(e);
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
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
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

        private static Factory INSTANCE;

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

            // TODO to add in , SYSTEM_SEARCH_TEMPLATES_TYPE_FIELD, USER_SEARCH_TEMPLATES_TYPE_FIELD when searchTemplatesTool is
            // implemented.
            if (!LLM_GENERATED_TYPE_FIELD.equals(type)) {
                throw new IllegalArgumentException("Invalid generation type: " + type + ". The current supported types are llmGenerated.");
            }
            return new QueryPlanningTool(client, (String) map.get(MODEL_ID_FIELD), type, queryGenerationTool);
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
