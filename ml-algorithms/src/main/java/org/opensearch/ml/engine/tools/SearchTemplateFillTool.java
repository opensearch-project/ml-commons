/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.processor.ProcessorChain.OUTPUT_PROCESSORS;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.INDEX_NAME_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.QUESTION_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.STRICT_FIELD;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.CANNOT_EXPRESS_FIELD;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.CHOICE_FIELD;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.MAX_CANDIDATES;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.NONE_CHOICE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplateResolver;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.ml.engine.tools.templatefill.FillToolSchema;
import org.opensearch.ml.engine.tools.templatefill.FillValueValidator;
import org.opensearch.ml.engine.tools.templatefill.TemplateFillPrompts;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.reflect.TypeToken;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Generates a {@code _search} body by filling the parameters of a registered agentic search template,
 * instead of having the model author the whole query.
 *
 * <p>The model is given a forced tool whose fields are the template's parameters (from the param-schema
 * stored at registration) and returns only values; the cluster renders them into the stored Mustache
 * body. With several candidate templates, one forced call picks a template and fills it. The model may
 * decline through a {@code cannot_express} flag when no template fits the question.
 *
 * <p>A decline, or any failure along the way, falls back to a {@link QueryPlanningTool} built from the
 * same parameters, which writes the query directly. Experimental: gated behind
 * {@code plugins.ml_commons.agentic_search_template_enabled}.
 *
 * <p>Configuration: {@code model_id} and {@code _llm_interface} (one that supports forced tool calls) in the
 * tool's parameters, and {@code template_ids} in the tool's {@code config}, which execution parameters
 * cannot override. Each stored script is read, and rendered, as the searching user, so with the security
 * plugin enabled that user needs {@code cluster:admin/script/get}; a template they cannot read is not
 * offered to the model.
 */
@Log4j2
@ToolAnnotation(SearchTemplateFillTool.TYPE)
public class SearchTemplateFillTool implements WithModelTool {
    public static final String TYPE = "SearchTemplateFillTool";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String TEMPLATE_IDS_FIELD = "template_ids";

    static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    static final String USER_PROMPT_FIELD = "user_prompt";
    static final String PROMPT_FIELD = "prompt";
    static final String TOOL_CONFIGS_FIELD = "tool_configs";
    static final String WHOLE_RESPONSE_FILTER = "$";
    static final String NO_PROCESSORS = "[]";

    static String DEFAULT_DESCRIPTION = "Use this tool to generate OpenSearch Query DSL from a natural language question by filling "
        + "the parameters of a registered search template. Provide a 'question' parameter with the complete natural language "
        + "query and 'index_name' with the index to search. The tool returns a valid OpenSearch query.";

