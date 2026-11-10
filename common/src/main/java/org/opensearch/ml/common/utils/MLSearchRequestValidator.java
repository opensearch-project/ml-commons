/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.search.BooleanClause;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoostingQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilderVisitor;
import org.opensearch.index.query.TemplateQueryBuilder;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregatorFactories;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.opensearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.opensearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.opensearch.search.fetch.StoredFieldsContext;
import org.opensearch.search.fetch.subphase.FieldAndFormat;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.rescore.QueryRescorerBuilder;
import org.opensearch.search.rescore.RescorerBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.NestedSortBuilder;
import org.opensearch.search.sort.ScoreSortBuilder;
import org.opensearch.search.sort.SortBuilder;

/**
 * Validates caller-supplied search requests on ML Commons search APIs.
 *
 * <p>These APIs apply their result filtering (owner, backend roles, memory container, conversation,
 * hidden flag) through {@code query()}. This class checks that a request only uses search features
 * that operate on the documents {@code query()} matches, and rejects anything else with a 400 before
 * the API runs the search.
 *
 * <p>Top-level fields are checked against an allowlist ({@link #ALLOWED_SEARCH_SOURCE_FIELDS}), so a
 * search feature added to OpenSearch later is rejected until it has been reviewed. Within the allowed
 * fields:
 * <ul>
 *   <li>{@code aggregations}: rejected, or limited to types that only aggregate the documents the
 *   query matches ({@link AggregationPolicy}).</li>
 *   <li>{@code sort}: field and {@code _score} sorts only.</li>
 *   <li>{@code inner_hits} (on a {@code nested} query or in {@code collapse}), the {@code wrapper} and
 *   {@code template} queries, and {@code has_child} / {@code has_parent}: rejected in every query in the
 *   request. They are found by walking the parsed queries, not by field name, so a script parameter or
 *   aggregation that happens to use one of these names is left alone.</li>
 *   <li>Fields the API excludes from {@code _source}, such as the connector credential, cannot be
 *   returned through {@code fields}, {@code docvalue_fields}, {@code stored_fields},
 *   {@code highlight}, field {@code sort}, {@code collapse} or aggregation fields, including through
 *   wildcard patterns that could match them.</li>
 * </ul>
 * On the request itself, a search pipeline and {@code scroll} are rejected.
 *
 * <p>{@code query}, {@code post_filter} and {@code rescore} are otherwise not inspected: a query
 * only scores or narrows the documents the API's filter selects. {@code explain} and {@code profile} are not
 * on the allowlist because their output describes the rewritten query, and a multi-term query (for
 * example {@code prefix} or {@code wildcard}) is rewritten using terms from the whole index, not only
 * from the documents the API's filter selects.
 */
public final class MLSearchRequestValidator {

    /**
     * Top-level search request fields these APIs accept. Each one either selects, orders or pages the
     * documents the query matches, or controls how those documents are returned. Any other field is
     * rejected.
     */
    static final Set<String> ALLOWED_SEARCH_SOURCE_FIELDS = Set
        .of(
            "query",
            "post_filter",
            "from",
            "size",
            "timeout",
            "terminate_after",
            "min_score",
            "version",
            "seq_no_primary_term",
            "_source",
            "stored_fields",
            "docvalue_fields",
            "fields",
            "sort",
            "track_scores",
            "include_named_queries_score",
            "track_total_hits",
            "search_after",
            "aggregations",
            "highlight",
            "rescore",
            "collapse"
        );

    /**
     * Aggregation types allowed under {@link AggregationPolicy#QUERY_SCOPED}. Each one only reads the
     * documents matched by the query. {@code global}, {@code top_hits}, {@code significant_*},
     * pipeline aggregations and any type not listed here are rejected.
     */
    static final Set<String> QUERY_SCOPED_AGGREGATION_TYPES = Set
        .of(
            "terms",
            "cardinality",
            "value_count",
            "min",
            "max",
            "avg",
            "sum",
            "stats",
            "filter",
            "filters",
            "composite",
            "histogram",
            "date_histogram",
            "range",
            "date_range",
            "missing"
        );

