/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.*;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;

public class MemorySearchQueryBuilderNewMethodsTests {

    private MemoryConfiguration textEmbeddingConfig() {
        return MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-123")
            .dimension(1024)
            .build();
    }

    private MemoryConfiguration sparseEncodingConfig() {
        return MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-model-456")
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> semanticEmbeddingClause(QueryBuilder query, String queryType) {
        WrapperQueryBuilder wrapper = (WrapperQueryBuilder) ((BoolQueryBuilder) query).must().get(0);
        String json = new String(wrapper.source(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false);
        return (Map<String, Object>) ((Map<String, Object>) parsed.get(queryType)).get(MEMORY_EMBEDDING_FIELD);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> hybridEmbeddingClause(String query, String queryType) {
        Map<String, Object> parsed = XContentHelper.convertToMap(JsonXContent.jsonXContent, query, false);
        List<Object> queries = (List<Object>) ((Map<String, Object>) parsed.get("hybrid")).get("queries");
        Map<String, Object> neural = (Map<String, Object>) queries.get(1);
        return (Map<String, Object>) ((Map<String, Object>) neural.get(queryType)).get(MEMORY_EMBEDDING_FIELD);
    }

    // === buildSemanticSearchQuery tests ===

    @Test
    public void testBuildSemanticSearchQuery_TextEmbedding_IncludesK() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", textEmbeddingConfig(), null, 7);
        Map<String, Object> clause = semanticEmbeddingClause(query, "neural");
        assertEquals("test query", clause.get("query_text"));
        assertEquals("model-123", clause.get("model_id"));
        assertEquals(7, clause.get("k"));
    }

    @Test
    public void testBuildSemanticSearchQuery_SparseEncoding_OmitsK() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", sparseEncodingConfig(), null, 7);
        Map<String, Object> clause = semanticEmbeddingClause(query, "neural_sparse");
        assertEquals("test query", clause.get("query_text"));
        assertEquals("sparse-model-456", clause.get("model_id"));
        assertFalse("neural_sparse does not accept k", clause.containsKey("k"));
    }

    @Test
    public void testBuildSemanticSearchQuery_TextEmbedding() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", textEmbeddingConfig(), null, 10);
        assertNotNull(query);
        // bool query with wrapperQuery must + container filter
        String json = query.toString();
        assertTrue(json.contains("container-1"));
        assertTrue(json.contains("memory_container_id"));
    }

