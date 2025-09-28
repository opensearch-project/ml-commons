/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;

public class MemorySearchQueryBuilderTests {

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
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .disableHistory(true)
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
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .disableHistory(true)
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
        // Create a config with unsupported type - will fail during build
        assertThrows(
            IllegalArgumentException.class,
            () -> MemoryConfiguration
                .builder()
                .disableHistory(true)
                .embeddingModelType(FunctionName.KMEANS) // Unsupported type - will throw during build
                .embeddingModelId("model-123")
                .build()
        );
    }

    @Test
    public void testBuildFactSearchQueryWithSparseEncoding() throws IOException {
        String fact = "Works at TechCorp";
        String sessionId = "session-456";
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .disableHistory(true)
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-model-789")
            .build();

        QueryBuilder builder = MemorySearchQueryBuilder
            .buildFactSearchQuery(
                MemoryStrategy.builder().type("semantic").namespace(List.of(SESSION_ID_FIELD)).build(),
                fact,
                Map.of(SESSION_ID_FIELD, sessionId),
                null, // ownerId
                config
            );
        String jsonString = builder.toString();
        jsonString = jsonString.replace("\n", "");
        jsonString = jsonString.replace(" ", "");

        // Verify filters
        assertTrue(jsonString.contains("{\"term\":{\"namespace.session_id\":{\"value\":\"session-456\",\"boost\":1.0}}}"));
        assertTrue(jsonString.contains("{\"term\":{\"namespace_size\":{\"value\":1,\"boost\":1.0}}}"));
        assertTrue(jsonString.contains("{\"term\":{\"memory_type\":{\"value\":\"SEMANTIC\",\"boost\":1.0}}}"));
    }

    @Test
    public void testBuildFactSearchQueryWithTextEmbedding() throws IOException {
        String fact = "Has a PhD in Computer Science";
        String sessionId = "session-999";

        QueryBuilder builder = MemorySearchQueryBuilder
            .buildFactSearchQuery(
                MemoryStrategy.builder().type("semantic").namespace(List.of(SESSION_ID_FIELD)).build(),
                fact,
                Map.of(SESSION_ID_FIELD, sessionId),
                null, // ownerId
                MemoryConfiguration
                    .builder()
                    .llmId("llm_id1")
                    .embeddingModelId("embedding_model1")
                    .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                    .dimension(512)
                    .build()
            );
        String jsonString = builder.toString();
        jsonString = jsonString.replace("\n", "");
        jsonString = jsonString.replace(" ", "");

        // Verify filters
        assertTrue(jsonString.contains("{\"term\":{\"namespace.session_id\":{\"value\":\"session-999\",\"boost\":1.0}}}"));
        assertTrue(jsonString.contains("{\"term\":{\"namespace_size\":{\"value\":1,\"boost\":1.0}}}"));
        assertTrue(jsonString.contains("{\"term\":{\"memory_type\":{\"value\":\"SEMANTIC\",\"boost\":1.0}}}"));
    }

    @Test
    public void testBuildFactSearchQueryWithUnsupportedType() {
        String fact = "test fact";
        String sessionId = "session-test";
        // Create a config with unsupported type - will fail during build
        assertThrows(
            IllegalArgumentException.class,
            () -> MemoryConfiguration
                .builder()
                .disableHistory(true)
                .embeddingModelType(FunctionName.LINEAR_REGRESSION) // Unsupported type - will throw during build
                .embeddingModelId("model-test")
                .build()
        );
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
}