    /** Protected fields for connector search: the encrypted connector credential. */
    public static final List<String> CONNECTOR_PROTECTED_FIELDS = List.of(HttpConnector.CREDENTIAL_FIELD);

    /** Protected fields for model search: the credential of a model's inline connector. */
    public static final List<String> MODEL_PROTECTED_FIELDS = List.of(MLModel.CONNECTOR_FIELD + "." + HttpConnector.CREDENTIAL_FIELD);

    /** How a search API treats caller-supplied aggregations. */
    public enum AggregationPolicy {
        /** Reject any aggregation. */
        REJECT,
        /**
         * Allow only aggregation types that are bounded by the query, without scripts, without
         * {@code min_doc_count: 0} on {@code terms}, and without reading a protected field.
         * Safe only for APIs whose per-document access filter is applied in {@code query()}.
         */
        QUERY_SCOPED
    }

    private MLSearchRequestValidator() {}

    /**
     * Validates a search request and reports a rejection to the listener.
     *
     * @return true if the request is valid; false if it was rejected, in which case
     *         {@code listener.onFailure} has been called
     */
    public static boolean validate(SearchRequest request, AggregationPolicy aggregationPolicy, ActionListener<?> listener) {
        return validate(request, aggregationPolicy, List.of(), listener);
    }

    /**
     * Validates a search request and reports a rejection to the listener.
     *
     * @param protectedFields fields the API hides from {@code _source}; the request must not return them
     *        through fetch, highlight, sort, collapse or aggregation options
     * @return true if the request is valid; false if it was rejected, in which case
     *         {@code listener.onFailure} has been called
     */
    public static boolean validate(
        SearchRequest request,
        AggregationPolicy aggregationPolicy,
        Collection<String> protectedFields,
        ActionListener<?> listener
    ) {
        try {
            validate(request, aggregationPolicy, protectedFields);
            return true;
        } catch (OpenSearchStatusException e) {
            listener.onFailure(e);
            return false;
        }
    }

    /**
     * Validates a search source and reports a rejection to the listener.
     *
     * @return true if the source is valid; false if it was rejected, in which case
     *         {@code listener.onFailure} has been called
     */
    public static boolean validate(SearchSourceBuilder source, AggregationPolicy aggregationPolicy, ActionListener<?> listener) {
        try {
            validate(source, aggregationPolicy, List.of());
            return true;
        } catch (OpenSearchStatusException e) {
            listener.onFailure(e);
            return false;
        }
    }

    /**
     * Validates a search request whose query will be rewritten for access control.
     *
     * @param request the caller's search request; a null request or source is accepted
     * @param aggregationPolicy how to treat aggregations
     * @param protectedFields fields the API hides from {@code _source}; the request must not return them
     *        through fetch, highlight, sort, collapse or aggregation options
     * @throws OpenSearchStatusException with {@link RestStatus#BAD_REQUEST} if the request uses a
     *         feature that is not supported by this API or reads a protected field
     */
    public static void validate(SearchRequest request, AggregationPolicy aggregationPolicy, Collection<String> protectedFields) {
        if (request == null) {
            return;
        }
        if (request.pipeline() != null) {
            throw badRequest("search_pipeline");
        }
        if (request.scroll() != null) {
            throw badRequest("scroll");
        }
        validate(request.source(), aggregationPolicy, protectedFields);
    }

    /**
     * Validates a search source whose query will be rewritten for access control.
     *
     * @param source the caller's search source; null is accepted
     * @param aggregationPolicy how to treat aggregations
     * @param protectedFields fields the API hides from {@code _source}; the source must not return them
     *        through fetch, highlight, sort, collapse or aggregation options
     * @throws OpenSearchStatusException with {@link RestStatus#BAD_REQUEST} if the source uses a
     *         feature that is not supported by this API or reads a protected field
     */
    public static void validate(SearchSourceBuilder source, AggregationPolicy aggregationPolicy, Collection<String> protectedFields) {
        if (source == null) {
            return;
        }
        validateTopLevelFields(source);
        validateQueryFeatures(source);
        if (source.sorts() != null) {
            for (SortBuilder<?> sort : source.sorts()) {
                if (sort instanceof FieldSortBuilder fieldSort) {
                    checkField("sort", fieldSort.getFieldName(), protectedFields);
                } else if (!(sort instanceof ScoreSortBuilder)) {
                    throw new OpenSearchStatusException(
                        "[sort] type [" + sort.getWriteableName() + "] is not supported in this search API",
                        RestStatus.BAD_REQUEST
                    );
                }
            }
        }
        validateProtectedFields(source, protectedFields);
        validateAggregations(source.aggregations(), aggregationPolicy, protectedFields);
    }