    public static final String DEFAULT_INPUT_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"question\":{\"type\":\"string\",\"description\":\"Complete natural language query with all necessary context.\"},"
        + "\"index_name\":{\"type\":\"string\",\"description\":\"the name of the index against which the query needs to be generated.\"}"
        + "},"
        + "\"required\":[\"question\", \"index_name\"],"
        + "\"additionalProperties\":false"
        + "}";

    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);

    private final Client client;
    private final AgenticSearchTemplateResolver resolver;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Getter
    private final String modelId;
    private final FunctionCalling functionCalling;
    @Getter
    private final List<String> templateIds;
    @Getter
    private final QueryPlanningTool fallbackTool;

    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    @Setter
    private Map<String, Object> attributes;

    public SearchTemplateFillTool(
        Client client,
        AgenticSearchTemplateResolver resolver,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        String modelId,
        FunctionCalling functionCalling,
        List<String> templateIds,
        QueryPlanningTool fallbackTool
    ) {
        this.client = client;
        this.resolver = resolver;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.modelId = modelId;
        this.functionCalling = functionCalling;
        this.templateIds = templateIds;
        this.fallbackTool = fallbackTool;
        this.attributes = new HashMap<>(DEFAULT_ATTRIBUTES);
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> delegate) {
        ActionListener<T> listener = ActionListener.notifyOnce(delegate);
        // The setting is dynamic, so an agent registered while the feature was on must stop filling once it is off.
        if (mlFeatureEnabledSetting == null || !mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()) {
            listener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }
        Map<String, String> parameters;
        try {
            parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
        } catch (Exception e) {
            fallback(originalParameters, listener, "could not read the tool input", e);
            return;
        }
        if (!validate(parameters)) {
            listener
                .onFailure(
                    new IllegalArgumentException(
                        String
                            .format(
                                Locale.ROOT,
                                "Validation error: missing or empty required parameters — %s, %s.",
                                INDEX_NAME_FIELD,
                                QUESTION_FIELD
                            )
                    )
                );
            return;
        }
        String indexName = parameters.get(INDEX_NAME_FIELD);
        // Listeners here are explicit rather than ActionListener.wrap: wrap routes an exception thrown by the
        // caller's own onResponse into onFailure, which would then run the fallback and answer the caller twice.
        resolver.getTemplates(templateIds, indexName, new ActionListener<>() {
            @Override
            public void onResponse(Map<String, AgenticSearchTemplate> usable) {
                List<AgenticSearchTemplate> candidates = selectCandidates(usable);
                if (candidates.isEmpty()) {
                    fallback(originalParameters, listener, "no usable template for index " + indexName, null);
                    return;
                }
                fill(parameters, candidates, new ActionListener<>() {
                    @Override
                    public void onResponse(String rendered) {
                        listener.onResponse((T) rendered);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (e instanceof CannotExpressException) {
                            fallback(originalParameters, listener, e.getMessage(), null);
                        } else {
                            fallback(originalParameters, listener, "template fill failed", e);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                fallback(originalParameters, listener, "template lookup failed", e);
            }
        });
    }

    /**
     * The templates to offer, in configured order: those the resolver found usable (registered, bound to
     * the searched index, readable by the caller) with a non-empty schema, capped so the merged schema stays
     * bounded.
     */
    @VisibleForTesting
    List<AgenticSearchTemplate> selectCandidates(Map<String, AgenticSearchTemplate> usable) {
        List<AgenticSearchTemplate> candidates = new ArrayList<>();
        for (String templateId : templateIds) {
            AgenticSearchTemplate template = usable.get(templateId);
            if (template == null) {
                log.debug("Search template {} is not usable for this search; skipping", templateId);
                continue;
            }
            if (!template.hasParams()) {
                log.warn("Search template {} has an empty param-schema; skipping", templateId);
                continue;
            }
            if (candidates.size() >= MAX_CANDIDATES) {
                log.warn("Search template candidates capped at {}; ignoring {}", MAX_CANDIDATES, templateId);
                continue;
            }
            candidates.add(template);
        }
        return candidates;
    }

    /** One forced tool call picks (when several candidates) and fills a template, then the cluster renders it. */
    private void fill(Map<String, String> parameters, List<AgenticSearchTemplate> candidates, ActionListener<String> listener) {
        boolean single = candidates.size() == 1;
        String toolName = single ? TemplateFillPrompts.FILL_TOOL_NAME : TemplateFillPrompts.MULTI_FILL_TOOL_NAME;
        FillToolSchema.Built built;
        Map<String, String> modelParameters;
        try {
            built = single ? FillToolSchema.single(candidates.get(0)) : FillToolSchema.multi(candidates);
            String question = parameters.get(QUESTION_FIELD);
            String toolDescription = single ? TemplateFillPrompts.FILL_TOOL_DESCRIPTION : TemplateFillPrompts.MULTI_FILL_TOOL_DESCRIPTION;
            String systemPrompt = single ? TemplateFillPrompts.FILL_SYSTEM_PROMPT : TemplateFillPrompts.MULTI_FILL_SYSTEM_PROMPT;
            String userPrompt = single
                ? TemplateFillPrompts.fillUserPrompt(question)
                : TemplateFillPrompts.multiFillUserPrompt(question, candidates, built.getPrefixes());
            modelParameters = buildModelParameters(
                parameters,
                systemPrompt,
                userPrompt,
                functionCalling.forcedToolConfigs(toolName, toolDescription, built.getSchema())
            );
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }

        predict(modelParameters, new ActionListener<>() {
            @Override
            public void onResponse(ModelTensorOutput output) {
                AgenticSearchTemplate chosen;
                Map<String, Object> clean;
                try {
                    String toolInput = functionCalling.extractForcedToolInput(output, toolName);
                    if (toolInput == null || toolInput.isBlank()) {
                        throw new IllegalStateException("the model did not call " + toolName);
                    }
                    Map<String, Object> filled = XContentHelper.convertToMap(JsonXContent.jsonXContent, toolInput, true);
                    if (built.isAbstainEnabled() && isTrue(filled.remove(CANNOT_EXPRESS_FIELD))) {
                        throw new CannotExpressException("the model judged the question outside the templates' range");
                    }
                    chosen = single ? candidates.get(0) : choose(candidates, filled.get(CHOICE_FIELD));
                    Map<String, Object> values = toParamNames(built.getPropertyToParam().get(chosen.getTemplateId()), filled);
                    clean = FillValueValidator.validate(chosen.getParamSchema(), values);
                } catch (Exception e) {
                    listener.onFailure(e);
                    return;
                }
                log
                    .info(
                        "Filled search template {} (from {} candidates) with {} params",
                        chosen.getTemplateId(),
                        candidates.size(),
                        clean.size()
                    );
                resolver.renderTemplate(chosen.getTemplateId(), clean, listener);
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    /**
     * The fill call's parameters: the caller's parameters (so connector-level settings still apply), with
     * the fill prompts and the forced tool config. Output filtering is overridden with a whole-response
     * filter and an empty processor chain, since a filter meant for the direct-DSL fallback, whether set on
     * the tool or on the connector, would strip the tool call from the response.
     */
    @VisibleForTesting
    static Map<String, String> buildModelParameters(
        Map<String, String> parameters,
        String systemPrompt,
        String userPrompt,
        String toolConfigs
    ) {
        Map<String, String> modelParameters = new HashMap<>(parameters);
        // Request parameters take precedence over the connector's, so these also replace connector-level filters.
        modelParameters.put(RESPONSE_FILTER_FIELD, WHOLE_RESPONSE_FILTER);
        modelParameters.put(OUTPUT_PROCESSORS, NO_PROCESSORS);
        modelParameters.put(SYSTEM_PROMPT_FIELD, systemPrompt);
        // Connectors name the user turn differently; QueryPlanningTool's use user_prompt, agent ones prompt.
        modelParameters.put(USER_PROMPT_FIELD, userPrompt);
        modelParameters.put(PROMPT_FIELD, userPrompt);
        modelParameters.put(TOOL_CONFIGS_FIELD, toolConfigs);
        String noEscape = modelParameters.get(NO_ESCAPE_PARAMS);
        if (noEscape == null || noEscape.isBlank()) {
            modelParameters.put(NO_ESCAPE_PARAMS, TOOL_CONFIGS_FIELD);
        } else if (!List.of(noEscape.split(",")).stream().map(String::trim).toList().contains(TOOL_CONFIGS_FIELD)) {
            modelParameters.put(NO_ESCAPE_PARAMS, noEscape + "," + TOOL_CONFIGS_FIELD);
        }
        return modelParameters;
    }

    private void predict(Map<String, String> modelParameters, ActionListener<ModelTensorOutput> listener) {
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(modelParameters).build();
        MLPredictionTaskRequest request = MLPredictionTaskRequest
            .builder()
            .modelId(modelId)
            .mlInput(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build())
            .tenantId(modelParameters.get(TENANT_ID_FIELD))
            .build();
        client.execute(MLPredictionTaskAction.INSTANCE, request, new ActionListener<>() {
            @Override
            public void onResponse(MLTaskResponse response) {
                ModelTensorOutput output;
                try {
                    output = (ModelTensorOutput) response.getOutput();
                } catch (Exception e) {
                    listener.onFailure(e);
                    return;
                }
                listener.onResponse(output);
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    /** Resolve the model's template choice against the real candidates. */
    private static AgenticSearchTemplate choose(List<AgenticSearchTemplate> candidates, Object choice) {
        // Matched against the candidates first, so a template whose id is the sentinel stays selectable.
        for (AgenticSearchTemplate candidate : candidates) {
            if (candidate.getTemplateId().equals(choice)) {
                return candidate;
            }
        }
        if (choice == null || NONE_CHOICE.equals(choice) || String.valueOf(choice).isBlank()) {
            throw new CannotExpressException("the model chose no template");
        }
        throw new IllegalArgumentException("the model chose unknown template '" + choice + "'");
    }

    /** Map the emitted property names back to real param names, keeping only the chosen template's params. */
    private static Map<String, Object> toParamNames(Map<String, String> propertyToParam, Map<String, Object> filled) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : filled.entrySet()) {
            String param = propertyToParam.get(entry.getKey());
            if (param != null && entry.getValue() != null) {
                values.put(param, entry.getValue());
            }
        }
        return values;
    }

    /** A flag may arrive as a boolean or a string; plain truthiness would read "false" as true. */
    static boolean isTrue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(Locale.ROOT);
            return s.equals("true") || s.equals("yes") || s.equals("1");
        }
        return value instanceof Number && ((Number) value).doubleValue() == 1;
    }

    private <T> void fallback(Map<String, String> originalParameters, ActionListener<T> listener, String reason, Exception cause) {
        if (cause == null) {
            log.info("Search template fill declined ({}); writing the query directly", reason);
        } else {
            log.warn("Search template fill failed ({}); writing the query directly", reason, cause);
        }
        fallbackTool.run(originalParameters, listener);
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
        return parameters != null
            && parameters.get(QUESTION_FIELD) != null
            && !parameters.get(QUESTION_FIELD).isBlank()
            && parameters.get(INDEX_NAME_FIELD) != null
            && !parameters.get(INDEX_NAME_FIELD).isBlank();
    }

    /** The model declined: no offered template can express the question. Routed to the fallback, not an error. */
    static final class CannotExpressException extends RuntimeException {
        CannotExpressException(String message) {
            super(message);
        }
    }

    public static class Factory implements WithModelTool.Factory<SearchTemplateFillTool> {
        private Client client;
        private AgenticSearchTemplateResolver resolver;
        private MLFeatureEnabledSetting mlFeatureEnabledSetting;
        private static volatile Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SearchTemplateFillTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client, AgenticSearchTemplateResolver resolver, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
            this.client = client;
            this.resolver = resolver;
            this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        }

        @Override
        public SearchTemplateFillTool create(Map<String, Object> params) {
            String modelId = stringParam(params, MODEL_ID_FIELD);
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException(TYPE + " requires a model_id");
            }
            FunctionCalling functionCalling = forcedToolFunctionCalling(stringParam(params, LLM_INTERFACE));
            List<String> templateIds = parseTemplateIds(params.get(TEMPLATE_IDS_FIELD));

            // The fallback writes the query directly with the same model and parameters.
            QueryPlanningTool fallbackTool = QueryPlanningTool.Factory.getInstance().create(new HashMap<>(params));
            return new SearchTemplateFillTool(
                client,
                resolver,
                mlFeatureEnabledSetting,
                modelId,
                functionCalling,
                templateIds,
                fallbackTool
            );
        }

        private static String stringParam(Map<String, Object> params, String key) {
            Object value = params.get(key);
            return value == null ? null : String.valueOf(value);
        }

        /** The function-calling implementation for an {@code _llm_interface}, which must support forced tool calls. */
        public static FunctionCalling forcedToolFunctionCalling(String llmInterface) {
            if (llmInterface == null || llmInterface.isBlank()) {
                throw new IllegalArgumentException(TYPE + " requires " + LLM_INTERFACE + " to be set");
            }
            FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
            if (functionCalling == null || !functionCalling.supportsForcedToolCall()) {
                throw new IllegalArgumentException(TYPE + " does not support " + LLM_INTERFACE + " " + llmInterface);
            }
            return functionCalling;
        }

        /** Parse {@code template_ids}: a JSON array of ids, or a single id. Duplicates are collapsed. */
        public static List<String> parseTemplateIds(Object value) {
            List<String> ids = new ArrayList<>();
            if (value instanceof List) {
                for (Object id : (List<?>) value) {
                    ids.add(String.valueOf(id));
                }
            } else if (value instanceof String && !((String) value).isBlank()) {
                String text = ((String) value).trim();
                if (text.startsWith("[")) {
                    try {
                        List<String> parsed = gson.fromJson(text, new TypeToken<List<String>>() {
                        }.getType());
                        ids.addAll(parsed);
                    } catch (RuntimeException e) {
                        throw new IllegalArgumentException(TYPE + " " + TEMPLATE_IDS_FIELD + " must be a JSON array of template ids", e);
                    }
                } else {
                    ids.add(text);
                }
            }
            LinkedHashSet<String> distinct = new LinkedHashSet<>();
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    distinct.add(id.trim());
                }
            }
            if (distinct.isEmpty()) {
                throw new IllegalArgumentException(TYPE + " requires " + TEMPLATE_IDS_FIELD + " to name at least one template");
            }
            return new ArrayList<>(distinct);
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

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return DEFAULT_ATTRIBUTES;
        }
    }
}
