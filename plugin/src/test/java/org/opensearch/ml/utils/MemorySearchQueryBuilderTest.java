/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

public class MemorySearchQueryBuilderTest {

    @Test
    public void testBuildNeuralQuery() throws IOException {
        String queryText = "machine learning concepts";
        String embeddingModelId = "model-123";

        XContentBuilder builder = MemorySearchQueryBuilder.buildNeuralQuery(queryText, embeddingModelId);
        String jsonString = builder.toString();

        assertNotNull(jsonString);
        assertTrue(jsonString.contains("\"neural\""));
        assertTrue(jsonString.contains("\"memory_embedding\""));
        assertTrue(jsonString.contains("\"query_text\":\"machine learning concepts\""));
        assertTrue(jsonString.contains("\"model_id\":\"model-123\""));
    }

    @Test
    public void testBuildNeuralSparseQuery() throws IOException {
        String queryText = "deep learning frameworks";
        String embeddingModelId = "sparse-model-456";

        XContentBuilder builder = MemorySearchQueryBuilder.buildNeuralSparseQuery(queryText, embeddingModelId);
        String jsonString = builder.toString();

        assertNotNull(jsonString);
        assertTrue(jsonString.contains("\"neural_sparse\""));
        assertTrue(jsonString.contains("\"memory_embedding\""));
        assertTrue(jsonString.contains("\"query_text\":\"deep learning frameworks\""));
        assertTrue(jsonString.contains("\"model_id\":\"sparse-model-456\""));
    }

    @Test
    public void testBuildMatchQuery() throws IOException {
        String queryText = "natural language processing";

        XContentBuilder builder = MemorySearchQueryBuilder.buildMatchQuery(queryText);
        String jsonString = builder.toString();

        assertNotNull(jsonString);
        assertTrue(jsonString.contains("\"match\""));
        assertTrue(jsonString.contains("\"memory\":\"natural language processing\""));
        assertFalse(jsonString.contains("\"neural\""));
        assertFalse(jsonString.contains("\"model_id\""));
    }

