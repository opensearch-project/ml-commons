/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.BoolQueryBuilder;
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
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@Ignore
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class SearchModelITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private static final String PRE_BUILD_MODEL_URL =
        "https://artifacts.opensearch.org/models/ml-models/huggingface/sentence-transformers/all-MiniLM-L6-v2/1.0.1/torch_script/sentence-transformers_all-MiniLM-L6-v2-1.0.1-torch_script.zip";

    private String modelGroupId;

    private static final String CHUNK_NUMBER = "chunk_number";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        registerModelGroup();
    }

    private void registerModelGroup() throws InterruptedException {
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
        registerModelVersion();
    }

    private void registerModelVersion() throws InterruptedException {
        final MLModelConfig modelConfig = new TextEmbeddingModelConfig(
            "distilbert",
            768,
            TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS,
            null,
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
            .hashValue("c15f0d2e62d872be5b5bc6c84d2e0f4921541e29fefbef51d59cc10a8ae30e0f")
            .description("mock model desc")
            .build();
        MLRegisterModelRequest registerModelRequest = new MLRegisterModelRequest(input);
        client().execute(MLRegisterModelAction.INSTANCE, registerModelRequest).actionGet();
        Thread.sleep(30000);
    }

    /**
     * The reason to use one method instead of using different methods is because of the mechanism of OpenSearchIntegTestCase,
     * for each test method in the test class, after the running the cluster will clear all the data created in the cluster by
     * the method, so if we use multiple methods, then we always need to wait a long time until the model version registration
     * completes, making all the tests in one method can make the overall process faster.
     */

    public void test_all() {
        test_empty_body_search();
        test_matchAll_search();
        test_bool_search();
        test_term_search();
        test_terms_search();
        test_range_search();
        test_matchPhrase_search();
    }

    private void test_empty_body_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER)));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_matchAll_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest
            .source()
            .query(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER)).must(QueryBuilders.matchAllQuery()));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_bool_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        searchRequest
            .source()
            .query(
                QueryBuilders
                    .boolQuery()
                    .must(
                        QueryBuilders
                            .boolQuery()
                            .mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER))
                            .must(QueryBuilders.termQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt"))
                    )
            );
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_term_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER))
            .must(QueryBuilders.termQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt"));
        searchRequest.source().query(boolQueryBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_terms_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER))
            .must(QueryBuilders.termsQuery("name.keyword", "msmarco-distilbert-base-tas-b-pt", "test_model_group_name"));
        searchRequest.source().query(boolQueryBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_range_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER))
            .must(QueryBuilders.rangeQuery("created_time").gte("now-1d"));
        searchRequest.source().query(boolQueryBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    private void test_matchPhrase_search() {
        SearchRequest searchRequest = new SearchRequest(".plugins-ml-model");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.source(searchSourceBuilder);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery(CHUNK_NUMBER))
            .must(QueryBuilders.matchPhraseQuery("description", "desc"));
        searchRequest.source().query(boolQueryBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        SearchResponse response = client().execute(MLModelSearchAction.INSTANCE, mlSearchActionRequest).actionGet();
        assertEquals(1, response.getHits().getTotalHits().value());
    }

    @Override
    protected Settings nodeSettings(int ordinal) {
        return Settings.builder().put(super.nodeSettings(ordinal)).put(ML_COMMONS_ALLOW_MODEL_URL.getKey(), true).build();
    }

}