    /**
     * Rejects any top-level search source field outside {@link #ALLOWED_SEARCH_SOURCE_FIELDS}. The set of
     * fields is read from the source's own XContent form, so a field that a later OpenSearch version
     * adds to the search request is rejected until it is reviewed and added to the allowlist.
     */
    private static void validateTopLevelFields(SearchSourceBuilder source) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            source.toXContent(builder, ToXContent.EMPTY_PARAMS);
            try (
                XContentParser parser = XContentHelper
                    .createParser(
                        NamedXContentRegistry.EMPTY,
                        DeprecationHandler.IGNORE_DEPRECATIONS,
                        BytesReference.bytes(builder),
                        MediaTypeRegistry.JSON
                    )
            ) {
                if (parser.nextToken() != XContentParser.Token.START_OBJECT) {
                    throw new IOException("expected an object");
                }
                while (parser.nextToken() == XContentParser.Token.FIELD_NAME) {
                    String field = parser.currentName();
                    if (!ALLOWED_SEARCH_SOURCE_FIELDS.contains(field)) {
                        throw badRequest(field);
                    }
                    parser.nextToken();
                    parser.skipChildren();
                }
            }
        } catch (OpenSearchStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Fail closed: a source that cannot be read back cannot be checked.
            throw new OpenSearchStatusException(
                "Invalid search request: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()),
                RestStatus.BAD_REQUEST,
                e
            );
        }
    }

    /** Query types that this class identifies by name because their builder classes live in other modules. */
    private static final Set<String> REJECTED_QUERY_NAMES = Set.of("has_child", "has_parent");

    /**
     * Rejects query features whose output is not limited to the documents the API's filter selects:
     * {@code inner_hits} (a separate fetch with its own options, such as {@code explain}), the
     * {@code wrapper} and {@code template} queries (their content is parsed into a query only after this
     * check runs), and {@code has_child} / {@code has_parent}. Every query in the request is walked
     * structurally ({@code query}, {@code post_filter}, {@code rescore}, {@code filter} and
     * {@code filters} aggregations, highlight queries and nested sort filters), so these are matched only
     * where they are real query clauses, not where the same names appear as a script parameter or an
     * aggregation name.
     */
    private static void validateQueryFeatures(SearchSourceBuilder source) {
        // Each query is checked once. Core visit() for script_score and boosting hands its child to this
        // visitor again after the explicit walk below, so without this set a chain of such queries would
        // be walked a number of times that grows exponentially with its depth.
        Set<QueryBuilder> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        QueryBuilderVisitor visitor = new QueryBuilderVisitor() {
            @Override
            public void accept(QueryBuilder queryBuilder) {
                if (queryBuilder == null || !visited.add(queryBuilder)) {
                    return;
                }
                if (queryBuilder instanceof WrapperQueryBuilder
                    || queryBuilder instanceof TemplateQueryBuilder
                    || REJECTED_QUERY_NAMES.contains(queryBuilder.getWriteableName())) {
                    throw badRequest(queryBuilder.getWriteableName());
                }
                if (queryBuilder instanceof NestedQueryBuilder nested && nested.innerHit() != null) {
                    throw badRequest("inner_hits");
                }
                // The core visit() implementations of these queries do not descend into all of their
                // child queries, so walk those children here.
                if (queryBuilder instanceof ScriptScoreQueryBuilder scriptScore) {
                    visitQuery(scriptScore.query(), this);
                } else if (queryBuilder instanceof BoostingQueryBuilder boosting) {
                    visitQuery(boosting.positiveQuery(), this);
                    visitQuery(boosting.negativeQuery(), this);
                } else if (queryBuilder instanceof FunctionScoreQueryBuilder functionScore) {
                    for (FunctionScoreQueryBuilder.FilterFunctionBuilder function : functionScore.filterFunctionBuilders()) {
                        visitQuery(function.getFilter(), this);
                    }
                }
            }

            @Override
            public QueryBuilderVisitor getChildVisitor(BooleanClause.Occur occur) {
                return this;
            }
        };
        visitQuery(source.query(), visitor);
        visitQuery(source.postFilter(), visitor);
        if (source.rescores() != null) {
            for (RescorerBuilder<?> rescorer : source.rescores()) {
                if (rescorer instanceof QueryRescorerBuilder queryRescorer) {
                    visitQuery(queryRescorer.getRescoreQuery(), visitor);
                } else {
                    throw new OpenSearchStatusException(
                        "[rescore] type [" + rescorer.getWriteableName() + "] is not supported in this search API",
                        RestStatus.BAD_REQUEST
                    );
                }
            }
        }
        HighlightBuilder highlighter = source.highlighter();
        if (highlighter != null) {
            visitQuery(highlighter.highlightQuery(), visitor);
            for (HighlightBuilder.Field field : highlighter.fields()) {
                visitQuery(field.highlightQuery(), visitor);
            }
        }
        if (source.sorts() != null) {
            for (SortBuilder<?> sort : source.sorts()) {
                if (sort instanceof FieldSortBuilder fieldSort) {
                    visitQuery(deprecatedNestedFilter(fieldSort), visitor);
                    for (NestedSortBuilder nestedSort = fieldSort.getNestedSort(); nestedSort != null; nestedSort = nestedSort
                        .getNestedSort()) {
                        visitQuery(nestedSort.getFilter(), visitor);
                    }
                }
            }
        }
        if (source.aggregations() != null) {
            visitAggregationQueries(source.aggregations().getAggregatorFactories(), visitor);
        }
        CollapseBuilder collapse = source.collapse();
        if (collapse != null && collapse.getInnerHits() != null && !collapse.getInnerHits().isEmpty()) {
            throw badRequest("inner_hits");
        }
    }

    @SuppressWarnings("deprecation")
    private static QueryBuilder deprecatedNestedFilter(FieldSortBuilder sort) {
        // nested_filter is deprecated in favour of nested.filter but is still parsed.
        return sort.getNestedFilter();
    }

    private static void visitQuery(QueryBuilder query, QueryBuilderVisitor visitor) {
        if (query != null) {
            query.visit(visitor);
        }
    }

    private static void visitAggregationQueries(Collection<AggregationBuilder> aggregations, QueryBuilderVisitor visitor) {
        if (aggregations == null) {
            return;
        }
        for (AggregationBuilder aggregation : aggregations) {
            if (aggregation instanceof FilterAggregationBuilder filter) {
                visitQuery(filter.getFilter(), visitor);
            } else if (aggregation instanceof FiltersAggregationBuilder filters) {
                for (FiltersAggregator.KeyedFilter keyedFilter : filters.filters()) {
                    visitQuery(keyedFilter.filter(), visitor);
                }
            }
            visitAggregationQueries(aggregation.getSubAggregations(), visitor);
        }
    }

    private static void validateProtectedFields(SearchSourceBuilder source, Collection<String> protectedFields) {
        if (protectedFields.isEmpty()) {
            return;
        }
        checkFieldAndFormats("fields", source.fetchFields(), protectedFields);
        checkFieldAndFormats("docvalue_fields", source.docValueFields(), protectedFields);
        StoredFieldsContext storedFields = source.storedFields();
        if (storedFields != null && storedFields.fieldNames() != null) {
            for (String field : storedFields.fieldNames()) {
                checkField("stored_fields", field, protectedFields);
            }
        }
        HighlightBuilder highlighter = source.highlighter();
        if (highlighter != null) {
            for (HighlightBuilder.Field field : highlighter.fields()) {
                checkField("highlight", field.name(), protectedFields);
            }
        }
        CollapseBuilder collapse = source.collapse();
        if (collapse != null) {
            checkField("collapse", collapse.getField(), protectedFields);
        }
    }

    private static void checkFieldAndFormats(String feature, List<FieldAndFormat> fields, Collection<String> protectedFields) {
        if (fields == null) {
            return;
        }
        for (FieldAndFormat field : fields) {
            checkField(feature, field.field, protectedFields);
        }
    }

    private static void checkField(String feature, String field, Collection<String> protectedFields) {
        if (matchesProtectedField(field, protectedFields)) {
            throw new OpenSearchStatusException(
                "[" + feature + "] cannot read field [" + field + "] in this search API",
                RestStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Returns true if a field name or {@code *} pattern could name a protected field or one of its
     * sub-fields. A pattern matches if the text before its first {@code *} is compatible with a
     * protected field, which errs on the side of rejecting.
     */
    static boolean matchesProtectedField(String field, Collection<String> protectedFields) {
        if (field == null) {
            return false;
        }
        String name = field.toLowerCase(Locale.ROOT);
        int wildcard = name.indexOf('*');
        for (String protectedField : protectedFields) {
            String prefix = protectedField.toLowerCase(Locale.ROOT);
            if (wildcard < 0) {
                if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                    return true;
                }
            } else {
                String literal = name.substring(0, wildcard);
                if (prefix.startsWith(literal) || literal.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateAggregations(
        AggregatorFactories.Builder aggregations,
        AggregationPolicy aggregationPolicy,
        Collection<String> protectedFields
    ) {
        if (aggregations == null || aggregations.count() == 0) {
            return;
        }
        if (aggregationPolicy != AggregationPolicy.QUERY_SCOPED) {
            throw badRequest("aggs");
        }
        if (!aggregations.getPipelineAggregatorFactories().isEmpty()) {
            throw badAggregation("pipeline aggregations are not supported");
        }
        validateQueryScoped(aggregations.getAggregatorFactories(), protectedFields);
    }

    private static void validateQueryScoped(Collection<AggregationBuilder> aggregations, Collection<String> protectedFields) {
        for (AggregationBuilder aggregation : aggregations) {
            String type = aggregation.getType();
            if (!QUERY_SCOPED_AGGREGATION_TYPES.contains(type)) {
                throw badAggregation("aggregation type [" + type + "] is not supported");
            }
            if (aggregation instanceof TermsAggregationBuilder terms && terms.minDocCount() < 1) {
                throw badAggregation("[terms] aggregation requires min_doc_count >= 1");
            }
            if (aggregation instanceof ValuesSourceAggregationBuilder<?> valuesSource) {
                validateValuesSource(type, valuesSource.field(), valuesSource.script() != null, protectedFields);
            }
            if (aggregation instanceof CompositeAggregationBuilder composite) {
                for (CompositeValuesSourceBuilder<?> source : composite.sources()) {
                    validateValuesSource(type, source.field(), source.script() != null, protectedFields);
                }
            }
            if (!aggregation.getPipelineAggregations().isEmpty()) {
                throw badAggregation("pipeline aggregations are not supported");
            }
            validateQueryScoped(aggregation.getSubAggregations(), protectedFields);
        }
    }

    private static void validateValuesSource(String type, String field, boolean hasScript, Collection<String> protectedFields) {
        if (hasScript) {
            throw badAggregation("scripts are not supported in [" + type + "] aggregation");
        }
        if (matchesProtectedField(field, protectedFields)) {
            throw badAggregation("field [" + field + "] cannot be aggregated");
        }
    }

    private static OpenSearchStatusException badRequest(String feature) {
        return new OpenSearchStatusException("[" + feature + "] is not supported in this search API", RestStatus.BAD_REQUEST);
    }

    private static OpenSearchStatusException badAggregation(String reason) {
        return new OpenSearchStatusException("Unsupported aggregation in this search API: " + reason, RestStatus.BAD_REQUEST);
    }
}
