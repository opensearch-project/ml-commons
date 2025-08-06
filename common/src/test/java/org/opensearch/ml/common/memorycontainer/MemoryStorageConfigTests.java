/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;

public class MemoryStorageConfigTests {

    private MemoryStorageConfig textEmbeddingConfig;
    private MemoryStorageConfig sparseEncodingConfig;
    private MemoryStorageConfig minimalConfig;

    @Before
    public void setUp() {
        // Text embedding configuration (semantic storage enabled)
        textEmbeddingConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-text-embedding-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("text-embedding-model")
            .llmModelId("llm-model")
            .dimension(768)
            .maxInferSize(8)
            .build();

        // Sparse encoding configuration (semantic storage enabled)
        sparseEncodingConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-sparse-encoding-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-encoding-model")
            .llmModelId("llm-model")
            .dimension(null) // Not allowed for sparse encoding
            .maxInferSize(5)
            .build();

        // Minimal configuration (semantic storage disabled)
        minimalConfig = MemoryStorageConfig.builder().memoryIndexName("test-minimal-index").llmModelId("llm-model-only").build();
    }

    @Test
    public void testConstructorWithBuilderTextEmbedding() {
        assertNotNull(textEmbeddingConfig);
        assertEquals("test-text-embedding-index", textEmbeddingConfig.getMemoryIndexName());
        assertTrue(textEmbeddingConfig.isSemanticStorageEnabled()); // Auto-determined
        assertEquals(FunctionName.TEXT_EMBEDDING, textEmbeddingConfig.getEmbeddingModelType());
        assertEquals("text-embedding-model", textEmbeddingConfig.getEmbeddingModelId());
        assertEquals("llm-model", textEmbeddingConfig.getLlmModelId());
        assertEquals(Integer.valueOf(768), textEmbeddingConfig.getDimension());
        assertEquals(Integer.valueOf(8), textEmbeddingConfig.getMaxInferSize());
    }

    @Test
    public void testConstructorWithBuilderSparseEncoding() {
        assertNotNull(sparseEncodingConfig);
        assertEquals("test-sparse-encoding-index", sparseEncodingConfig.getMemoryIndexName());
        assertTrue(sparseEncodingConfig.isSemanticStorageEnabled()); // Auto-determined
        assertEquals(FunctionName.SPARSE_ENCODING, sparseEncodingConfig.getEmbeddingModelType());
        assertEquals("sparse-encoding-model", sparseEncodingConfig.getEmbeddingModelId());
        assertEquals("llm-model", sparseEncodingConfig.getLlmModelId());
        assertNull(sparseEncodingConfig.getDimension()); // Not allowed for sparse encoding
        assertEquals(Integer.valueOf(5), sparseEncodingConfig.getMaxInferSize());
    }

    @Test
    public void testConstructorWithBuilderMinimal() {
        assertNotNull(minimalConfig);
        assertEquals("test-minimal-index", minimalConfig.getMemoryIndexName());
        assertFalse(minimalConfig.isSemanticStorageEnabled()); // Auto-determined as false
        assertNull(minimalConfig.getEmbeddingModelType());
        assertNull(minimalConfig.getEmbeddingModelId());
        assertEquals("llm-model-only", minimalConfig.getLlmModelId());
        assertNull(minimalConfig.getDimension());
        assertEquals(Integer.valueOf(5), minimalConfig.getMaxInferSize()); // Default value when llmModelId is present
    }

    @Test
    public void testConstructorWithAllParameters() {
        MemoryStorageConfig config = new MemoryStorageConfig(
            "test-index",
            false, // This will be overridden by auto-determination
            FunctionName.TEXT_EMBEDDING,
            "embedding-model",
            "llm-model",
            512,
            7
        );

        assertEquals("test-index", config.getMemoryIndexName());
        assertTrue(config.isSemanticStorageEnabled()); // Auto-determined as true
        assertEquals(FunctionName.TEXT_EMBEDDING, config.getEmbeddingModelType());
        assertEquals("embedding-model", config.getEmbeddingModelId());
        assertEquals("llm-model", config.getLlmModelId());
        assertEquals(Integer.valueOf(512), config.getDimension());
        assertEquals(Integer.valueOf(7), config.getMaxInferSize());
    }