    @Test
    public void testBuildQueryByStorageTypeWithTextEmbedding() throws IOException {
        String queryText = "AI research topics";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("dense-model-789")
            .dimension(768)
            .build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildQueryByStorageType(queryText, config);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"neural\""));
        assertTrue(jsonString.contains("\"query_text\":\"AI research topics\""));
        assertTrue(jsonString.contains("\"model_id\":\"dense-model-789\""));
    }

    @Test
    public void testBuildQueryByStorageTypeWithSparseEncoding() throws IOException {
        String queryText = "computer vision";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-model-999")
            .build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildQueryByStorageType(queryText, config);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"neural_sparse\""));
        assertTrue(jsonString.contains("\"query_text\":\"computer vision\""));
        assertTrue(jsonString.contains("\"model_id\":\"sparse-model-999\""));
    }

    @Test
    public void testBuildQueryByStorageTypeWithNonSemanticStorage() throws IOException {
        String queryText = "reinforcement learning";
        MemoryStorageConfig config = MemoryStorageConfig.builder().semanticStorageEnabled(false).build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildQueryByStorageType(queryText, config);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"match\""));
        assertTrue(jsonString.contains("\"memory\":\"reinforcement learning\""));
        assertFalse(jsonString.contains("\"neural\""));
    }

    @Test
    public void testBuildQueryByStorageTypeWithNullConfig() throws IOException {
        String queryText = "quantum computing";

        XContentBuilder builder = MemorySearchQueryBuilder.buildQueryByStorageType(queryText, null);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"match\""));
        assertTrue(jsonString.contains("\"memory\":\"quantum computing\""));
    }

    @Test
    public void testBuildQueryByStorageTypeWithUnsupportedType() {
        String queryText = "test query";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.KMEANS) // Unsupported type
            .embeddingModelId("model-123")
            .build();

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildQueryByStorageType(queryText, config)
        );
        assertTrue(exception.getMessage().contains("Unsupported embedding model type"));
        assertTrue(exception.getMessage().contains("KMEANS"));
    }

    @Test
    public void testBuildFactSearchQueryWithTextEmbedding() throws IOException {
        String fact = "User's name is John";
        String sessionId = "session-123";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("text-model-456")
            .dimension(384)
            .build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, config);
        String jsonString = builder.toString();

        // Verify bool query structure
        assertTrue(jsonString.contains("\"bool\""));
        assertTrue(jsonString.contains("\"filter\""));
        assertTrue(jsonString.contains("\"must\""));

        // Verify filters
        assertTrue(jsonString.contains("\"term\":{\"session_id\":\"session-123\"}"));
        assertTrue(jsonString.contains("\"term\":{\"memory_type\":\"FACT\"}"));

        // Verify neural query
        assertTrue(jsonString.contains("\"neural\""));
        assertTrue(jsonString.contains("\"query_text\":\"User's name is John\""));
        assertTrue(jsonString.contains("\"model_id\":\"text-model-456\""));
    }

    @Test
    public void testBuildFactSearchQueryWithSparseEncoding() throws IOException {
        String fact = "Works at TechCorp";
        String sessionId = "session-456";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-model-789")
            .build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, config);
        String jsonString = builder.toString();

        // Verify filters
        assertTrue(jsonString.contains("\"term\":{\"session_id\":\"session-456\"}"));
        assertTrue(jsonString.contains("\"term\":{\"memory_type\":\"FACT\"}"));

        // Verify neural_sparse query
        assertTrue(jsonString.contains("\"neural_sparse\""));
        assertTrue(jsonString.contains("\"query_text\":\"Works at TechCorp\""));
        assertTrue(jsonString.contains("\"model_id\":\"sparse-model-789\""));
    }

    @Test
    public void testBuildFactSearchQueryWithNonSemanticStorage() throws IOException {
        String fact = "Lives in San Francisco";
        String sessionId = "session-789";
        MemoryStorageConfig config = MemoryStorageConfig.builder().semanticStorageEnabled(false).build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, config);
        String jsonString = builder.toString();

        // Verify filters
        assertTrue(jsonString.contains("\"term\":{\"session_id\":\"session-789\"}"));
        assertTrue(jsonString.contains("\"term\":{\"memory_type\":\"FACT\"}"));

        // Verify match query
        assertTrue(jsonString.contains("\"match\""));
        assertTrue(jsonString.contains("\"memory\":\"Lives in San Francisco\""));
        assertFalse(jsonString.contains("\"neural\""));
    }

    @Test
    public void testBuildFactSearchQueryWithNullConfig() throws IOException {
        String fact = "Has a PhD in Computer Science";
        String sessionId = "session-999";

        XContentBuilder builder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, null);
        String jsonString = builder.toString();

        // Verify filters
        assertTrue(jsonString.contains("\"term\":{\"session_id\":\"session-999\"}"));
        assertTrue(jsonString.contains("\"term\":{\"memory_type\":\"FACT\"}"));

        // Verify match query (fallback for null config)
        assertTrue(jsonString.contains("\"match\""));
        assertTrue(jsonString.contains("\"memory\":\"Has a PhD in Computer Science\""));
    }

    @Test
    public void testBuildFactSearchQueryWithUnsupportedType() {
        String fact = "test fact";
        String sessionId = "session-test";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.LINEAR_REGRESSION) // Unsupported type
            .embeddingModelId("model-test")
            .build();

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, config)
        );
        assertTrue(exception.getMessage().contains("Unsupported embedding model type"));
        assertTrue(exception.getMessage().contains("LINEAR_REGRESSION"));
    }

    @Test
    public void testBuildQueriesWithSpecialCharacters() throws IOException {
        String queryText = "Query with \"quotes\" and \nnewlines";
        String embeddingModelId = "model-with-special-chars-🚀";

        // Test neural query
        XContentBuilder neuralBuilder = MemorySearchQueryBuilder.buildNeuralQuery(queryText, embeddingModelId);
        String neuralJson = neuralBuilder.toString();
        assertTrue(neuralJson.contains("Query with"));
        assertTrue(neuralJson.contains("quotes"));
        assertTrue(neuralJson.contains("newlines"));

        // Test match query
        XContentBuilder matchBuilder = MemorySearchQueryBuilder.buildMatchQuery(queryText);
        String matchJson = matchBuilder.toString();
        assertTrue(matchJson.contains("Query with"));
        assertTrue(matchJson.contains("quotes"));
        assertTrue(matchJson.contains("newlines"));
    }

    @Test
    public void testBuildFactSearchQueryStructure() throws IOException {
        // Test the exact structure of the fact search query
        String fact = "Simple fact";
        String sessionId = "sess-123";
        MemoryStorageConfig config = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-123")
            .build();

        XContentBuilder builder = MemorySearchQueryBuilder.buildFactSearchQuery(fact, sessionId, config);
        String jsonString = builder.toString();

        // Verify the query doesn't have a "query" wrapper (as per comment in code)
        assertFalse(jsonString.startsWith("{\"query\":"));

        // Verify it starts with bool
        assertTrue(jsonString.startsWith("{\"bool\":"));

        // Verify both filter and must sections exist
        assertTrue(jsonString.contains("\"filter\":{\"bool\":{\"must\":["));
        assertTrue(jsonString.contains("],\"must\":[{"));
    }

    @Test
    public void testBuildNeuralQueryWithEmptyText() throws IOException {
        XContentBuilder builder = MemorySearchQueryBuilder.buildNeuralQuery("", "model-123");
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"query_text\":\"\""));
        assertTrue(jsonString.contains("\"model_id\":\"model-123\""));
    }

    @Test
    public void testBuildMatchQueryWithEmptyText() throws IOException {
        XContentBuilder builder = MemorySearchQueryBuilder.buildMatchQuery("");
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"memory\":\"\""));
    }

    // Helper method to verify JSON structure
    private void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
