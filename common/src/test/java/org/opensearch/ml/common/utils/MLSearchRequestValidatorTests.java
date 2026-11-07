/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.utils.MLSearchRequestValidator.AggregationPolicy.QUERY_SCOPED;
import static org.opensearch.ml.common.utils.MLSearchRequestValidator.AggregationPolicy.REJECT;
import static org.opensearch.ml.common.utils.MLSearchRequestValidator.CONNECTOR_PROTECTED_FIELDS;
import static org.opensearch.ml.common.utils.MLSearchRequestValidator.MODEL_PROTECTED_FIELDS;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.join.ScoreMode;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.InnerHitBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TemplateQueryBuilder;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchExtBuilder;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.PipelineAggregatorBuilders;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.opensearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.builder.PointInTimeBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.rescore.QueryRescorerBuilder;
import org.opensearch.search.slice.SliceBuilder;
import org.opensearch.search.sort.NestedSortBuilder;
import org.opensearch.search.sort.ScriptSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.suggest.SuggestBuilder;
import org.opensearch.search.suggest.SuggestBuilders;

public class MLSearchRequestValidatorTests {

    private static SearchSourceBuilder source() {
        return new SearchSourceBuilder().query(QueryBuilders.matchAllQuery());
    }

    private static String rejectionMessage(SearchSourceBuilder source, MLSearchRequestValidator.AggregationPolicy policy) {
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(source, policy, MODEL_PROTECTED_FIELDS)
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
        return e.getMessage();
    }

    @Test
    public void nullRequestAndSourceAreAccepted() {
        MLSearchRequestValidator.validate((SearchRequest) null, REJECT, List.of());
        MLSearchRequestValidator.validate((SearchSourceBuilder) null, REJECT, List.of());
        MLSearchRequestValidator.validate(new SearchRequest(), REJECT, CONNECTOR_PROTECTED_FIELDS);
    }

    @Test
    public void plainSearchIsAccepted() {
        SearchSourceBuilder source = source()
            .from(10)
            .size(5)
            .sort("created_time")
            .postFilter(QueryBuilders.termQuery("name", "a"))
            .fetchSource(new String[] { "name" }, null)
            .trackTotalHits(true)
            .searchAfter(new Object[] { 1L });
        MLSearchRequestValidator.validate(new SearchRequest().source(source), REJECT, CONNECTOR_PROTECTED_FIELDS);
        MLSearchRequestValidator.validate(new SearchRequest().source(source), QUERY_SCOPED, MODEL_PROTECTED_FIELDS);
    }

    @Test
    public void globalTopHitsAggregationIsRejected() {
        SearchSourceBuilder source = source()
            .aggregation(AggregationBuilders.global("all").subAggregation(AggregationBuilders.topHits("docs").size(100)));
        assertTrue(rejectionMessage(source, REJECT).contains("[aggs]"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[global]"));
    }

    @Test
    public void anyAggregationIsRejectedUnderRejectPolicy() {
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.terms("names").field("name.keyword"));
        assertTrue(rejectionMessage(source, REJECT).contains("[aggs]"));
    }

    @Test
    public void dashboardsConnectorNameTermsAggregationIsAcceptedUnderQueryScopedPolicy() {
        // Request sent by ml-commons-dashboards ConnectorService.getUniqueInternalConnectorNames
        SearchSourceBuilder source = new SearchSourceBuilder()
            .size(0)
            .aggregation(AggregationBuilders.terms("unique_connector_names").field("connector.name.keyword").size(10000));
        MLSearchRequestValidator.validate(source, QUERY_SCOPED, MODEL_PROTECTED_FIELDS);
    }

    @Test
    public void nestedQueryScopedAggregationsAreAccepted() {
        SearchSourceBuilder source = source()
            .aggregation(
                AggregationBuilders
                    .filter("deployed", QueryBuilders.termQuery("model_state", "DEPLOYED"))
                    .subAggregation(
                        AggregationBuilders
                            .terms("algo")
                            .field("algorithm")
                            .subAggregation(AggregationBuilders.max("m").field("created_time"))
                    )
            )
            .aggregation(AggregationBuilders.cardinality("c").field("model_group_id"))
            .aggregation(new CompositeAggregationBuilder("comp", List.of(new TermsValuesSourceBuilder("algo").field("algorithm"))));
        MLSearchRequestValidator.validate(source, QUERY_SCOPED, MODEL_PROTECTED_FIELDS);
    }

    @Test
    public void topHitsIsRejectedEvenWhenNestedUnderAnAllowedAggregation() {
        SearchSourceBuilder source = source()
            .aggregation(AggregationBuilders.terms("algo").field("algorithm").subAggregation(AggregationBuilders.topHits("docs")));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[top_hits]"));
    }

    @Test
    public void significantTermsIsRejected() {
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.significantTerms("s").field("algorithm"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[significant_terms]"));
    }

    @Test
    public void termsWithZeroMinDocCountIsRejected() {
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.terms("t").field("name.keyword").minDocCount(0));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("min_doc_count"));
    }