    @Test
    public void testBuildSemanticSearchQuery_SparseEncoding() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", sparseEncodingConfig(), null, 10);
        assertNotNull(query);
        String json = query.toString();
        assertTrue(json.contains("container-1"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithNamespace() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", Map.of("user_id", "alice"), null, null, "c1", textEmbeddingConfig(), null, 10);
        String json = query.toString();
        assertTrue(json.contains("namespace.user_id"));
        assertTrue(json.contains("alice"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithTags() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, Map.of("topic", "food"), null, "c1", textEmbeddingConfig(), null, 10);
        String json = query.toString();
        assertTrue(json.contains("tags.topic"));
        assertTrue(json.contains("food"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithOwner() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, null, "admin", "c1", textEmbeddingConfig(), null, 10);
        String json = query.toString();
        assertTrue(json.contains("owner_id"));
        assertTrue(json.contains("admin"));
    }

    @Test
    public void testBuildSemanticSearchQuery_AllFilters() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery(
                "test",
                Map.of("user_id", "bob"),
                Map.of("topic", "dev"),
                "admin",
                "c1",
                textEmbeddingConfig(),
                null,
                10
            );
        String json = query.toString();
        assertTrue(json.contains("namespace.user_id"));
        assertTrue(json.contains("tags.topic"));
        assertTrue(json.contains("owner_id"));
        assertTrue(json.contains("memory_container_id"));
    }

    // === buildPostFilter tests ===

    @Test
    public void testBuildPostFilter_WithNamespace() {
        QueryBuilder filter = MemorySearchQueryBuilder.buildPostFilter(Map.of("user_id", "alice"), null, null, "c1", null);
        String json = filter.toString();
        assertTrue(json.contains("namespace.user_id"));
        assertTrue(json.contains("alice"));
        assertTrue(json.contains("memory_container_id"));
    }

    @Test
    public void testBuildPostFilter_WithTags() {
        QueryBuilder filter = MemorySearchQueryBuilder.buildPostFilter(null, Map.of("topic", "food"), null, "c1", null);
        String json = filter.toString();
        assertTrue(json.contains("tags.topic"));
    }

    @Test
    public void testBuildPostFilter_AllFilters() {
        QueryBuilder filter = MemorySearchQueryBuilder
            .buildPostFilter(Map.of("user_id", "bob"), Map.of("topic", "dev"), "admin", "c1", null);
        String json = filter.toString();
        assertTrue(json.contains("namespace.user_id"));
        assertTrue(json.contains("tags.topic"));
        assertTrue(json.contains("owner_id"));
        assertTrue(json.contains("memory_container_id"));
    }

    // === buildHybridSearchQueryString tests ===

    @Test
    public void testBuildHybridSearchQueryString_TextEmbedding() {
        String query = MemorySearchQueryBuilder
            .buildHybridSearchQueryString("test query", textEmbeddingConfig(), 10, null, null, null, null, null);
        assertTrue(query.contains("hybrid"));
        assertTrue(query.contains("match"));
        assertTrue(query.contains("neural"));
        assertTrue(query.contains("memory"));
        assertTrue(query.contains("memory_embedding"));
        assertTrue(query.contains("model-123"));
        assertTrue(query.contains("test query"));
    }

    @Test
    public void testBuildHybridSearchQueryString_SparseEncoding() {
        String query = MemorySearchQueryBuilder
            .buildHybridSearchQueryString("test query", sparseEncodingConfig(), 10, null, null, null, null, null);
        assertTrue(query.contains("hybrid"));
        assertTrue(query.contains("match"));
        assertTrue(query.contains("neural_sparse"));
        assertTrue(query.contains("sparse-model-456"));
    }

    @Test
    public void testBuildHybridSearchQueryString_TextEmbedding_IncludesK() {
        String query = MemorySearchQueryBuilder
            .buildHybridSearchQueryString("test query", textEmbeddingConfig(), 7, null, null, null, "c1", null);
        Map<String, Object> clause = hybridEmbeddingClause(query, "neural");
        assertEquals("model-123", clause.get("model_id"));
        assertEquals(7, clause.get("k"));
    }

    @Test
    public void testBuildHybridSearchQueryString_SparseEncoding_OmitsK() {
        String query = MemorySearchQueryBuilder
            .buildHybridSearchQueryString("test query", sparseEncodingConfig(), 7, null, null, null, "c1", null);
        Map<String, Object> clause = hybridEmbeddingClause(query, "neural_sparse");
        assertEquals("sparse-model-456", clause.get("model_id"));
        assertFalse("neural_sparse does not accept k", clause.containsKey("k"));
    }

    @Test
    public void testBuildHybridSearchQueryString_EscapesSpecialChars() {
        String query = MemorySearchQueryBuilder
            .buildHybridSearchQueryString("test \"with quotes\"", textEmbeddingConfig(), 10, null, null, null, null, null);
        assertTrue(query.contains("test \\\"with quotes\\\""));
    }

    @Test
    public void testBuildSemanticSearchQuery_NullConfig_Throws() {
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildSemanticSearchQuery("test", null, null, null, "c1", null, null, 10)
        );
    }

    @Test
    public void testBuildSemanticSearchQuery_NullEmbeddingType_Throws() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildSemanticSearchQuery("test", null, null, null, "c1", config, null, 10)
        );
    }

    @Test
    public void testBuildHybridSearchQueryString_NullConfig_Throws() {
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildHybridSearchQueryString("test", null, 10, null, null, null, null, null)
        );
    }

    @Test
    public void testBuildHybridSearchQueryString_NullEmbeddingType_Throws() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildHybridSearchQueryString("test", config, 10, null, null, null, null, null)
        );
    }

    @Test
    public void testBuildSemanticSearchQuery_WithFilter() {
        QueryBuilder filter = QueryBuilders.termQuery("strategy_type", "SEMANTIC");
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, null, null, "c1", textEmbeddingConfig(), filter, 10);
        assertTrue(query.toString().contains("strategy_type"));
    }

    @Test
    public void testBuildPostFilter_WithFilter() {
        QueryBuilder filter = QueryBuilders.termQuery("strategy_type", "SEMANTIC");
        QueryBuilder result = MemorySearchQueryBuilder.buildPostFilter(null, null, null, "c1", filter);
        assertTrue(result.toString().contains("strategy_type"));
    }

    @Test
    public void testBuildPostFilter_NullFilter() {
        QueryBuilder result = MemorySearchQueryBuilder.buildPostFilter(null, null, null, "c1", null);
        assertNotNull(result);
        assertTrue(result.toString().contains("memory_container_id"));
    }

    @Test
    public void testAddFilters_NullOwnerAndContainer() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, null, null, null, textEmbeddingConfig(), null, 10);
        // Should not throw, just no owner/container filters
        assertNotNull(query);
    }
}
