/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * All index I/O and registration logic for agentic-search template param-schemas.
 *
 * <p>Registration (§4.5) is the interesting path: given only a {@code _scripts}
 * template name + target index, it (1) fetches the stored Mustache body, (2) fetches
 * the index mapping, (3) derives the param-schema — {@link MustacheTemplateAnalyzer}
 * for names/types/required, the mapping for field-name enums — and (4) pre-flight
 * validates by rendering the body twice (all-filled and required-only) through the
 * cluster's own Mustache engine, so a broken body fails here, not on the customer's
 * first query. The derived schema is then the customer's editable tuning surface.
 */
@Log4j2
public class AgenticSearchTemplateService {

    private static final int DEFAULT_MAX_TEMPLATES = 1000;
    private static final String INDEX = CommonValue.ML_AGENTIC_SEARCH_TEMPLATES_INDEX;
    private static final String NOT_FOUND_ERROR = "Agentic search template not found: ";
    // param-schema entry keys shared with the analyzer.
    private static final String TYPE_KEY = "type";
    private static final String ENUM_KEY = "enum";
    private static final String SOURCE_KEY = "source";

    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final ClusterService clusterService;
    private final ScriptService scriptService;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public AgenticSearchTemplateService(
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ClusterService clusterService,
        ScriptService scriptService,
        NamedXContentRegistry xContentRegistry
    ) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.xContentRegistry = xContentRegistry;
    }

    // ---- Register (derive + validate + store) ------------------------------

    /**
     * Register a template for filling: derive its param-schema and store it.
     *
     * @param templateId the {@code _scripts} template name (also the doc id)
     * @param index the target index (for field-name enums)
     * @param description optional human description
     * @param user the caller, captured by the transport action before it stashed the
     *     thread context (reading it here would be too late — the stash below drops the
     *     security-user transient); may be null when security is disabled
     * @param listener yields the stored {@link AgenticSearchTemplate}
     */
    public void register(String templateId, String index, String description, User user, ActionListener<AgenticSearchTemplate> listener) {
        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<AgenticSearchTemplate> wrapped = ActionListener.runBefore(listener, ctx::restore);

            // 1. Fetch the stored Mustache body from core _scripts.
            fetchTemplateBody(templateId, ActionListener.wrap(body -> {
                // 2. Fetch the index mapping for field-name enums.
                fetchFlattenedMapping(index, ActionListener.wrap(mappingFields -> {
                    try {
                        // 3. Derive the schema: parse-tree for names/types/required,
                        // mapping for field-name enums (a param whose name is *_field
                        // or that targets a field can only choose an existing field).
                        Map<String, Object> paramSchema = deriveSchema(body, mappingFields);
                        // 4. Pre-flight validate: render all-filled + required-only.
                        preflightValidate(body, paramSchema);

                        Instant now = Instant.now();
                        AgenticSearchTemplate template = AgenticSearchTemplate
                            .builder()
                            .templateId(templateId)
                            .indexBinding(index)
                            .description(description)
                            .paramSchema(paramSchema)
                            .createdTime(now)
                            .lastUpdatedTime(now)
                            .createdBy(user != null ? user.getName() : null)
                            .build();

                        storeTemplate(template, ActionListener.wrap(ignored -> wrapped.onResponse(template), wrapped::onFailure));
                    } catch (Exception e) {
                        wrapped.onFailure(e);
                    }
                }, wrapped::onFailure));
            }, wrapped::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void storeTemplate(AgenticSearchTemplate template, ActionListener<Boolean> listener) {
        mlIndicesHandler.initMLIndexIfAbsent(MLIndex.AGENTIC_SEARCH_TEMPLATES, ActionListener.wrap(created -> {
            try {
                IndexRequest indexRequest = new IndexRequest(INDEX)
                    .id(template.getTemplateId())
                    .source(template.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.wrap(r -> {
                    log.info("Registered agentic search template: {}", template.getTemplateId());
                    listener.onResponse(true);
                }, listener::onFailure));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }, listener::onFailure));
    }

    // ---- Derivation helpers ------------------------------------------------

    /**
     * Combine the two automatic inputs into a param-schema: parse-tree structure
     * (names/types/required) plus mapping-derived field-name enums. A param named
     * like {@code sort_by}/{@code *_field} that selects a field is scoped to the
     * mapping's field names, so the model can only target an existing field.
     */
    Map<String, Object> deriveSchema(String body, List<String> mappingFields) {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive(body);
        if (mappingFields == null || mappingFields.isEmpty()) {
            return schema;
        }
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            String name = e.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) e.getValue();
            // Field-selector params (e.g. sort_by, *_field): scope them to the mapping's
            // field names so the model can only target an existing field. Only for
            // string-typed params that don't already carry an enum.
            if (targetsAField(name) && MustacheTemplateAnalyzer.TYPE_STRING.equals(spec.get(TYPE_KEY)) && !spec.containsKey(ENUM_KEY)) {
                spec.put(ENUM_KEY, new ArrayList<>(mappingFields));
                spec.put(SOURCE_KEY, "mapping");
            }
        }
        return schema;
    }

    /** Heuristic: a param that selects a field (name ends with _field/_by or is "field"). */
    private static boolean targetsAField(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.equals("field") || n.endsWith("_field") || n.endsWith("_by") || n.equals("sort_by");
    }

    /**
     * Render the body twice through the cluster's Mustache engine — once with every
     * param filled, once with only required params — and confirm each renders to
     * legal JSON. Exercises the optional-clause sections so a body that can't render
     * to legal DSL fails here, not on the first query.
     */
    void preflightValidate(String body, Map<String, Object> paramSchema) {
        Map<String, Object> allFilled = sampleParams(paramSchema, false);
        renderAndCheckJson(body, allFilled, "all-filled");
        Map<String, Object> requiredOnly = sampleParams(paramSchema, true);
        renderAndCheckJson(body, requiredOnly, "required-only");
    }

    private void renderAndCheckJson(String body, Map<String, Object> params, String label) {
        String rendered;
        try {
            Script script = new Script(ScriptType.INLINE, "mustache", body, Collections.emptyMap());
            TemplateScript.Factory factory = scriptService.compile(script, TemplateScript.CONTEXT);
            rendered = factory.newInstance(params).execute();
        } catch (Exception e) {
            throw new IllegalArgumentException("Template failed to render (" + label + "): " + e.getMessage(), e);
        }
        // JSON-legality only. Parsing as a search body would be stricter, but the
        // sampleValue placeholders aren't domain-valid ("x" for a sort order or a
        // boost_mode), so real templates would fail here on the placeholder, not the body.
        try (
            XContentParser parser = MediaTypeRegistry.JSON
                .xContent()
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, rendered)
        ) {
            parser.map();
        } catch (Exception e) {
            throw new IllegalArgumentException("Template rendered invalid JSON (" + label + "): " + e.getMessage(), e);
        }
    }

    /** Build a placeholder param set for pre-flight: sample values by type. */
    private static Map<String, Object> sampleParams(Map<String, Object> paramSchema, boolean requiredOnly) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : paramSchema.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) e.getValue();
            boolean required = Boolean.TRUE.equals(spec.get(MustacheTemplateAnalyzer.REQUIRED_KEY));
            if (requiredOnly && !required) {
                continue;
            }
            params.put(e.getKey(), sampleValue(spec));
        }
        return params;
    }

    private static Object sampleValue(Map<String, Object> spec) {
        Object enumValues = spec.get(ENUM_KEY);
        if (enumValues instanceof List && !((List<?>) enumValues).isEmpty()) {
            return ((List<?>) enumValues).get(0);
        }
        String type = String.valueOf(spec.get(TYPE_KEY));
        switch (type) {
            case MustacheTemplateAnalyzer.TYPE_NUMBER:
                return 1;
            case MustacheTemplateAnalyzer.TYPE_BOOLEAN:
                return true;
            case MustacheTemplateAnalyzer.TYPE_ARRAY:
                return "[]"; // triple-stache injects raw JSON; an empty array is legal
            default:
                return "x";
        }
    }

    // ---- _scripts + mapping fetch ------------------------------------------

    private void fetchTemplateBody(String templateId, ActionListener<String> listener) {
        GetStoredScriptRequest request = new GetStoredScriptRequest(templateId);
        client.admin().cluster().getStoredScript(request, ActionListener.wrap(response -> {
            if (response.getSource() == null || response.getSource().getSource() == null) {
                listener
                    .onFailure(
                        new OpenSearchStatusException("No stored search template found at _scripts/" + templateId, RestStatus.BAD_REQUEST)
                    );
                return;
            }
            listener.onResponse(response.getSource().getSource());
        }, listener::onFailure));
    }

    /** Fetch the index mapping and flatten it to the list of leaf field names. */
    private void fetchFlattenedMapping(String index, ActionListener<List<String>> listener) {
        GetIndexRequest request = new GetIndexRequest().indices(index).indicesOptions(IndicesOptions.strictExpand()).local(false);
        client.admin().indices().getIndex(request, ActionListener.wrap(response -> {
            try {
                listener.onResponse(extractFieldNames(response));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                listener.onFailure(new OpenSearchStatusException("Index does not exist: " + index, RestStatus.BAD_REQUEST));
            } else {
                listener.onFailure(e);
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractFieldNames(GetIndexResponse response) {
        Map<String, MappingMetadata> mappings = response.mappings();
        List<String> fields = new ArrayList<>();
        if (mappings == null || mappings.isEmpty()) {
            return fields;
        }
        MappingMetadata mapping = mappings.values().iterator().next();
        Map<String, Object> source = mapping.getSourceAsMap();
        Object props = source.get("properties");
        if (props instanceof Map) {
            collectFieldNames((Map<String, Object>) props, "", fields);
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void collectFieldNames(Map<String, Object> properties, String prefix, List<String> out) {
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            String name = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            out.add(name);
            Object value = e.getValue();
            if (value instanceof Map) {
                Map<String, Object> field = (Map<String, Object>) value;
                if (field.get("properties") instanceof Map) {
                    collectFieldNames((Map<String, Object>) field.get("properties"), name, out);
                }
                // Expose a text field's keyword sub-field (used for exact/sort).
                if (field.get("fields") instanceof Map) {
                    for (String sub : ((Map<String, Object>) field.get("fields")).keySet()) {
                        out.add(name + "." + sub);
                    }
                }
            }
        }
    }

    // ---- Get / List / Delete / Update --------------------------------------

    public void getTemplate(String templateId, ActionListener<AgenticSearchTemplate> listener) {
        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<AgenticSearchTemplate> wrapped = ActionListener.runBefore(listener, ctx::restore);
            client.get(new GetRequest(INDEX, templateId), ActionListener.wrap(response -> {
                if (!response.isExists()) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                    return;
                }
                try {
                    wrapped.onResponse(parse(response.getSourceAsBytesRef()));
                } catch (Exception e) {
                    wrapped.onFailure(e);
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                } else {
                    wrapped.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public void listTemplates(int from, int size, ActionListener<MLListResult> listener) {
        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLListResult> wrapped = ActionListener.runBefore(listener, ctx::restore);
            SearchRequest searchRequest = new SearchRequest(INDEX)
                .source(new SearchSourceBuilder().query(new MatchAllQueryBuilder()).from(from).size(size));
            client.search(searchRequest, ActionListener.wrap(response -> {
                try {
                    List<AgenticSearchTemplate> templates = new ArrayList<>();
                    for (SearchHit hit : response.getHits().getHits()) {
                        templates.add(parse(hit.getSourceRef()));
                    }
                    long total = response.getHits().getTotalHits() != null ? response.getHits().getTotalHits().value() : templates.size();
                    wrapped.onResponse(new MLListResult(templates, total));
                } catch (Exception e) {
                    wrapped.onFailure(e);
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrapped.onResponse(new MLListResult(new ArrayList<>(), 0));
                } else {
                    wrapped.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    public void deleteTemplate(String templateId, ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrapped = ActionListener.runBefore(listener, ctx::restore);
            DeleteRequest deleteRequest = new DeleteRequest(INDEX, templateId).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.delete(deleteRequest, ActionListener.wrap(response -> {
                boolean deleted = response.getResult() == DeleteResponse.Result.DELETED;
                if (!deleted) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                    return;
                }
                wrapped.onResponse(true);
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                } else {
                    wrapped.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Merge a partial schema edit into the stored doc (§4.5): only the fields the
     * customer sent are written, via a doc-merge update.
     *
     * <p>Validated like a registration, but against the <em>merged</em> schema, since a
     * per-param edit must not fail for params it didn't mention. An edit carrying no
     * params skips validation — nothing schema-shaped to check.
     */
    public void updateTemplate(String templateId, AgenticSearchTemplate patch, ActionListener<UpdateResponse> listener) {
        try (ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> wrapped = ActionListener.runBefore(listener, ctx::restore);
            if (patch.getParamSchema() == null || patch.getParamSchema().isEmpty()) {
                writeTemplatePatch(templateId, patch, wrapped);
                return;
            }
            // Validate the merged schema against the live body before persisting.
            client.get(new GetRequest(INDEX, templateId), ActionListener.wrap(response -> {
                if (!response.isExists()) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                    return;
                }
                final Map<String, Object> merged;
                try {
                    merged = mergeParamSchema(parse(response.getSourceAsBytesRef()).getParamSchema(), patch.getParamSchema());
                } catch (Exception e) {
                    wrapped.onFailure(e);
                    return;
                }
                fetchTemplateBody(templateId, ActionListener.wrap(body -> {
                    try {
                        validateParamSchema(merged);
                        preflightValidate(body, merged);
                    } catch (Exception e) {
                        wrapped.onFailure(new OpenSearchStatusException(e.getMessage(), RestStatus.BAD_REQUEST));
                        return;
                    }
                    writeTemplatePatch(templateId, patch, wrapped);
                }, wrapped::onFailure));
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                } else {
                    wrapped.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void writeTemplatePatch(String templateId, AgenticSearchTemplate patch, ActionListener<UpdateResponse> listener) {
        patch.setLastUpdatedTime(Instant.now());
        try {
            UpdateRequest updateRequest = new UpdateRequest(INDEX, templateId)
                .doc(patch.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.update(updateRequest, ActionListener.wrap(listener::onResponse, e -> {
                if (e instanceof org.opensearch.index.engine.DocumentMissingException || e instanceof IndexNotFoundException) {
                    listener.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                } else {
                    listener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Overlay a partial param-schema on the stored one, per param and per key, mirroring
     * the doc-merge update's own recursive merge so the schema validated is the schema
     * stored. An edit may not introduce a param absent from the stored schema: it could
     * never be filled, and signals drift from the template body.
     */
    static Map<String, Object> mergeParamSchema(Map<String, Object> stored, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(stored == null ? Collections.emptyMap() : stored);
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            String name = e.getKey();
            if (!(e.getValue() instanceof Map)) {
                throw new IllegalArgumentException("param '" + name + "' schema entry must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> patchSpec = (Map<String, Object>) e.getValue();
            Object storedSpec = merged.get(name);
            if (storedSpec == null) {
                throw new IllegalArgumentException(
                    "param '"
                        + name
                        + "' is not a parameter of template body; "
                        + "cannot add params that the Mustache body never references"
                );
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mergedSpec = new LinkedHashMap<>((Map<String, Object>) storedSpec);
            mergedSpec.putAll(patchSpec);
            merged.put(name, mergedSpec);
        }
        return merged;
    }

    /**
     * Check a param-schema is internally consistent. {@link #preflightValidate} renders
     * from {@link #sampleValue} placeholders, so it cannot see a self-contradictory
     * spec: an {@code enum} whose values don't fit the declared {@code type}, or an
     * empty {@code enum}, which the agent server rejects only when building its model.
     */
    static void validateParamSchema(Map<String, Object> paramSchema) {
        for (Map.Entry<String, Object> e : paramSchema.entrySet()) {
            String name = e.getKey();
            if (!(e.getValue() instanceof Map)) {
                throw new IllegalArgumentException("param '" + name + "' schema entry must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) e.getValue();
            Object type = spec.get(TYPE_KEY);
            if (!(type instanceof String) || ((String) type).isEmpty()) {
                throw new IllegalArgumentException("param '" + name + "' must declare a non-empty string 'type'");
            }
            Object required = spec.get(MustacheTemplateAnalyzer.REQUIRED_KEY);
            if (required != null && !(required instanceof Boolean)) {
                throw new IllegalArgumentException("param '" + name + "' has a non-boolean 'required'");
            }
            Object enumValues = spec.get(ENUM_KEY);
            if (enumValues == null) {
                continue;
            }
            if (!(enumValues instanceof List) || ((List<?>) enumValues).isEmpty()) {
                throw new IllegalArgumentException("param '" + name + "' has an empty or non-list 'enum'");
            }
            for (Object value : (List<?>) enumValues) {
                if (!valueFitsType(value, (String) type)) {
                    throw new IllegalArgumentException(
                        "param '" + name + "' enum value '" + value + "' does not fit declared type '" + type + "'"
                    );
                }
            }
        }
    }

    /**
     * Whether a schema-supplied value is usable for a param of {@code type}. An
     * {@code array} param is a triple-stache slot and must carry raw JSON as a string:
     * the Mustache engine stringifies a {@code List} instead of emitting JSON
     * ({@code [{"term":{"t":"a"}}]} becomes {@code {0={term={t=a}}}}).
     */
    private static boolean valueFitsType(Object value, String type) {
        if (value == null) {
            return false;
        }
        switch (type) {
            case MustacheTemplateAnalyzer.TYPE_NUMBER:
                return value instanceof Number;
            case MustacheTemplateAnalyzer.TYPE_BOOLEAN:
                return value instanceof Boolean;
            case MustacheTemplateAnalyzer.TYPE_ARRAY:
                return value instanceof String;
            default:
                return value instanceof String;
        }
    }

    private AgenticSearchTemplate parse(BytesReference source) throws Exception {
        XContentParser parser = MediaTypeRegistry.JSON
            .xContent()
            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, source.streamInput());
        return AgenticSearchTemplate.parse(parser);
    }

    /** Small carrier so the list transport action can build its paged response. */
    public static final class MLListResult {
        public final List<AgenticSearchTemplate> templates;
        public final long total;

        public MLListResult(List<AgenticSearchTemplate> templates, long total) {
            this.templates = templates;
            this.total = total;
        }
    }
}
