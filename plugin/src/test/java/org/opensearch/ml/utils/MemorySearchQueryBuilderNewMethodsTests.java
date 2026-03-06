/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
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

    // === buildSemanticSearchQuery tests ===

    @Test
    public void testBuildSemanticSearchQuery_TextEmbedding() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", textEmbeddingConfig(), null);
        assertNotNull(query);
        // bool query with wrapperQuery must + container filter
        String json = query.toString();
        assertTrue(json.contains("container-1"));
        assertTrue(json.contains("memory_container_id"));
    }

    @Test
    public void testBuildSemanticSearchQuery_SparseEncoding() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test query", null, null, null, "container-1", sparseEncodingConfig(), null);
        assertNotNull(query);
        String json = query.toString();
        assertTrue(json.contains("container-1"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithNamespace() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", Map.of("user_id", "alice"), null, null, "c1", textEmbeddingConfig(), null);
        String json = query.toString();
        assertTrue(json.contains("namespace.user_id"));
        assertTrue(json.contains("alice"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithTags() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, Map.of("topic", "food"), null, "c1", textEmbeddingConfig(), null);
        String json = query.toString();
        assertTrue(json.contains("tags.topic"));
        assertTrue(json.contains("food"));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithOwner() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, null, "admin", "c1", textEmbeddingConfig(), null);
        String json = query.toString();
        assertTrue(json.contains("owner_id"));
        assertTrue(json.contains("admin"));
    }

    @Test
    public void testBuildSemanticSearchQuery_AllFilters() {
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", Map.of("user_id", "bob"), Map.of("topic", "dev"), "admin", "c1", textEmbeddingConfig(), null);
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
        String query = MemorySearchQueryBuilder.buildHybridSearchQueryString("test query", textEmbeddingConfig());
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
        String query = MemorySearchQueryBuilder.buildHybridSearchQueryString("test query", sparseEncodingConfig());
        assertTrue(query.contains("hybrid"));
        assertTrue(query.contains("match"));
        assertTrue(query.contains("neural_sparse"));
        assertTrue(query.contains("sparse-model-456"));
    }

    @Test
    public void testBuildHybridSearchQueryString_EscapesSpecialChars() {
        String query = MemorySearchQueryBuilder.buildHybridSearchQueryString("test \"with quotes\"", textEmbeddingConfig());
        assertTrue(query.contains("test \\\"with quotes\\\""));
    }

    @Test
    public void testBuildSemanticSearchQuery_NullConfig_Throws() {
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildSemanticSearchQuery("test", null, null, null, "c1", null, null)
        );
    }

    @Test
    public void testBuildSemanticSearchQuery_NullEmbeddingType_Throws() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildSemanticSearchQuery("test", null, null, null, "c1", config, null)
        );
    }

    @Test
    public void testBuildHybridSearchQueryString_NullConfig_Throws() {
        assertThrows(IllegalStateException.class, () -> MemorySearchQueryBuilder.buildHybridSearchQueryString("test", null));
    }

    @Test
    public void testBuildHybridSearchQueryString_NullEmbeddingType_Throws() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        assertThrows(IllegalStateException.class, () -> MemorySearchQueryBuilder.buildHybridSearchQueryString("test", config));
    }

    @Test
    public void testBuildSemanticSearchQuery_WithFilter() {
        QueryBuilder filter = QueryBuilders.termQuery("strategy_type", "SEMANTIC");
        QueryBuilder query = MemorySearchQueryBuilder
            .buildSemanticSearchQuery("test", null, null, null, "c1", textEmbeddingConfig(), filter);
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
        QueryBuilder query = MemorySearchQueryBuilder.buildSemanticSearchQuery("test", null, null, null, null, textEmbeddingConfig(), null);
        // Should not throw, just no owner/container filters
        assertNotNull(query);
    }
}
