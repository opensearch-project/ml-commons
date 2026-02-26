/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1, supportsDedicatedMasters = false)
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
            null
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        MLRegisterModelGroupResponse response = client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
        this.modelGroupId = response.getModelGroupId();
    }

    @Test
    public void test_empty_body_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_matchAll_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchAllQuery());
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_bool_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("name.keyword", "mock_model_group_name")));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_term_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name.keyword", "mock_model_group_name"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_terms_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termsQuery("name.keyword", "mock_model_group_name", "test_model_group_name"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_range_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.rangeQuery("created_time").gte("now-1d"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_matchPhrase_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchPhraseQuery("description", "desc"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

    @Test
    public void test_queryString_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.queryStringQuery("name: mock_model_group_*"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelGroupSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
        assertEquals(modelGroupId, response.getHits().getHits()[0].getId());
    }

}