    @Test
    public void aggregationScriptIsRejected() {
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.terms("t").script(new Script("params._source.name")));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("scripts"));
    }

    @Test
    public void compositeSourceScriptIsRejected() {
        SearchSourceBuilder source = source()
            .aggregation(
                new CompositeAggregationBuilder("comp", List.of(new TermsValuesSourceBuilder("s").script(new Script("doc['x'].value"))))
            );
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("scripts"));
    }

    @Test
    public void connectorCredentialFieldCannotBeAggregated() {
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.terms("t").field("connector.credential.secret_key"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("connector.credential.secret_key"));

        SearchSourceBuilder composite = source()
            .aggregation(
                new CompositeAggregationBuilder("comp", List.of(new TermsValuesSourceBuilder("s").field("Connector.Credential.access_key")))
            );
        assertTrue(rejectionMessage(composite, QUERY_SCOPED).contains("cannot be aggregated"));
    }

    @Test
    public void pipelineAggregationsAreRejected() {
        SearchSourceBuilder topLevel = source()
            .aggregation(AggregationBuilders.terms("t").field("algorithm"))
            .aggregation(PipelineAggregatorBuilders.maxBucket("mb", "t>_count"));
        assertTrue(rejectionMessage(topLevel, QUERY_SCOPED).contains("pipeline aggregations"));

        SearchSourceBuilder nested = source()
            .aggregation(
                AggregationBuilders
                    .dateHistogram("h")
                    .field("created_time")
                    .fixedInterval(DateHistogramInterval.DAY)
                    .subAggregation(PipelineAggregatorBuilders.cumulativeSum("cs", "_count"))
            );
        assertTrue(rejectionMessage(nested, QUERY_SCOPED).contains("pipeline aggregations"));
    }

    @Test
    public void suggestIsRejected() {
        SearchSourceBuilder source = source()
            .suggest(new SuggestBuilder().addSuggestion("s", SuggestBuilders.termSuggestion("name").text("a")));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[suggest]"));
    }

    @Test
    public void pointInTimeIsRejected() {
        SearchSourceBuilder source = source().pointInTimeBuilder(new PointInTimeBuilder("pit-id"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[pit]"));
    }

    @Test
    public void searchPipelinesAreRejected() {
        assertTrue(rejectionMessage(source().pipeline("my_pipeline"), QUERY_SCOPED).contains("[search_pipeline]"));
        assertTrue(
            rejectionMessage(source().searchPipelineSource(Map.of("request_processors", List.of())), QUERY_SCOPED)
                .contains("[search_pipeline]")
        );

        SearchRequest request = new SearchRequest().source(source());
        request.pipeline("my_pipeline");
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(request, REJECT, List.of())
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
        assertTrue(e.getMessage().contains("[search_pipeline]"));
    }

    @Test
    public void scriptFieldsAreRejected() {
        SearchSourceBuilder source = source().scriptField("f", new Script("doc['name.keyword'].value"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[script_fields]"));
    }

    @Test
    public void derivedFieldsAreRejected() {
        SearchSourceBuilder source = source().derivedField("f", "keyword", new Script("emit(doc['name.keyword'].value)"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[derived]"));
    }

    @Test
    public void listenerVariantReportsRejection() {
        @SuppressWarnings("unchecked")
        ActionListener<Object> listener = mock(ActionListener.class);
        SearchSourceBuilder source = source().aggregation(AggregationBuilders.global("all"));

        assertFalse(MLSearchRequestValidator.validate(source, REJECT, listener));
        assertFalse(MLSearchRequestValidator.validate(new SearchRequest().source(source), REJECT, listener));
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(2)).onFailure(captor.capture());
        for (Exception e : captor.getAllValues()) {
            assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) e).status());
        }
    }

    @Test
    public void listenerVariantPassesValidRequest() {
        @SuppressWarnings("unchecked")
        ActionListener<Object> listener = mock(ActionListener.class);
        assertTrue(MLSearchRequestValidator.validate(source(), REJECT, listener));
        assertTrue(MLSearchRequestValidator.validate(new SearchRequest().source(source()), REJECT, listener));
        verify(listener, never()).onFailure(any());
    }

    @Test
    public void protectedFieldMatchingHandlesSubFieldsAndWildcards() {
        List<String> model = MODEL_PROTECTED_FIELDS;
        assertTrue(MLSearchRequestValidator.matchesProtectedField("connector.credential", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("connector.credential.secret_key", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("Connector.Credential.access_key", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("*", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("connector.*", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("conn*", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("connector.credential.sec*", model));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("*.secret_key", model));
        assertFalse(MLSearchRequestValidator.matchesProtectedField("connector.name.keyword", model));
        assertFalse(MLSearchRequestValidator.matchesProtectedField("connector.credentials_count", model));
        assertFalse(MLSearchRequestValidator.matchesProtectedField("name*", model));
        assertFalse(MLSearchRequestValidator.matchesProtectedField(null, model));

        assertTrue(MLSearchRequestValidator.matchesProtectedField("credential", CONNECTOR_PROTECTED_FIELDS));
        assertTrue(MLSearchRequestValidator.matchesProtectedField("cred*", CONNECTOR_PROTECTED_FIELDS));
        assertFalse(MLSearchRequestValidator.matchesProtectedField("name", CONNECTOR_PROTECTED_FIELDS));
        assertFalse(MLSearchRequestValidator.matchesProtectedField("*", List.of()));
    }

    @Test
    public void protectedFieldsCannotBeReadOutsideSource() {
        assertTrue(rejectionMessage(source().fetchField("connector.credential.secret_key"), REJECT).contains("[fields]"));
        assertTrue(rejectionMessage(source().fetchField("*"), REJECT).contains("[fields]"));
        assertTrue(rejectionMessage(source().docValueField("connector.credential.*"), REJECT).contains("[docvalue_fields]"));
        assertTrue(rejectionMessage(source().storedFields(List.of("connector.credential")), REJECT).contains("[stored_fields]"));
        assertTrue(rejectionMessage(source().highlighter(new HighlightBuilder().field("connector.*")), REJECT).contains("[highlight]"));
        assertTrue(rejectionMessage(source().sort("connector.credential.secret_key"), REJECT).contains("[sort]"));
        assertTrue(
            rejectionMessage(source().collapse(new CollapseBuilder("connector.credential.secret_key")), REJECT).contains("[collapse]")
        );
    }

    @Test
    public void unprotectedFieldsCanBeRead() {
        SearchSourceBuilder source = source()
            .fetchField("connector.name")
            .docValueField("created_time")
            .storedFields(List.of("_id"))
            .highlighter(new HighlightBuilder().field("name"))
            .sort("created_time")
            .collapse(new CollapseBuilder("model_group_id"));
        MLSearchRequestValidator.validate(source, QUERY_SCOPED, MODEL_PROTECTED_FIELDS);
        // With no protected fields, a wildcard is fine (e.g. memory search)
        MLSearchRequestValidator.validate(source().fetchField("*"), REJECT, List.of());
    }

    @Test
    public void collapseInnerHitsAreRejected() {
        SearchSourceBuilder source = source().collapse(new CollapseBuilder("protocol").setInnerHits(new InnerHitBuilder("x")));
        assertTrue(rejectionMessage(source, REJECT).contains("[inner_hits]"));
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(source, REJECT, List.of())
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
    }

    @Test
    public void onlyFieldAndScoreSortsAreAllowed() {
        MLSearchRequestValidator.validate(source().sort("created_time").sort(SortBuilders.scoreSort()), REJECT, List.of());
        SearchSourceBuilder scriptSort = source()
            .sort(SortBuilders.scriptSort(new Script("doc['name.keyword'].value"), ScriptSortBuilder.ScriptSortType.STRING));
        assertTrue(rejectionMessage(scriptSort, REJECT).contains("_script"));
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(scriptSort, REJECT, List.of())
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
    }

    @Test
    public void fieldsOutsideTheAllowlistAreRejected() {
        assertTrue(rejectionMessage(source().ext(List.of(new TestSearchExt())), REJECT).contains("[ext]"));
        assertTrue(rejectionMessage(source().profile(true), REJECT).contains("[profile]"));
        assertTrue(rejectionMessage(source().slice(new SliceBuilder(0, 2)), REJECT).contains("[slice]"));
        assertTrue(rejectionMessage(source().indexBoost(".plugins-ml-model", 2.0f), REJECT).contains("[indices_boost]"));
        assertTrue(rejectionMessage(source().stats(List.of("group")), REJECT).contains("[stats]"));
        assertTrue(rejectionMessage(source().verbosePipeline(true), REJECT).contains("[verbose_pipeline]"));
    }

    @Test
    public void explainIsRejected() {
        SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.prefixQuery("name", "").rewrite("constant_score_boolean"))
            .explain(true);
        // Same query shape as the request in the review comment; the rejection is on explain alone.
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[explain]"));
        assertTrue(rejectionMessage(source().explain(false), REJECT).contains("[explain]"));
    }

    @Test
    public void innerHitsInQueriesAreRejected() {
        InnerHitBuilder explainInnerHits = new InnerHitBuilder().setExplain(true);
        SearchSourceBuilder nested = new SearchSourceBuilder()
            .query(
                QueryBuilders.nestedQuery("owner", QueryBuilders.prefixQuery("owner.name", ""), ScoreMode.None).innerHit(explainInnerHits)
            );
        assertTrue(rejectionMessage(nested, REJECT).contains("[inner_hits]"));

        SearchSourceBuilder inBool = new SearchSourceBuilder()
            .query(
                QueryBuilders
                    .boolQuery()
                    .filter(
                        QueryBuilders.nestedQuery("owner", QueryBuilders.matchAllQuery(), ScoreMode.None).innerHit(new InnerHitBuilder())
                    )
            );
        assertTrue(rejectionMessage(inBool, QUERY_SCOPED).contains("[inner_hits]"));

        SearchSourceBuilder inPostFilter = source()
            .postFilter(QueryBuilders.nestedQuery("owner", QueryBuilders.matchAllQuery(), ScoreMode.None).innerHit(new InnerHitBuilder()));
        assertTrue(rejectionMessage(inPostFilter, REJECT).contains("[inner_hits]"));

        // A nested query without inner_hits is still accepted.
        MLSearchRequestValidator
            .validate(
                new SearchSourceBuilder().query(QueryBuilders.nestedQuery("owner", QueryBuilders.matchAllQuery(), ScoreMode.None)),
                REJECT,
                List.of()
            );
    }

    @Test
    public void wrapperQueryIsRejected() {
        SearchSourceBuilder source = new SearchSourceBuilder().query(QueryBuilders.wrapperQuery("{\"match_all\":{}}"));
        assertTrue(rejectionMessage(source, QUERY_SCOPED).contains("[wrapper]"));
    }

    @Test
    public void scriptParamsAndAggregationNamesAreNotTreatedAsQueryFeatures() {
        // A script parameter named like a rejected query feature is data, not a wrapper/inner_hits query.
        SearchSourceBuilder scriptParam = new SearchSourceBuilder()
            .query(
                QueryBuilders
                    .scriptScoreQuery(
                        QueryBuilders.matchAllQuery(),
                        new Script(ScriptType.INLINE, "painless", "_score * params.wrapper", Map.of("wrapper", 1))
                    )
            );
        MLSearchRequestValidator.validate(scriptParam, QUERY_SCOPED, List.of());

        // An aggregation named like a rejected query feature is accepted (its type is allowlisted).
        SearchSourceBuilder aggName = new SearchSourceBuilder()
            .size(0)
            .aggregation(AggregationBuilders.terms("inner_hits").field("application_type"));
        MLSearchRequestValidator.validate(aggName, QUERY_SCOPED, List.of());

        // A field or named-query value equal to a rejected key is not a key either.
        MLSearchRequestValidator
            .validate(
                source()
                    .postFilter(QueryBuilders.termQuery("name", "inner_hits"))
                    .query(QueryBuilders.matchAllQuery().queryName("wrapper")),
                REJECT,
                List.of()
            );

        // A wrapper query inside a rescore is still rejected (structural, under every section).
        SearchSourceBuilder inRescore = source().addRescorer(new QueryRescorerBuilder(QueryBuilders.wrapperQuery("{\"match_all\":{}}")));
        assertTrue(rejectionMessage(inRescore, REJECT).contains("[wrapper]"));
    }

    @Test
    public void queryFeaturesUnderShallowVisitQueriesAreRejected() {
        QueryBuilder nestedInnerHits = QueryBuilders
            .nestedQuery("owner", QueryBuilders.matchAllQuery(), ScoreMode.None)
            .innerHit(new InnerHitBuilder().setExplain(true));
        QueryBuilder wrapper = QueryBuilders.wrapperQuery("{\"match_all\":{}}");

        SearchSourceBuilder scriptScore = new SearchSourceBuilder()
            .query(QueryBuilders.scriptScoreQuery(QueryBuilders.boolQuery().filter(nestedInnerHits), new Script("1")));
        assertTrue(rejectionMessage(scriptScore, REJECT).contains("[inner_hits]"));

        SearchSourceBuilder boosting = new SearchSourceBuilder()
            .query(QueryBuilders.boostingQuery(QueryBuilders.boolQuery().must(wrapper), QueryBuilders.matchAllQuery()));
        assertTrue(rejectionMessage(boosting, REJECT).contains("[wrapper]"));

        SearchSourceBuilder functionFilter = new SearchSourceBuilder()
            .query(
                QueryBuilders
                    .functionScoreQuery(
                        QueryBuilders.matchAllQuery(),
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(wrapper, ScoreFunctionBuilders.weightFactorFunction(1f)) }
                    )
            );
        assertTrue(rejectionMessage(functionFilter, REJECT).contains("[wrapper]"));
    }

    @Test(timeout = 10000)
    public void deeplyNestedShallowVisitQueriesAreWalkedOnce() {
        QueryBuilder query = QueryBuilders.matchAllQuery();
        for (int i = 0; i < 200; i++) {
            query = i % 2 == 0
                ? QueryBuilders.scriptScoreQuery(query, new Script("1"))
                : QueryBuilders.boostingQuery(query, QueryBuilders.matchAllQuery());
        }
        MLSearchRequestValidator.validate(new SearchSourceBuilder().query(query), REJECT, List.of());

        QueryBuilder withWrapper = QueryBuilders.wrapperQuery("{\"match_all\":{}}");
        for (int i = 0; i < 200; i++) {
            withWrapper = QueryBuilders.scriptScoreQuery(withWrapper, new Script("1"));
        }
        assertTrue(rejectionMessage(new SearchSourceBuilder().query(withWrapper), REJECT).contains("[wrapper]"));
    }

    @Test
    public void templateAndJoinQueriesAreRejected() {
        SearchSourceBuilder template = new SearchSourceBuilder().query(new TemplateQueryBuilder(Map.of("match_all", Map.of())));
        assertTrue(rejectionMessage(template, QUERY_SCOPED).contains("[template]"));

        SearchSourceBuilder hasChild = new SearchSourceBuilder().query(QueryBuilders.boolQuery().filter(new NamedQuery("has_child")));
        assertTrue(rejectionMessage(hasChild, REJECT).contains("[has_child]"));
        SearchSourceBuilder hasParent = new SearchSourceBuilder().query(new NamedQuery("has_parent"));
        assertTrue(rejectionMessage(hasParent, REJECT).contains("[has_parent]"));
    }

    @Test
    public void queriesOutsideQueryAreWalked() {
        QueryBuilder wrapper = QueryBuilders.wrapperQuery("{\"match_all\":{}}");
        SearchSourceBuilder filterAgg = new SearchSourceBuilder()
            .aggregation(AggregationBuilders.terms("t").field("algorithm").subAggregation(AggregationBuilders.filter("f", wrapper)));
        assertTrue(rejectionMessage(filterAgg, QUERY_SCOPED).contains("[wrapper]"));

        SearchSourceBuilder filtersAgg = new SearchSourceBuilder()
            .aggregation(AggregationBuilders.filters("f", new FiltersAggregator.KeyedFilter("k", wrapper)));
        assertTrue(rejectionMessage(filtersAgg, QUERY_SCOPED).contains("[wrapper]"));

        SearchSourceBuilder highlightQuery = source().highlighter(new HighlightBuilder().field("name").highlightQuery(wrapper));
        assertTrue(rejectionMessage(highlightQuery, REJECT).contains("[wrapper]"));

        SearchSourceBuilder fieldHighlightQuery = source()
            .highlighter(new HighlightBuilder().field(new HighlightBuilder.Field("name").highlightQuery(wrapper)));
        assertTrue(rejectionMessage(fieldHighlightQuery, REJECT).contains("[wrapper]"));

        @SuppressWarnings("deprecation")
        SearchSourceBuilder legacyNestedFilter = source()
            .sort(SortBuilders.fieldSort("owner.name").setNestedPath("owner").setNestedFilter(wrapper));
        assertTrue(rejectionMessage(legacyNestedFilter, REJECT).contains("[wrapper]"));

        SearchSourceBuilder nestedSortFilter = source()
            .sort(SortBuilders.fieldSort("owner.name").setNestedSort(new NestedSortBuilder("owner").setFilter(wrapper)));
        assertTrue(rejectionMessage(nestedSortFilter, REJECT).contains("[wrapper]"));
    }

    @Test
    public void scrollIsRejected() {
        SearchRequest request = new SearchRequest().source(source()).scroll(TimeValue.timeValueMinutes(1));
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(request, REJECT, List.of())
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
        assertTrue(e.getMessage().contains("[scroll]"));
    }

    @Test
    public void everyAllowedFieldIsAccepted() throws IOException {
        // One request that sets every allowlisted top-level field, parsed the way the REST layer parses it.
        String json = "{"
            + "\"query\":{\"match_all\":{}},"
            + "\"post_filter\":{\"term\":{\"name\":\"a\"}},"
            + "\"from\":1,\"size\":2,\"timeout\":\"5s\",\"terminate_after\":10,\"min_score\":0.1,"
            + "\"version\":true,\"seq_no_primary_term\":true,"
            + "\"_source\":[\"name\"],\"stored_fields\":[\"_id\"],\"docvalue_fields\":[\"created_time\"],"
            + "\"fields\":[\"name\"],\"sort\":[{\"created_time\":\"desc\"},\"_score\"],\"track_scores\":true,"
            + "\"include_named_queries_score\":true,\"track_total_hits\":true,\"search_after\":[1,2],"
            + "\"aggregations\":{\"t\":{\"terms\":{\"field\":\"algorithm\"}}},"
            + "\"highlight\":{\"fields\":{\"name\":{}}},"
            + "\"rescore\":{\"query\":{\"rescore_query\":{\"match_all\":{}}}},"
            + "\"collapse\":{\"field\":\"model_group_id\"}"
            + "}";
        SearchSourceBuilder source;
        try (
            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                    new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of()).getNamedXContents()),
                    DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                    json
                )
        ) {
            source = SearchSourceBuilder.fromXContent(parser);
        }
        MLSearchRequestValidator.validate(source, QUERY_SCOPED, MODEL_PROTECTED_FIELDS);
    }

    @Test
    public void sourceThatCannotBeSerializedIsRejected() {
        SearchSourceBuilder source = source().ext(List.of(new FailingSearchExt()));
        OpenSearchStatusException e = assertThrows(
            OpenSearchStatusException.class,
            () -> MLSearchRequestValidator.validate(source, REJECT, List.of())
        );
        assertEquals(RestStatus.BAD_REQUEST, e.status());
        assertTrue(e.getMessage().startsWith("Invalid search request"));
        assertTrue(e.getCause() instanceof IOException);
    }

    private static class FailingSearchExt extends TestSearchExt {
        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            throw new IOException("cannot serialize");
        }
    }

    /** A query whose only distinguishing property is its name, standing in for query types from other modules. */
    private static class NamedQuery extends MatchAllQueryBuilder {
        private final String name;

        NamedQuery(String name) {
            this.name = name;
        }

        @Override
        public String getWriteableName() {
            return name;
        }
    }

    private static class TestSearchExt extends SearchExtBuilder {
        @Override
        public String getWriteableName() {
            return "test_ext";
        }

        @Override
        public void writeTo(StreamOutput out) {}

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.field("test_ext", "x");
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestSearchExt;
        }
    }

}
