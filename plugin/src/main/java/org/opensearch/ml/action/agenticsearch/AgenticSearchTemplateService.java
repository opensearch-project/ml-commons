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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest;
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
import org.opensearch.index.engine.VersionConflictEngineException;
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

    private static final String INDEX = CommonValue.ML_AGENTIC_SEARCH_TEMPLATES_INDEX;
    private static final String NOT_FOUND_ERROR = "Agentic search template not found: ";

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
     * Register a template for filling: derive or accept its param-schema and store it.
     *
     * @param templateId the {@code _scripts} template name (also the doc id)
     * @param index the target index (for field-name enums)
     * @param description optional human description; when null, the derive path assembles a
     *     template-level one from the body's recovered clauses (used for multi-template selection)
     * @param providedSchema a caller-supplied param-schema. When non-null it is validated
     *     and pre-flight rendered against the body, then stored without derivation. When
     *     null the schema is derived from the body and index mapping.
     * @param user the caller, resolved by the transport action; may be null when security is disabled
     * @param listener yields the stored {@link AgenticSearchTemplate}
     */
    public void register(
        String templateId,
        String index,
        String description,
        Map<String, Object> providedSchema,
        User user,
        ActionListener<AgenticSearchTemplate> listener
    ) {
        // Steps 1 and 2 read a stored script and an index mapping, which are the caller's resources rather
        // than the plugin's. They deliberately run in the caller's context so the security plugin authorizes
        // them against the caller's own permissions; stashing here would run them as the plugin and let a
        // caller read a script or mapping they cannot otherwise see. Only the system-index write in
        // storeTemplate needs the plugin's identity, so the stash is scoped to that.
        try {
            ActionListener<AgenticSearchTemplate> wrapped = listener;
            // 1. Fetch the stored Mustache body from core _scripts.
            fetchTemplateBody(templateId, ActionListener.wrap(body -> {
                // 2. Fetch the index mapping for field-name enums.
                fetchFlattenedMapping(index, ActionListener.wrap(mappingFields -> {
                    try {
                        Map<String, Object> paramSchema;
                        String derivedDescription = null;
                        if (providedSchema != null) {
                            // Caller-supplied schema: check it is internally consistent
                            // (types, enums) and references only params the body declares,
                            // then pre-flight render below. The schema is stored as sent.
                            validateParamSchema(providedSchema);
                            rejectUnknownParams(providedSchema, body);
                            paramSchema = providedSchema;
                        } else {
                            // 3. Derive the schema: parse-tree for names/types/required,
                            // mapping for field-name enums (a param whose name is *_field
                            // or that targets a field can only choose an existing field).
                            paramSchema = deriveSchema(body, mappingFields);
                            // Enrich the derived schema with descriptions and
                            // fixed-value enums recovered from where each param renders, and
                            // derive a template-level description for multi-template selection.
                            // Best-effort: on any failure the base derivation stands.
                            derivedDescription = enrichStructurally(body, paramSchema, mappingFields.sortable);
                        }
                        // 4. Pre-flight validate: render all-filled + required-only.
                        preflightValidate(body, paramSchema);

                        Instant now = Instant.now();
                        AgenticSearchTemplate template = AgenticSearchTemplate
                            .builder()
                            .templateId(templateId)
                            .indexBinding(index)
                            .description(description != null ? description : derivedDescription)
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

    /** Writes to the system index, so this is the one step that runs with the plugin's identity. */
    private void storeTemplate(AgenticSearchTemplate template, ActionListener<Boolean> outerListener) {
        ThreadContext.StoredContext ctx = client.threadPool().getThreadContext().stashContext();
        ActionListener<Boolean> listener = ActionListener.runBefore(outerListener, ctx::restore);
        try {
            storeTemplateInIndex(template, listener);
        } catch (Exception e) {
            // Restore the stashed context even if the write could not be dispatched at all.
            listener.onFailure(e);
        }
    }

    private void storeTemplateInIndex(AgenticSearchTemplate template, ActionListener<Boolean> listener) {
        mlIndicesHandler.initMLIndexIfAbsent(MLIndex.AGENTIC_SEARCH_TEMPLATES, ActionListener.wrap(created -> {
            try {
                // CREATE opType so a duplicate template id conflicts instead of overwriting.
                IndexRequest indexRequest = new IndexRequest(INDEX)
                    .id(template.getTemplateId())
                    .opType(DocWriteRequest.OpType.CREATE)
                    .source(template.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.wrap(r -> {
                    log.info("Registered agentic search template: {}", template.getTemplateId());
                    listener.onResponse(true);
                }, e -> {
                    if (e instanceof VersionConflictEngineException) {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "Agentic search template already exists: " + template.getTemplateId(),
                                    RestStatus.CONFLICT
                                )
                            );
                    } else {
                        listener.onFailure(e);
                    }
                }));
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
     * mapping's field names, so the model can only target an existing field. {@code sort_by}
     * is scoped to the sortable fields only; other selectors may also target fields that
     * cannot be sorted on (a {@code text} match field, a {@code knn_vector} neural target).
     */
    Map<String, Object> deriveSchema(String body, MappingFields mappingFields) {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive(body);
        if (mappingFields == null || mappingFields.all.isEmpty()) {
            return schema;
        }
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            String name = e.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) e.getValue();
            // Field-selector params (e.g. sort_by, *_field): scope them to the mapping's
            // field names so the model can only target an existing field. Only for
            // string-typed params that don't already carry an enum.
            if (targetsAField(name)
                && MustacheTemplateAnalyzer.TYPE_STRING.equals(spec.get(MustacheTemplateAnalyzer.TYPE_KEY))
                && !spec.containsKey(MustacheTemplateAnalyzer.ENUM_KEY)) {
                List<String> fields = isSortSelector(name) ? new ArrayList<>(mappingFields.sortable) : new ArrayList<>(mappingFields.all);
                // An empty enum fails validateParamSchema, so omit it when nothing qualifies.
                if (!fields.isEmpty()) {
                    spec.put(MustacheTemplateAnalyzer.ENUM_KEY, fields);
                    spec.put(MustacheTemplateAnalyzer.SOURCE_KEY, MustacheTemplateAnalyzer.SOURCE_MAPPING);
                }
            }
        }
        return schema;
    }

    /**
     * Enrich a derived schema in place with a description and a fixed-value enum per
     * param, recovered from where each param renders in the body (see
     * {@link TemplateStructureAnalyzer}). Best-effort: any failure, or a body that will not
     * render to parseable JSON, leaves the base derivation untouched. Only writes an empty
     * description and only adds an enum a param does not already carry. A field-name enum
     * from {@link #deriveSchema} is kept unless the recovered role contradicts it: a sort slot
     * narrows it to {@code sortable}, a value slot drops it.
     */
    String enrichStructurally(String body, Map<String, Object> schema, Set<String> sortable) {
        try {
            TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
            Map<String, Object> rendered = renderToMap(body, markers.renderParams());
            if (rendered == null) {
                return null;
            }
            // Render with optionals omitted so an optional param's slot shows the body's own
            // default; read that value at the param's (stable) path below.
            Map<String, Object> defaults = renderToMap(body, sampleParams(schema, true));
            applyStructuralEnrichment(schema, markers, rendered, defaults, sortable);
            // Derive a one-line template-level description (capabilities grouped by clause)
            // for multi-template selection. Null when no role is recovered.
            return TemplateStructureAnalyzer.describeTemplate(schema, markers, rendered);
        } catch (Exception e) {
            log.warn("Structural enrichment skipped: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Apply the facts recovered from the rendered trees to the schema in place. Split from
     * {@link #enrichStructurally} so the mutation logic is testable without a cluster: the
     * caller owns rendering, this owns locate + per-param enrichment.
     */
    void applyStructuralEnrichment(
        Map<String, Object> schema,
        TemplateStructureAnalyzer.MarkerSet markers,
        Map<String, Object> rendered,
        Map<String, Object> defaults,
        Set<String> sortable
    ) {
        Map<String, TemplateStructureAnalyzer.Located> located = TemplateStructureAnalyzer.locate(rendered, markers);
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            enrichParam(entry.getKey(), spec, located.get(entry.getKey()), rendered, defaults, sortable);
        }
    }

    /** Apply the recovered enum and description to one param, respecting values already set. */
    private void enrichParam(
        String param,
        Map<String, Object> spec,
        TemplateStructureAnalyzer.Located located,
        Map<String, Object> rendered,
        Map<String, Object> defaults,
        Set<String> sortable
    ) {
        Object existing = spec.get(MustacheTemplateAnalyzer.DESCRIPTION_KEY);
        boolean hasDescription = existing instanceof String && !((String) existing).isEmpty();
        String type = spec.get(MustacheTemplateAnalyzer.TYPE_KEY) instanceof String
            ? (String) spec.get(MustacheTemplateAnalyzer.TYPE_KEY)
            : MustacheTemplateAnalyzer.TYPE_STRING;

        // Array and boolean params carry no single clause or field, so describe them from
        // their type alone (no marker to locate).
        if (!hasDescription && MustacheTemplateAnalyzer.TYPE_ARRAY.equals(type)) {
            spec.put(MustacheTemplateAnalyzer.DESCRIPTION_KEY, "A JSON array or object passed as a raw JSON string.");
            return;
        }
        if (!hasDescription && MustacheTemplateAnalyzer.TYPE_BOOLEAN.equals(type)) {
            spec.put(MustacheTemplateAnalyzer.DESCRIPTION_KEY, "Set to true to enable the optional " + param + " clause.");
            return;
        }

        if (located == null) {
            return;
        }
        TemplateStructureAnalyzer.Facts facts = TemplateStructureAnalyzer.classify(located, rendered);
        reconcileMappingEnum(spec, facts.role, sortable);

        // A closed-vocabulary slot becomes an enum: only for a string param with no enum yet,
        // so a field-name enum from deriveSchema is left as is.
        List<String> vocab = TemplateStructureAnalyzer.vocabEnum(facts);
        if (vocab != null && MustacheTemplateAnalyzer.TYPE_STRING.equals(type) && !spec.containsKey(MustacheTemplateAnalyzer.ENUM_KEY)) {
            spec.put(MustacheTemplateAnalyzer.ENUM_KEY, new ArrayList<>(vocab));
        }

        if (hasDescription) {
            return;
        }
        Object defaultValue = null;
        if (Boolean.FALSE.equals(spec.get(MustacheTemplateAnalyzer.REQUIRED_KEY)) && located.isStablePath() && defaults != null) {
            defaultValue = TemplateStructureAnalyzer.valueAt(defaults, located.path);
        }
        String description = TemplateStructureAnalyzer.describe(facts, defaultValue);
        if (description != null) {
            spec.put(MustacheTemplateAnalyzer.DESCRIPTION_KEY, description);
        }
    }

    /**
     * Let the recovered role beat the name heuristic for a mapping-derived field-name enum
     * ({@code source=mapping}): a sort slot can only take a sortable field, so the enum is
     * narrowed to {@code sortable} (dropped when none is); a value slot takes free text or a
     * term, not a field name, so the enum is removed. No role leaves the enum as is.
     */
    private static void reconcileMappingEnum(Map<String, Object> spec, String role, Set<String> sortable) {
        if (role == null || !MustacheTemplateAnalyzer.SOURCE_MAPPING.equals(spec.get(MustacheTemplateAnalyzer.SOURCE_KEY))) {
            return;
        }
        if (TemplateStructureAnalyzer.ROLE_SORT_FIELD.equals(role)) {
            if (sortable == null || sortable.isEmpty()) {
                spec.remove(MustacheTemplateAnalyzer.ENUM_KEY);
                spec.remove(MustacheTemplateAnalyzer.SOURCE_KEY);
            } else {
                spec.put(MustacheTemplateAnalyzer.ENUM_KEY, new ArrayList<>(sortable));
            }
        } else if (VALUE_ROLES.contains(role)) {
            spec.remove(MustacheTemplateAnalyzer.ENUM_KEY);
            spec.remove(MustacheTemplateAnalyzer.SOURCE_KEY);
        }
    }

    /** Roles whose slot takes a query or filter value rather than a field name. */
    private static final Set<String> VALUE_ROLES = Set
        .of(
            TemplateStructureAnalyzer.ROLE_FULL_TEXT,
            TemplateStructureAnalyzer.ROLE_PHRASE,
            TemplateStructureAnalyzer.ROLE_FILTER_TERM,
            TemplateStructureAnalyzer.ROLE_FILTER_TERMS,
            TemplateStructureAnalyzer.ROLE_PATTERN,
            TemplateStructureAnalyzer.ROLE_FUZZY,
            TemplateStructureAnalyzer.ROLE_RANGE_BOUND
        );

    /** A field selector that picks the sort key, scoped to sortable fields only. */
    private static boolean isSortSelector(String name) {
        return name.toLowerCase(Locale.ROOT).equals("sort_by");
    }

    /** Heuristic: a param that selects a field (named "field", "sort_by", or ending in _field). */
    private static boolean targetsAField(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.equals("field") || n.equals("sort_by") || n.endsWith("_field");
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

    /**
     * Render the body with the given params and return the parsed JSON, or null on any
     * failure. Unlike {@link #renderAndCheckJson} this never throws: structural enrichment is
     * best-effort, so a body that will not render simply yields no enrichment.
     */
    private Map<String, Object> renderToMap(String body, Map<String, Object> params) {
        try {
            Script script = new Script(ScriptType.INLINE, "mustache", body, Collections.emptyMap());
            TemplateScript.Factory factory = scriptService.compile(script, TemplateScript.CONTEXT);
            String rendered = factory.newInstance(params).execute();
            try (
                XContentParser parser = MediaTypeRegistry.JSON
                    .xContent()
                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, rendered)
            ) {
                return parser.map();
            }
        } catch (Exception e) {
            log.debug("Structural render did not produce parseable JSON: {}", e.getMessage());
            return null;
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
        Object enumValues = spec.get(MustacheTemplateAnalyzer.ENUM_KEY);
        if (enumValues instanceof List && !((List<?>) enumValues).isEmpty()) {
            return ((List<?>) enumValues).get(0);
        }
        String type = String.valueOf(spec.get(MustacheTemplateAnalyzer.TYPE_KEY));
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

    /** Fetch the index mapping and flatten it to the leaf field names and their sortable subset. */
    private void fetchFlattenedMapping(String index, ActionListener<MappingFields> listener) {
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

    /**
     * The target's flattened mapping: every leaf field name, and the subset a {@code sort} can
     * use. Kept apart because only a sort selector is limited to sortable fields; a match or
     * neural field selector may legitimately target a field that cannot be sorted on.
     */
    static final class MappingFields {
        static final MappingFields EMPTY = new MappingFields(List.of(), Set.of());

        final List<String> all;      // ordered union of leaf field names across the target's indices
        final Set<String> sortable;  // fields sortable in every index of the target, in `all` order

        MappingFields(List<String> all, Set<String> sortable) {
            this.all = all;
            this.sortable = sortable;
        }
    }

    // Field types that sort via doc_values (text sorts only with fielddata:true instead).
    private static final Set<String> SORTABLE_TYPES = Set
        .of(
            "keyword",
            "long",
            "integer",
            "short",
            "byte",
            "double",
            "float",
            "half_float",
            "scaled_float",
            "unsigned_long",
            "date",
            "date_nanos",
            "boolean",
            "ip"
        );

    /**
     * Flatten every mapping of the target, not just the first: an alias or wildcard can span
     * several indices. Indices are visited in name order so the enum order is deterministic.
     * A field is sortable only if it is sortable in every index, since a sort on a field that
     * is missing or unsortable in any one index fails the search.
     */
    @SuppressWarnings("unchecked")
    static MappingFields extractFieldNames(GetIndexResponse response) {
        Map<String, MappingMetadata> mappings = response.mappings();
        if (mappings == null || mappings.isEmpty()) {
            return MappingFields.EMPTY;
        }
        Set<String> all = new LinkedHashSet<>();
        Set<String> sortableEverywhere = null;
        for (MappingMetadata mapping : new TreeMap<>(mappings).values()) {
            List<String> fields = new ArrayList<>();
            Set<String> sortable = new LinkedHashSet<>();
            Object props = mapping == null ? null : mapping.getSourceAsMap().get("properties");
            if (props instanceof Map) {
                collectFieldNames((Map<String, Object>) props, "", fields, sortable);
            }
            all.addAll(fields);
            if (sortableEverywhere == null) {
                sortableEverywhere = sortable;
            } else {
                sortableEverywhere.retainAll(sortable);
            }
        }
        Set<String> sortableInOrder = new LinkedHashSet<>();
        for (String field : all) {
            if (sortableEverywhere.contains(field)) {
                sortableInOrder.add(field);
            }
        }
        return new MappingFields(new ArrayList<>(all), sortableInOrder);
    }

    /**
     * Collect the mapping's leaf field names, skipping the {@code object}/{@code nested}
     * containers on the way down, and record which of them can be sorted on.
     *
     * <p>Only leaves are valid targets for a field-selector param: a container like
     * {@code spec} in {@code spec.os} cannot be sorted on or matched against, so
     * including it would let the model pick a field that fails at query time. An
     * {@code enabled:false} object is not indexed at all, so it is skipped with its children.
     *
     * <p>A field is sortable when its type is in {@link #SORTABLE_TYPES} with doc_values on,
     * or it is {@code text} with {@code fielddata:true}. Nothing under a {@code nested}
     * container is sortable from the root, and a sub-field is judged by its own type (so
     * {@code title.keyword} is sortable while {@code title} is not).
     */
    static void collectFieldNames(Map<String, Object> properties, String prefix, List<String> out, Set<String> sortable) {
        collectFieldNames(properties, prefix, out, sortable, false);
    }

    @SuppressWarnings("unchecked")
    private static void collectFieldNames(
        Map<String, Object> properties,
        String prefix,
        List<String> out,
        Set<String> sortable,
        boolean underNested
    ) {
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            String name = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (!(value instanceof Map)) {
                out.add(name);
                continue;
            }
            Map<String, Object> field = (Map<String, Object>) value;
            if (isFlag(field.get("enabled"), false)) {
                // Not indexed: neither the object nor anything under it can be queried or sorted.
                continue;
            }
            if (field.get("properties") instanceof Map) {
                // A container: recurse for its leaves and don't offer the container itself.
                boolean nested = underNested || "nested".equals(field.get("type"));
                collectFieldNames((Map<String, Object>) field.get("properties"), name, out, sortable, nested);
            } else {
                out.add(name);
                if (!underNested && isSortable(field)) {
                    sortable.add(name);
                }
            }
            // Expose a text field's keyword sub-field (used for exact/sort).
            if (field.get("fields") instanceof Map) {
                for (Map.Entry<String, Object> sub : ((Map<String, Object>) field.get("fields")).entrySet()) {
                    String subName = name + "." + sub.getKey();
                    out.add(subName);
                    if (!underNested && sub.getValue() instanceof Map && isSortable((Map<String, Object>) sub.getValue())) {
                        sortable.add(subName);
                    }
                }
            }
        }
    }

    private static boolean isSortable(Map<String, Object> field) {
        Object type = field.get("type");
        if ("text".equals(type)) {
            return isFlag(field.get("fielddata"), true);
        }
        return type instanceof String && SORTABLE_TYPES.contains(type) && !isFlag(field.get("doc_values"), false);
    }

    /** A mapping flag set to {@code expected}, given as a JSON boolean or its string form. */
    private static boolean isFlag(Object value, boolean expected) {
        return value != null && String.valueOf(expected).equals(String.valueOf(value));
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
                // No schema to validate, so no read is needed before the write.
                writeTemplatePatch(templateId, patch, null, null, wrapped);
                return;
            }
            // A schema edit is validated against the merge of the stored schema and the
            // patch, so gate the write on the read's seqNo/primaryTerm to avoid validating
            // against stale content and overwriting a concurrent edit.
            client.get(new GetRequest(INDEX, templateId), ActionListener.wrap(response -> {
                if (!response.isExists()) {
                    wrapped.onFailure(new OpenSearchStatusException(NOT_FOUND_ERROR + templateId, RestStatus.NOT_FOUND));
                    return;
                }
                final long seqNo = response.getSeqNo();
                final long primaryTerm = response.getPrimaryTerm();
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
                    writeTemplatePatch(templateId, patch, seqNo, primaryTerm, wrapped);
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

    /**
     * Persist a partial edit via a doc-merge update. When {@code seqNo} and
     * {@code primaryTerm} are non-null the write is gated on them so a concurrent
     * modification since the read fails with a version conflict; callers that do not read
     * first pass null for both.
     */
    private void writeTemplatePatch(
        String templateId,
        AgenticSearchTemplate patch,
        Long seqNo,
        Long primaryTerm,
        ActionListener<UpdateResponse> listener
    ) {
        patch.setLastUpdatedTime(Instant.now());
        try {
            UpdateRequest updateRequest = new UpdateRequest(INDEX, templateId)
                .doc(patch.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            if (seqNo != null && primaryTerm != null) {
                updateRequest.setIfSeqNo(seqNo).setIfPrimaryTerm(primaryTerm);
            }
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
     * Reject any param in a caller-supplied schema that the Mustache body does not
     * reference. Such a param can never be filled or rendered. This applies the same
     * guard {@link #mergeParamSchema} uses for a schema edit, so register and update
     * enforce the same contract.
     */
    void rejectUnknownParams(Map<String, Object> paramSchema, String body) {
        Map<String, Object> bodyParams = MustacheTemplateAnalyzer.derive(body);
        for (String name : paramSchema.keySet()) {
            if (!bodyParams.containsKey(name)) {
                throw new IllegalArgumentException(
                    "param '"
                        + name
                        + "' is not a parameter of template body; "
                        + "cannot register params that the Mustache body never references"
                );
            }
        }
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
            Object type = spec.get(MustacheTemplateAnalyzer.TYPE_KEY);
            if (!(type instanceof String) || ((String) type).isEmpty()) {
                throw new IllegalArgumentException("param '" + name + "' must declare a non-empty string 'type'");
            }
            Object required = spec.get(MustacheTemplateAnalyzer.REQUIRED_KEY);
            if (required != null && !(required instanceof Boolean)) {
                throw new IllegalArgumentException("param '" + name + "' has a non-boolean 'required'");
            }
            Object enumValues = spec.get(MustacheTemplateAnalyzer.ENUM_KEY);
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
        try (
            XContentParser parser = MediaTypeRegistry.JSON
                .xContent()
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, source.streamInput())
        ) {
            return AgenticSearchTemplate.parse(parser);
        }
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