    @Test
    public void testDefaultMaxInferSize() {
        // Test with llmModelId present - should get default value
        MemoryStorageConfig configWithLlm = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmModelId("llm-model")
            .dimension(768)
            // maxInferSize not set, should use default
            .build();

        assertEquals(Integer.valueOf(MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE), configWithLlm.getMaxInferSize());

        // Test without llmModelId - should be null
        MemoryStorageConfig configWithoutLlm = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .dimension(768)
            .build();

        assertNull(configWithoutLlm.getMaxInferSize());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        textEmbeddingConfig.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MemoryStorageConfig parsedConfig = new MemoryStorageConfig(streamInput);

        assertEquals(textEmbeddingConfig.getMemoryIndexName(), parsedConfig.getMemoryIndexName());
        assertEquals(textEmbeddingConfig.isSemanticStorageEnabled(), parsedConfig.isSemanticStorageEnabled());
        assertEquals(textEmbeddingConfig.getEmbeddingModelType(), parsedConfig.getEmbeddingModelType());
        assertEquals(textEmbeddingConfig.getEmbeddingModelId(), parsedConfig.getEmbeddingModelId());
        assertEquals(textEmbeddingConfig.getLlmModelId(), parsedConfig.getLlmModelId());
        assertEquals(textEmbeddingConfig.getDimension(), parsedConfig.getDimension());
        assertEquals(textEmbeddingConfig.getMaxInferSize(), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testStreamInputOutputWithNullValues() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        minimalConfig.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MemoryStorageConfig parsedConfig = new MemoryStorageConfig(streamInput);

        assertEquals(minimalConfig.getMemoryIndexName(), parsedConfig.getMemoryIndexName());
        assertEquals(minimalConfig.isSemanticStorageEnabled(), parsedConfig.isSemanticStorageEnabled());
        assertNull(parsedConfig.getEmbeddingModelType());
        assertNull(parsedConfig.getEmbeddingModelId());
        assertEquals(minimalConfig.getLlmModelId(), parsedConfig.getLlmModelId());
        assertNull(parsedConfig.getDimension());
        assertEquals(Integer.valueOf(5), parsedConfig.getMaxInferSize()); // Default value when llmModelId is present
    }

    @Test
    public void testToXContentWithSemanticStorageEnabled() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        textEmbeddingConfig.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify semantic storage enabled fields are present
        assertTrue(jsonStr.contains("\"memory_index_name\":\"test-text-embedding-index\""));
        assertTrue(jsonStr.contains("\"semantic_storage_enabled\":true"));
        assertTrue(jsonStr.contains("\"embedding_model_type\":\"TEXT_EMBEDDING\""));
        assertTrue(jsonStr.contains("\"embedding_model_id\":\"text-embedding-model\""));
        assertTrue(jsonStr.contains("\"llm_model_id\":\"llm-model\""));
        assertTrue(jsonStr.contains("\"dimension\":768"));
        assertTrue(jsonStr.contains("\"max_infer_size\":8"));
    }

