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

    private MemoryConfiguration textEmbeddingConfig;
    private MemoryConfiguration sparseEncodingConfig;
    private MemoryConfiguration minimalConfig;

    @Before
    public void setUp() {
        // Text embedding configuration (semantic storage enabled)
        textEmbeddingConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test-text-embedding-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("text-embedding-model")
            .llmId("llm-model")
            .dimension(768)
            .maxInferSize(8)
            .disableSession(false)
            .build();

        // Sparse encoding configuration (semantic storage enabled)
        sparseEncodingConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test-sparse-encoding-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-encoding-model")
            .llmId("llm-model")
            .dimension(null) // Not allowed for sparse encoding
            .maxInferSize(5)
            .build();

        // Minimal configuration (semantic storage disabled)
        minimalConfig = MemoryConfiguration.builder().indexPrefix("test-minimal-index").llmId("llm-model-only").build();
    }

    @Test
    public void testConstructorWithBuilderTextEmbedding() {
        assertNotNull(textEmbeddingConfig);
        assertEquals("test-text-embedding-index", textEmbeddingConfig.getIndexPrefix());
        assertEquals(FunctionName.TEXT_EMBEDDING, textEmbeddingConfig.getEmbeddingModelType());
        assertEquals("text-embedding-model", textEmbeddingConfig.getEmbeddingModelId());
        assertEquals("llm-model", textEmbeddingConfig.getLlmId());
        assertEquals(Integer.valueOf(768), textEmbeddingConfig.getDimension());
        assertEquals(Integer.valueOf(8), textEmbeddingConfig.getMaxInferSize());
    }

    @Test
    public void testConstructorWithBuilderSparseEncoding() {
        assertNotNull(sparseEncodingConfig);
        assertEquals("test-sparse-encoding-index", sparseEncodingConfig.getIndexPrefix());
        assertEquals(FunctionName.SPARSE_ENCODING, sparseEncodingConfig.getEmbeddingModelType());
        assertEquals("sparse-encoding-model", sparseEncodingConfig.getEmbeddingModelId());
        assertEquals("llm-model", sparseEncodingConfig.getLlmId());
        assertNull(sparseEncodingConfig.getDimension()); // Not allowed for sparse encoding
        assertEquals(Integer.valueOf(5), sparseEncodingConfig.getMaxInferSize());
    }

    @Test
    public void testConstructorWithBuilderMinimal() {
        assertNotNull(minimalConfig);
        assertEquals("test-minimal-index", minimalConfig.getIndexPrefix());
        assertNull(minimalConfig.getEmbeddingModelType());
        assertNull(minimalConfig.getEmbeddingModelId());
        assertEquals("llm-model-only", minimalConfig.getLlmId());
        assertNull(minimalConfig.getDimension());
        assertEquals(Integer.valueOf(5), minimalConfig.getMaxInferSize()); // Default value when llmModelId is present
    }

    @Test
    public void testConstructorWithAllParameters() {
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .disableSession(false)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .dimension(512)
            .maxInferSize(7)
            .llmId("llm-model")
            .build();

        assertEquals("test-index", config.getIndexPrefix());
        assertEquals(FunctionName.TEXT_EMBEDDING, config.getEmbeddingModelType());
        assertEquals("embedding-model", config.getEmbeddingModelId());
        assertEquals("llm-model", config.getLlmId());
        assertEquals(Integer.valueOf(512), config.getDimension());
        assertEquals(Integer.valueOf(7), config.getMaxInferSize());
    }

    @Test
    public void testDefaultMaxInferSize() {
        // Test with llmModelId present - should get default value
        MemoryConfiguration configWithLlm = MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmId("llm-model")
            .dimension(768)
            // maxInferSize not set, should use default
            .build();

        assertEquals(Integer.valueOf(MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE), configWithLlm.getMaxInferSize());

        // Test without llmModelId - should be null
        MemoryConfiguration configWithoutLlm = MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
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
        MemoryConfiguration parsedConfig = new MemoryConfiguration(streamInput);

        assertEquals(textEmbeddingConfig.getIndexPrefix(), parsedConfig.getIndexPrefix());
        assertEquals(textEmbeddingConfig.getEmbeddingModelType(), parsedConfig.getEmbeddingModelType());
        assertEquals(textEmbeddingConfig.getEmbeddingModelId(), parsedConfig.getEmbeddingModelId());
        assertEquals(textEmbeddingConfig.getLlmId(), parsedConfig.getLlmId());
        assertEquals(textEmbeddingConfig.getDimension(), parsedConfig.getDimension());
        assertEquals(textEmbeddingConfig.getMaxInferSize(), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testStreamInputOutputWithNullValues() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        minimalConfig.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MemoryConfiguration parsedConfig = new MemoryConfiguration(streamInput);

        assertEquals(minimalConfig.getIndexPrefix(), parsedConfig.getIndexPrefix());
        assertNull(parsedConfig.getEmbeddingModelType());
        assertNull(parsedConfig.getEmbeddingModelId());
        assertEquals(minimalConfig.getLlmId(), parsedConfig.getLlmId());
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
        assertTrue(jsonStr.contains("\"index_prefix\":\"test-text-embedding-index\""));
        assertTrue(jsonStr.contains("\"disable_history\":false"));
        assertTrue(jsonStr.contains("\"disable_session\":false"));
        assertTrue(jsonStr.contains("\"embedding_model_type\":\"TEXT_EMBEDDING\""));
        assertTrue(jsonStr.contains("\"embedding_model_id\":\"text-embedding-model\""));
        assertTrue(jsonStr.contains("\"llm_id\":\"llm-model\""));
        assertTrue(jsonStr.contains("\"embedding_dimension\":768"));
        assertTrue(jsonStr.contains("\"max_infer_size\":8"));
    }

    @Test
    public void testToXContentWithSemanticStorageDisabled() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        minimalConfig.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify only basic fields are present
        assertTrue(jsonStr.contains("\"index_prefix\":\"test-minimal-index\""));
        assertTrue(jsonStr.contains("\"disable_history\":false"));
        assertTrue(jsonStr.contains("\"llm_id\":\"llm-model-only\""));
        // Verify semantic storage fields are NOT present
        assertFalse(jsonStr.contains("\"embedding_model_type\""));
        assertFalse(jsonStr.contains("\"embedding_model_id\""));
        assertFalse(jsonStr.contains("\"embedding_dimension\""));
        // max_infer_size is present because llmModelId is set
        assertTrue(jsonStr.contains("\"max_infer_size\":5"));
    }

    @Test
    public void testParseFromXContentWithAllFields() throws IOException {
        String jsonStr = "{" + "\"index_prefix\":\"parsed-index\"," + "\"disable_history\":true," + // This field is ignored
            "\"embedding_model_type\":\"TEXT_EMBEDDING\","
            + "\"embedding_model_id\":\"parsed-embedding-model\","
            + "\"llm_id\":\"parsed-llm-model\","
            + "\"embedding_dimension\":1024,"
            + "\"max_infer_size\":9"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryConfiguration parsedConfig = MemoryConfiguration.parse(parser);

        assertEquals("parsed-index", parsedConfig.getIndexPrefix());
        assertEquals(FunctionName.TEXT_EMBEDDING, parsedConfig.getEmbeddingModelType());
        assertEquals("parsed-embedding-model", parsedConfig.getEmbeddingModelId());
        assertEquals("parsed-llm-model", parsedConfig.getLlmId());
        assertEquals(Integer.valueOf(1024), parsedConfig.getDimension());
        assertEquals(Integer.valueOf(9), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testParseFromXContentWithPartialFields() throws IOException {
        String jsonStr = "{" + "\"index_prefix\":\"partial-index\"," + "\"llm_id\":\"partial-llm-model\"" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryConfiguration parsedConfig = MemoryConfiguration.parse(parser);

        assertEquals("partial-index", parsedConfig.getIndexPrefix());
        assertNull(parsedConfig.getEmbeddingModelType());
        assertNull(parsedConfig.getEmbeddingModelId());
        assertEquals("partial-llm-model", parsedConfig.getLlmId());
        assertNull(parsedConfig.getDimension());
        assertEquals(Integer.valueOf(5), parsedConfig.getMaxInferSize()); // Default value when llmModelId is present
    }

    @Test
    public void testParseFromXContentWithUnknownFields() throws IOException {
        String jsonStr = "{"
            + "\"index_prefix\":\"unknown-test-index\","
            + "\"unknown_field\":\"unknown_value\","
            + "\"llm_id\":\"test-llm\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MemoryConfiguration parsedConfig = MemoryConfiguration.parse(parser);

        assertEquals("unknown-test-index", parsedConfig.getIndexPrefix());
        assertEquals("test-llm", parsedConfig.getLlmId());
        // Unknown fields should be ignored
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
        MemoryConfiguration parsedConfig = MemoryConfiguration.parse(parser);

        assertEquals(textEmbeddingConfig.getIndexPrefix(), parsedConfig.getIndexPrefix());
        assertEquals(textEmbeddingConfig.getEmbeddingModelType(), parsedConfig.getEmbeddingModelType());
        assertEquals(textEmbeddingConfig.getEmbeddingModelId(), parsedConfig.getEmbeddingModelId());
        assertEquals(textEmbeddingConfig.getLlmId(), parsedConfig.getLlmId());
        assertEquals(textEmbeddingConfig.getDimension(), parsedConfig.getDimension());
        assertEquals(textEmbeddingConfig.getMaxInferSize(), parsedConfig.getMaxInferSize());
    }

    @Test
    public void testEqualsAndHashCode() {
        MemoryConfiguration config1 = MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmId("llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MemoryConfiguration config2 = MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmId("llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MemoryConfiguration config3 = MemoryConfiguration
            .builder()
            .indexPrefix("different-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .llmId("llm-model")
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
        MemoryConfiguration config = MemoryConfiguration.builder().disableSession(true).build();

        config.setIndexPrefix("new-index");
        config.setDisableHistory(true);
        config.setEmbeddingModelType(FunctionName.SPARSE_ENCODING);
        config.setEmbeddingModelId("new-embedding-model");
        config.setLlmId("new-llm-model");
        config.setDimension(1024);
        config.setMaxInferSize(10);

        assertEquals("new-index", config.getIndexPrefix());
        assertTrue(config.isDisableSession());
        assertTrue(config.isDisableHistory());
        assertEquals(FunctionName.SPARSE_ENCODING, config.getEmbeddingModelType());
        assertEquals("new-embedding-model", config.getEmbeddingModelId());
        assertEquals("new-llm-model", config.getLlmId());
        assertEquals(Integer.valueOf(1024), config.getDimension());
        assertEquals(Integer.valueOf(10), config.getMaxInferSize());
    }

    // Validation Tests

    @Test(expected = IllegalArgumentException.class)
    public void testValidationEmbeddingModelIdWithoutType() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelId("embedding-model") // Missing embeddingModelType
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationEmbeddingModelTypeWithoutId() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING) // Missing embeddingModelId
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationTextEmbeddingWithoutDimension() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            // Missing dimension for TEXT_EMBEDDING
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationSparseEncodingWithDimension() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("embedding-model")
            .dimension(768) // Not allowed for SPARSE_ENCODING
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationMaxInferSizeExceedsLimit() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("embedding-model")
            .dimension(768)
            .maxInferSize(11) // Exceeds limit of 10
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationInvalidEmbeddingModelType() {
        MemoryConfiguration
            .builder()
            .indexPrefix("test-index")
            .embeddingModelType(FunctionName.KMEANS) // Invalid embedding model type
            .embeddingModelId("embedding-model")
            .dimension(768)
            .build();
    }

    // Tests for getFinalMemoryIndexPrefix method

    @Test
    public void testGetFinalMemoryIndexPrefixWithSystemIndexEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test-prefix").useSystemIndex(true).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals(".plugins-ml-am-test-prefix-memory-", result);
    }

    @Test
    public void testGetFinalMemoryIndexPrefixWithSystemIndexDisabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("custom-prefix").useSystemIndex(false).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals("custom-prefix-memory-", result);
    }

    @Test
    public void testGetFinalMemoryIndexPrefixWithNullIndexPrefixAndSystemIndexEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix(null).useSystemIndex(true).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals(".plugins-ml-am-default-memory-", result);
    }

    @Test
    public void testGetFinalMemoryIndexPrefixWithEmptyIndexPrefixAndSystemIndexEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix(" ").useSystemIndex(true).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals(".plugins-ml-am-default-memory-", result);
    }

    @Test
    public void testGetFinalMemoryIndexPrefixWithNullIndexPrefixAndSystemIndexDisabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix(null).useSystemIndex(false).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertNotNull(result);
        assertEquals(16, result.length());
        assertTrue(result.endsWith("-memory-"));
    }

    @Test
    public void testGetFinalMemoryIndexPrefixWithSpecialCharacters() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test-prefix_123").useSystemIndex(true).build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals(".plugins-ml-am-test-prefix_123-memory-", result);
    }

    @Test
    public void testGetFinalMemoryIndexPrefixDefaultUseSystemIndexValue() {
        // Test that useSystemIndex defaults to true
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("default-test").build();

        String result = config.getFinalMemoryIndexPrefix();
        assertEquals(".plugins-ml-am-default-test-memory-", result);
        assertTrue(config.isUseSystemIndex()); // Verify default value
    }
}
