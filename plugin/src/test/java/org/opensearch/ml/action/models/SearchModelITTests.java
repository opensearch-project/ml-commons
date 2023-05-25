/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class SearchModelITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private static final String PRE_BUILD_MODEL_URL =
        "https://artifacts.opensearch.org/models/ml-models/huggingface/sentence-transformers/msmarco-distilbert-base-tas-b/1.0.1/torch_script/sentence-transformers_msmarco-distilbert-base-tas-b-1.0.1-torch_script.zip";

    private String modelGroupId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        registerModelGroup();
    }

    private void registerModelGroup() throws InterruptedException {
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput("mock_model_group_name", "mock model group desc", null, null, null);
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        MLRegisterModelGroupResponse response = client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
        this.modelGroupId = response.getModelGroupId();
        System.out.println("#########################model group id is: " + this.modelGroupId);
        registerModelVersion();
    }

    private void registerModelVersion() throws InterruptedException {
        final MLModelConfig modelConfig = new TextEmbeddingModelConfig(
            "distilbert",
            768,
            TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS,
            null,
            null,
            false,
            768
        );
        MLRegisterModelInput input = MLRegisterModelInput
            .builder()
            .modelName("msmarco-distilbert-base-tas-b-pt")
            .modelGroupId(modelGroupId)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .url(PRE_BUILD_MODEL_URL)
            .hashValue("c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8")
            .description("mock model desc")
            .build();
        MLRegisterModelRequest registerModelRequest = new MLRegisterModelRequest(input);
        client().execute(MLRegisterModelAction.INSTANCE, registerModelRequest).actionGet();
        Thread.sleep(10000);
    }

    public void test_empty_body_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_matchAll_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchAllQuery());
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_bool_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest
            .source()
            .query(QueryBuilders.boolQuery().must(QueryBuilders.termQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt")));
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_term_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt"));
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_terms_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termsQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt", "test_model_group_name"));
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_range_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.rangeQuery("created_time").gte("now-1d"));
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void test_matchPhrase_search() {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.matchPhraseQuery("description", "desc"));
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, searchRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

}