    @Test
    public void testToXContentWithSemanticStorageDisabled() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        minimalConfig.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify only basic fields are present
        assertTrue(jsonStr.contains("\"memory_index_name\":\"test-minimal-index\""));
        assertTrue(jsonStr.contains("\"semantic_storage_enabled\":false"));
        assertTrue(jsonStr.contains("\"llm_model_id\":\"llm-model-only\""));
        // Verify semantic storage fields are NOT present
        assertFalse(jsonStr.contains("\"embedding_model_type\""));
        assertFalse(jsonStr.contains("\"embedding_model_id\""));
        assertFalse(jsonStr.contains("\"dimension\""));
        // max_infer_size is present because llmModelId is set
        assertTrue(jsonStr.contains("\"max_infer_size\":5"));
    }

    @Test
    public void testParseFromXContentWithAllFields() throws IOException {
        String jsonStr = "{" + "\"memory_index_name\":\"parsed-index\"," + "\"semantic_storage_enabled\":true," + // This field is ignored
            "\"embedding_model_type\":\"TEXT_EMBEDDING\","
            + "\"embedding_model_id\":\"parsed-embedding-model\","
            + "\"llm_model_id\":\"parsed-llm-model\","
            + "\"dimension\":1024,"
            + "\"max_infer_size\":9"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryStorageConfig parsedConfig = MemoryStorageConfig.parse(parser);

        assertEquals("parsed-index", parsedConfig.getMemoryIndexName());
        assertTrue(parsedConfig.isSemanticStorageEnabled()); // Auto-determined
        assertEquals(FunctionName.TEXT_EMBEDDING, parsedConfig.getEmbeddingModelType());
        assertEquals("parsed-embedding-model", parsedConfig.getEmbeddingModelId());
        assertEquals("parsed-llm-model", parsedConfig.getLlmModelId());
        assertEquals(Integer.valueOf(1024), parsedConfig.getDimension());
        assertEquals(Integer.valueOf(9), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testParseFromXContentWithPartialFields() throws IOException {
        String jsonStr = "{" + "\"memory_index_name\":\"partial-index\"," + "\"llm_model_id\":\"partial-llm-model\"" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryStorageConfig parsedConfig = MemoryStorageConfig.parse(parser);

        assertEquals("partial-index", parsedConfig.getMemoryIndexName());
        assertFalse(parsedConfig.isSemanticStorageEnabled()); // Auto-determined as false
        assertNull(parsedConfig.getEmbeddingModelType());
        assertNull(parsedConfig.getEmbeddingModelId());
        assertEquals("partial-llm-model", parsedConfig.getLlmModelId());
        assertNull(parsedConfig.getDimension());
        assertEquals(Integer.valueOf(5), parsedConfig.getMaxInferSize()); // Default value when llmModelId is present
    }

    @Test
    public void testParseFromXContentWithUnknownFields() throws IOException {
        String jsonStr = "{"
            + "\"memory_index_name\":\"unknown-test-index\","
            + "\"unknown_field\":\"unknown_value\","
            + "\"llm_model_id\":\"test-llm\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryStorageConfig parsedConfig = MemoryStorageConfig.parse(parser);

        assertEquals("unknown-test-index", parsedConfig.getMemoryIndexName());
        assertEquals("test-llm", parsedConfig.getLlmModelId());
        // Unknown fields should be ignored
        assertFalse(parsedConfig.isSemanticStorageEnabled());
    }

    @Test
    public void testCompleteRoundTrip() throws IOException {
        // Test complete round trip: object -> JSON -> parse -> compare
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        textEmbeddingConfig.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryStorageConfig parsedConfig = MemoryStorageConfig.parse(parser);

        assertEquals(textEmbeddingConfig.getMemoryIndexName(), parsedConfig.getMemoryIndexName());
        assertEquals(textEmbeddingConfig.isSemanticStorageEnabled(), parsedConfig.isSemanticStorageEnabled());
        assertEquals(textEmbeddingConfig.getEmbeddingModelType(), parsedConfig.getEmbeddingModelType());
        assertEquals(textEmbeddingConfig.getEmbeddingModelId(), parsedConfig.getEmbeddingModelId());
        assertEquals(textEmbeddingConfig.getLlmModelId(), parsedConfig.getLlmModelId());
        assertEquals(textEmbeddingConfig.getDimension(), parsedConfig.getDimension());
        assertEquals(textEmbeddingConfig.getMaxInferSize(), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testEqualsAndHashCode() {
        MemoryStorageConfig config1 = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmModelId("llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MemoryStorageConfig config2 = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmModelId("llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MemoryStorageConfig config3 = MemoryStorageConfig
            .builder()
            .memoryIndexName("different-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmModelId("llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertFalse(config1.equals(config3));
        assertTrue(config1.hashCode() != config3.hashCode());
    }

    @Test
    public void testSettersAndGetters() {
        MemoryStorageConfig config = new MemoryStorageConfig(null, false, null, null, null, null, null);

        config.setMemoryIndexName("new-index");
        config.setSemanticStorageEnabled(true);
        config.setEmbeddingModelType(FunctionName.SPARSE_ENCODING);
        config.setEmbeddingModelId("new-embedding-model");
        config.setLlmModelId("new-llm-model");
        config.setDimension(1024);
        config.setMaxInferSize(10);

        assertEquals("new-index", config.getMemoryIndexName());
        assertTrue(config.isSemanticStorageEnabled());
        assertEquals(FunctionName.SPARSE_ENCODING, config.getEmbeddingModelType());
        assertEquals("new-embedding-model", config.getEmbeddingModelId());
        assertEquals("new-llm-model", config.getLlmModelId());
        assertEquals(Integer.valueOf(1024), config.getDimension());
        assertEquals(Integer.valueOf(10), config.getMaxInferSize());
    }

    // Validation Tests

    @Test(expected = IllegalArgumentException.class)
    public void testValidationEmbeddingModelIdWithoutType() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelId("embedding-model") // Missing embeddingModelType
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationEmbeddingModelTypeWithoutId() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING) // Missing embeddingModelId
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationTextEmbeddingWithoutDimension() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            // Missing dimension for TEXT_EMBEDDING
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationSparseEncodingWithDimension() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("embedding-model")
            .dimension(768) // Not allowed for SPARSE_ENCODING
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationMaxInferSizeExceedsLimit() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .dimension(768)
            .maxInferSize(11) // Exceeds limit of 10
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationInvalidEmbeddingModelType() {
        MemoryStorageConfig
            .builder()
            .memoryIndexName("test-index")
            .embeddingModelType(FunctionName.KMEANS) // Invalid embedding model type
            .embeddingModelId("embedding-model")
            .dimension(768)
            .build();
    }
}
