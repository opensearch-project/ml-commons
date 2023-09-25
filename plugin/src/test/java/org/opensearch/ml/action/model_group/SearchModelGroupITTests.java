/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.Map;

import org.apache.lucene.search.join.ScoreMode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.*;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.model.ModelGroupTag;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class SearchModelGroupITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        registerModelGroup();
    }

    private void registerModelGroup() {
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput(
            "mock_model_group_name",
            "mock model group desc",
            null,
            null,
            null,
            List.of(new ModelGroupTag("tag1", "String"), new ModelGroupTag("tag2", "Number"))
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        MLRegisterModelGroupResponse response = client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
        this.modelGroupId = response.getModelGroupId();
    }

    public void test_empty_body_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_matchAll_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchAllQuery());
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());

        List<Map<String, Object>> tags = (List<Map<String, Object>>) response.getHits().getHits()[0].getSourceAsMap().get("tags");
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).get("key"));
        assertEquals("String", tags.get(0).get("type"));
        assertEquals("tag2", tags.get(1).get("key"));
        assertEquals("Number", tags.get(1).get("type"));
    }

    public void test_match_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery("tags.key", "tag1");
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder("tags", matchQueryBuilder, ScoreMode.None);
        searchRequest.source().query(nestedQueryBuilder);

        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());

        List<Map<String, Object>> tags = (List<Map<String, Object>>) response.getHits().getHits()[0].getSourceAsMap().get("tags");
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).get("key"));
        assertEquals("String", tags.get(0).get("type"));
        assertEquals("tag2", tags.get(1).get("key"));
        assertEquals("Number", tags.get(1).get("type"));
    }

    public void test_bool_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("name.keyword", "mock_model_group_name")));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_term_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name.keyword", "mock_model_group_name"));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_term_search_tags() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery("tags.key", "tag1");
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder("tags", termQueryBuilder, ScoreMode.None);
        searchRequest.source().query(nestedQueryBuilder);

        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());

        List<Map<String, Object>> tags = (List<Map<String, Object>>) response.getHits().getHits()[0].getSourceAsMap().get("tags");
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).get("key"));
        assertEquals("String", tags.get(0).get("type"));
        assertEquals("tag2", tags.get(1).get("key"));
        assertEquals("Number", tags.get(1).get("type"));
    }

    public void test_terms_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termsQuery("name.keyword", "mock_model_group_name", "test_model_group_name"));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_terms_search_tags() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        TermsQueryBuilder termsQueryBuilder = QueryBuilders.termsQuery("tags.key", "tag1");
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder("tags", termsQueryBuilder, ScoreMode.None);
        searchRequest.source().query(nestedQueryBuilder);

        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());

        List<Map<String, Object>> tags = (List<Map<String, Object>>) response.getHits().getHits()[0].getSourceAsMap().get("tags");
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).get("key"));
        assertEquals("String", tags.get(0).get("type"));
        assertEquals("tag2", tags.get(1).get("key"));
        assertEquals("Number", tags.get(1).get("type"));
    }

    public void test_range_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.rangeQuery("created_time").gte("now-1d"));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_matchPhrase_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchPhraseQuery("description", "desc"));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    public void test_queryString_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.queryStringQuery("name: mock_model_group_*"));
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }
}
