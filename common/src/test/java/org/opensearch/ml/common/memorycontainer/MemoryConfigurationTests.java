/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.OpenSearchParseException;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration.EmbeddingConfig;

/**
 * Unit tests for MemoryConfiguration class methods.
 */
public class MemoryConfigurationTests {

    /** Creates a config with long-term memory enabled (llmId + strategies required for LONG_TERM/HISTORY index names). */
    private static MemoryConfiguration configWithLongTermMemory(MemoryConfiguration.MemoryConfigurationBuilder builder) {
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("test-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("test-namespace"))
                    .build()
            );
        return (builder != null ? builder : MemoryConfiguration.builder()).llmId("test-llm").strategies(strategies).build();
    }

    // ==================== validateStrategiesRequireModels Tests ====================

    @Test
    public void testValidateStrategiesRequireModels_NullConfig() {
        // Should not throw exception
        MemoryConfiguration.validateStrategiesRequireModels(null);
    }

    @Test
    public void testValidateStrategiesRequireModels_NullStrategies() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();
        // Should not throw exception
        MemoryConfiguration.validateStrategiesRequireModels(config);
    }

    @Test
    public void testValidateStrategiesRequireModels_EmptyStrategies() {
        MemoryConfiguration config = MemoryConfiguration.builder().strategies(new ArrayList<>()).build();
        // Should not throw exception
        MemoryConfiguration.validateStrategiesRequireModels(config);
    }

    @Test
    public void testValidateStrategiesRequireModels_BothModelsConfigured() {
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("test-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("test-namespace"))
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .llmId("llm-model-id")
            .embeddingModelId("embed-model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .strategies(strategies)
            .build();

        // Should not throw exception
        MemoryConfiguration.validateStrategiesRequireModels(config);
    }

    @Test
    public void testValidateStrategiesRequireModels_MissingLlm() {
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("test-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("test-namespace"))
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .embeddingModelId("embed-model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .strategies(strategies)
            .build();

        try {
            MemoryConfiguration.validateStrategiesRequireModels(config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("LLM model (llm_id)"));
        }
    }

    @Test
    public void testValidateStrategiesRequireModels_MissingEmbedding() {
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("test-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("test-namespace"))
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration.builder().llmId("llm-model-id").strategies(strategies).build();

        try {
            MemoryConfiguration.validateStrategiesRequireModels(config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("embedding model"));
        }
    }

    @Test
    public void testValidateStrategiesRequireModels_MissingBoth() {
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("test-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("test-namespace"))
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration.builder().strategies(strategies).build();

        try {
            MemoryConfiguration.validateStrategiesRequireModels(config);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("LLM model and embedding model"));
        }
    }

    // ==================== update Tests ====================

    @Test
    public void testUpdate_UpdateLlmId() {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId("old-llm-id").build();

        MemoryConfiguration updateContent = MemoryConfiguration.builder().llmId("new-llm-id").build();

        config.update(updateContent);
        assertEquals("new-llm-id", config.getLlmId());
    }

    @Test
    public void testUpdate_UpdateStrategies() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        List<MemoryStrategy> newStrategies = new ArrayList<>();
        newStrategies
            .add(
                MemoryStrategy
                    .builder()
                    .id("new-strategy-id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("new-namespace"))
                    .build()
            );
        MemoryConfiguration updateContent = MemoryConfiguration.builder().strategies(newStrategies).build();

        config.update(updateContent);
        assertEquals(1, config.getStrategies().size());
        assertEquals("new-strategy-id", config.getStrategies().get(0).getId());
    }

    @Test
    public void testUpdate_UpdateMaxInferSize() {
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .llmId("llm-id")
            .maxInferSize(5)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-id")
            .dimension(768)
            .build();

        MemoryConfiguration updateContent = MemoryConfiguration
            .builder()
            .llmId("llm-id")
            .maxInferSize(8)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-id")
            .dimension(768)
            .build();

        config.update(updateContent);
        assertEquals(Integer.valueOf(8), config.getMaxInferSize());
    }

    @Test
    public void testUpdate_UpdateEmbeddingModelId() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        // Create valid updateContent with all required embedding fields
        MemoryConfiguration updateContent = MemoryConfiguration
            .builder()
            .embeddingModelId("new-embed-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        config.update(updateContent);
        assertEquals("new-embed-id", config.getEmbeddingModelId());
    }

    @Test
    public void testUpdate_UpdateEmbeddingModelType() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        MemoryConfiguration updateContent = MemoryConfiguration
            .builder()
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-id")
            .dimension(768)
            .build();

        config.update(updateContent);
        assertEquals(FunctionName.TEXT_EMBEDDING, config.getEmbeddingModelType());
    }

    @Test
    public void testUpdate_UpdateDimension() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        MemoryConfiguration updateContent = MemoryConfiguration
            .builder()
            .dimension(1024)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("model-id")
            .build();

        config.update(updateContent);
        assertEquals(Integer.valueOf(1024), config.getDimension());
    }

    @Test
    public void testUpdate_SparseEncodingAutoClearsDimension() {
        // Start with TEXT_EMBEDDING model that has dimension
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("text-model-id")
            .dimension(1536)
            .build();

        // Update to SPARSE_ENCODING - dimension should auto-clear
        MemoryConfiguration updateContent = MemoryConfiguration
            .builder()
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-model-id")
            .build();

        config.update(updateContent);

        assertEquals(FunctionName.SPARSE_ENCODING, config.getEmbeddingModelType());
        assertEquals("sparse-model-id", config.getEmbeddingModelId());
        assertNull(config.getDimension()); // Dimension should be auto-cleared
    }

    @Test
    public void testUpdate_NullValuesNotUpdated() {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId("original-llm-id").build();

        MemoryConfiguration updateContent = MemoryConfiguration.builder().build(); // All nulls

        config.update(updateContent);
        assertEquals("original-llm-id", config.getLlmId()); // Should remain unchanged
    }

    @Test
    public void testUpdate_IndexPrefixNotUpdated() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("original-prefix").build();

        MemoryConfiguration updateContent = MemoryConfiguration.builder().indexPrefix("new-prefix").build();

        config.update(updateContent);
        // indexPrefix should NOT be updated (intentional in update() method)
        assertEquals("original-prefix", config.getIndexPrefix());
    }

    // ==================== extractEmbeddingConfigFromMapping Tests ====================

    @Test
    public void testExtractEmbeddingConfigFromMapping_NullMapping() {
        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(null);
        assertNull(result);
    }

    @Test
    public void testExtractEmbeddingConfigFromMapping_NoEmbeddingField() {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("other_field", new HashMap<>());

        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(mapping);
        assertNull(result);
    }

    @Test
    public void testExtractEmbeddingConfigFromMapping_EmbeddingFieldNotMap() {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("memory_embedding", "not-a-map");

        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(mapping);
        assertNull(result);
    }

    @Test
    public void testExtractEmbeddingConfigFromMapping_TextEmbedding() {
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 768);

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("memory_embedding", embeddingField);

        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(mapping);
        assertNotNull(result);
        assertEquals(FunctionName.TEXT_EMBEDDING, result.getType());
        assertEquals(Integer.valueOf(768), result.getDimension());
    }

    @Test
    public void testExtractEmbeddingConfigFromMapping_SparseEncoding() {
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "rank_features");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("memory_embedding", embeddingField);

        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(mapping);
        assertNotNull(result);
        assertEquals(FunctionName.SPARSE_ENCODING, result.getType());
        assertNull(result.getDimension());
    }

    @Test
    public void testExtractEmbeddingConfigFromMapping_UnknownType() {
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "unknown_type");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("memory_embedding", embeddingField);

        EmbeddingConfig result = MemoryConfiguration.extractEmbeddingConfigFromMapping(mapping);
        assertNull(result);
    }

    // ==================== asMap Tests ====================

    @Test
    public void testAsMap_WithMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        Map<String, Object> result = MemoryConfiguration.asMap(map);
        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    public void testAsMap_WithNonMap() {
        String notAMap = "not a map";

        Map<String, Object> result = MemoryConfiguration.asMap(notAMap);
        assertNull(result);
    }

    @Test
    public void testAsMap_WithNull() {
        Map<String, Object> result = MemoryConfiguration.asMap(null);
        assertNull(result);
    }

    // ==================== asList Tests ====================

    @Test
    public void testAsList_WithList() {
        List<String> list = new ArrayList<>();
        list.add("item1");
        list.add("item2");

        List<?> result = MemoryConfiguration.asList(list);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("item1", result.get(0));
    }

    @Test
    public void testAsList_WithNonList() {
        String notAList = "not a list";

        List<?> result = MemoryConfiguration.asList(notAList);
        assertNull(result);
    }

    @Test
    public void testAsList_WithNull() {
        List<?> result = MemoryConfiguration.asList(null);
        assertNull(result);
    }

    // ==================== extractModelIdFromPipeline Tests ====================

    @Test
    public void testExtractModelIdFromPipeline_NullPipeline() {
        String result = MemoryConfiguration.extractModelIdFromPipeline(null);
        assertNull(result);
    }

    @Test
    public void testExtractModelIdFromPipeline_NullProcessors() {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("other_field", "value");

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertNull(result);
    }

    @Test
    public void testExtractModelIdFromPipeline_EmptyProcessors() {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("processors", new ArrayList<>());

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertNull(result);
    }

    @Test
    public void testExtractModelIdFromPipeline_TextEmbeddingProcessor() {
        Map<String, Object> textEmbeddingConfig = new HashMap<>();
        textEmbeddingConfig.put("model_id", "test-model-id");
        textEmbeddingConfig.put("field_map", new HashMap<>());

        Map<String, Object> processor = new HashMap<>();
        processor.put("text_embedding", textEmbeddingConfig);

        List<Object> processors = new ArrayList<>();
        processors.add(processor);

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("processors", processors);

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertEquals("test-model-id", result);
    }

    @Test
    public void testExtractModelIdFromPipeline_SparseEncodingProcessor() {
        Map<String, Object> sparseEncodingConfig = new HashMap<>();
        sparseEncodingConfig.put("model_id", "sparse-model-id");
        sparseEncodingConfig.put("field_map", new HashMap<>());

        Map<String, Object> processor = new HashMap<>();
        processor.put("sparse_encoding", sparseEncodingConfig);

        List<Object> processors = new ArrayList<>();
        processors.add(processor);

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("processors", processors);

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertEquals("sparse-model-id", result);
    }

    @Test
    public void testExtractModelIdFromPipeline_ModelIdNotString() {
        Map<String, Object> textEmbeddingConfig = new HashMap<>();
        textEmbeddingConfig.put("model_id", 123); // Not a String

        Map<String, Object> processor = new HashMap<>();
        processor.put("text_embedding", textEmbeddingConfig);

        List<Object> processors = new ArrayList<>();
        processors.add(processor);

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("processors", processors);

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertNull(result);
    }

    @Test
    public void testExtractModelIdFromPipeline_MalformedProcessor() {
        List<Object> processors = new ArrayList<>();
        processors.add("not-a-map"); // Malformed processor

        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("processors", processors);

        String result = MemoryConfiguration.extractModelIdFromPipeline(pipeline);
        assertNull(result);
    }

    // ==================== compareEmbeddingConfig Tests ====================

    @Test
    public void testCompareEmbeddingConfig_TextEmbeddingMatch() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.TEXT_EMBEDDING, 768);

        // Should not throw exception
        MemoryConfiguration.compareEmbeddingConfig(requested, "model-id", existing);
    }

    @Test
    public void testCompareEmbeddingConfig_SparseEncodingMatch() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("model-id")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.SPARSE_ENCODING, null);

        // Should not throw exception
        MemoryConfiguration.compareEmbeddingConfig(requested, "model-id", existing);
    }

    @Test
    public void testCompareEmbeddingConfig_ModelIdMismatch() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("different-model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.TEXT_EMBEDDING, 768);

        try {
            MemoryConfiguration.compareEmbeddingConfig(requested, "existing-model-id", existing);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("embedding_model_id"));
            assertTrue(e.getMessage().contains("existing='existing-model-id'"));
            assertTrue(e.getMessage().contains("requested='different-model-id'"));
        }
    }

    @Test
    public void testCompareEmbeddingConfig_ModelTypeMismatch() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.SPARSE_ENCODING, null);

        try {
            MemoryConfiguration.compareEmbeddingConfig(requested, "model-id", existing);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("embedding_model_type"));
            assertTrue(e.getMessage().contains("existing='SPARSE_ENCODING'"));
            assertTrue(e.getMessage().contains("requested='TEXT_EMBEDDING'"));
        }
    }

    @Test
    public void testCompareEmbeddingConfig_DimensionMismatch() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(1024)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.TEXT_EMBEDDING, 768);

        try {
            MemoryConfiguration.compareEmbeddingConfig(requested, "model-id", existing);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("dimension"));
            assertTrue(e.getMessage().contains("existing=768"));
            assertTrue(e.getMessage().contains("requested=1024"));
        }
    }

    @Test
    public void testCompareEmbeddingConfig_MultipleMismatches() {
        MemoryConfiguration requested = MemoryConfiguration
            .builder()
            .indexPrefix("test-prefix")
            .embeddingModelId("different-model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(1024)
            .build();

        EmbeddingConfig existing = new EmbeddingConfig(FunctionName.SPARSE_ENCODING, null);

        try {
            MemoryConfiguration.compareEmbeddingConfig(requested, "existing-model-id", existing);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Should contain all three mismatches
            assertTrue(e.getMessage().contains("embedding_model_id"));
            assertTrue(e.getMessage().contains("embedding_model_type"));
            assertTrue(e.getMessage().contains("test-prefix"));
        }
    }

    // ==================== getIndexName Tests ====================

    @Test
    public void testGetIndexName_NullMemoryType() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        String result = config.getIndexName(null);
        assertNull(result);
    }

    @Test
    public void testGetIndexName_SessionsDisabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableSession(true).build();

        String result = config.getIndexName(MemoryType.SESSIONS);
        assertNull(result);
    }

    @Test
    public void testGetIndexName_HistoryDisabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableHistory(true).build();

        String result = config.getIndexName(MemoryType.HISTORY);
        assertNull(result);
    }

    @Test
    public void testGetIndexName_SessionsEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableSession(false).build();

        String result = config.getIndexName(MemoryType.SESSIONS);
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-sessions"));
    }

    @Test
    public void testGetIndexName_Working() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        String result = config.getIndexName(MemoryType.WORKING);
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-working"));
    }

    @Test
    public void testGetIndexName_LongTerm() {
        MemoryConfiguration config = configWithLongTermMemory(null);

        String result = config.getIndexName(MemoryType.LONG_TERM);
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetIndexName_HistoryEnabled() {
        MemoryConfiguration config = configWithLongTermMemory(MemoryConfiguration.builder().disableHistory(false));

        String result = config.getIndexName(MemoryType.HISTORY);
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-history"));
    }

    // ==================== validate Tests ====================

    @Test
    public void testValidate_ValidConfiguration() {
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .embeddingModelId("model-id")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(768)
            .build();

        // Should not throw exception
        config.validate();
    }

    @Test
    public void testValidate_EmbeddingModelIdWithoutType() {
        try {
            MemoryConfiguration.builder().embeddingModelId("model-id").build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("embedding_model_type") || e.getMessage().contains("required"));
        }
    }

    @Test
    public void testValidate_EmbeddingModelTypeWithoutId() {
        try {
            MemoryConfiguration.builder().embeddingModelType(FunctionName.TEXT_EMBEDDING).dimension(768).build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("embedding_model_id") || e.getMessage().contains("required"));
        }
    }

    @Test
    public void testValidate_InvalidEmbeddingModelType() {
        try {
            MemoryConfiguration.builder().embeddingModelId("model-id").embeddingModelType(FunctionName.KMEANS).build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(
                e.getMessage().contains("TEXT_EMBEDDING")
                    || e.getMessage().contains("SPARSE_ENCODING")
                    || e.getMessage().contains("embedding model type")
            );
        }
    }

    @Test
    public void testValidate_TextEmbeddingWithoutDimension() {
        try {
            MemoryConfiguration.builder().embeddingModelId("model-id").embeddingModelType(FunctionName.TEXT_EMBEDDING).build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("dimension") || e.getMessage().contains("required"));
        }
    }

    @Test
    public void testValidate_SparseEncodingWithDimension() {
        try {
            MemoryConfiguration
                .builder()
                .embeddingModelId("model-id")
                .embeddingModelType(FunctionName.SPARSE_ENCODING)
                .dimension(768)
                .build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("dimension") || e.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void testValidate_MaxInferSizeExceedsLimit() {
        try {
            MemoryConfiguration
                .builder()
                .llmId("llm-id")
                .maxInferSize(15) // Exceeds limit of 10
                .embeddingModelId("model-id")
                .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                .dimension(768)
                .build();
            fail("Expected IllegalArgumentException during construction");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("10") || e.getMessage().contains("exceed"));
        }
    }

    // ==================== getSessionIndexName Tests ====================

    @Test
    public void testGetSessionIndexName_Enabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableSession(false).build();

        String result = config.getSessionIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-sessions"));
    }

    @Test
    public void testGetSessionIndexName_Disabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableSession(true).build();

        String result = config.getSessionIndexName();
        assertNull(result);
    }

    @Test
    public void testGetSessionIndexName_WithCustomPrefix() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("custom-prefix").disableSession(false).build();

        String result = config.getSessionIndexName();
        assertNotNull(result);
        assertTrue(result.contains("custom-prefix"));
        assertTrue(result.endsWith("-memory-sessions"));
    }

    // ==================== getWorkingMemoryIndexName Tests ====================

    @Test
    public void testGetWorkingMemoryIndexName_Default() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        String result = config.getWorkingMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-working"));
    }

    @Test
    public void testGetWorkingMemoryIndexName_WithCustomPrefix() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("my-prefix").build();

        String result = config.getWorkingMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains("my-prefix"));
        assertTrue(result.endsWith("-memory-working"));
    }

    @Test
    public void testGetWorkingMemoryIndexName_WithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();

        String result = config.getWorkingMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains(".plugins-ml-am-"));
        assertTrue(result.endsWith("-memory-working"));
    }

    @Test
    public void testGetWorkingMemoryIndexName_WithoutSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();

        String result = config.getWorkingMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-working"));
    }

    // ==================== getLongMemoryIndexName Tests ====================

    @Test
    public void testGetLongMemoryIndexName_Default() {
        MemoryConfiguration config = configWithLongTermMemory(null);

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetLongMemoryIndexName_WithCustomPrefix() {
        MemoryConfiguration config = configWithLongTermMemory(MemoryConfiguration.builder().indexPrefix("long-term-prefix"));

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains("long-term-prefix"));
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetLongMemoryIndexName_WithSystemIndex() {
        MemoryConfiguration config = configWithLongTermMemory(MemoryConfiguration.builder().useSystemIndex(true));

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains(".plugins-ml-am-"));
        assertTrue(result.endsWith("-memory-long-term"));
    }

    // ==================== getLongMemoryHistoryIndexName Tests ====================

    @Test
    public void testGetLongMemoryHistoryIndexName_Enabled() {
        MemoryConfiguration config = configWithLongTermMemory(MemoryConfiguration.builder().disableHistory(false));

        String result = config.getLongMemoryHistoryIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-history"));
    }

    @Test
    public void testGetLongMemoryHistoryIndexName_Disabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableHistory(true).build();

        String result = config.getLongMemoryHistoryIndexName();
        assertNull(result);
    }

    @Test
    public void testGetLongMemoryHistoryIndexName_WithCustomPrefix() {
        MemoryConfiguration config = configWithLongTermMemory(
            MemoryConfiguration.builder().indexPrefix("history-prefix").disableHistory(false)
        );

        String result = config.getLongMemoryHistoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains("history-prefix"));
        assertTrue(result.endsWith("-memory-history"));
    }

    // ==================== getMemoryIndexMapping Tests ====================

    @Test
    public void testGetMemoryIndexMapping_NullSettings() {
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        Map<String, Object> result = config.getMemoryIndexMapping("test-index");
        assertNull(result);
    }

    @Test
    public void testGetMemoryIndexMapping_NonExistentIndex() {
        Map<String, Map<String, Object>> indexSettings = new HashMap<>();
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("field1", "value1");
        indexSettings.put("existing-index", mapping);

        MemoryConfiguration config = MemoryConfiguration.builder().indexSettings(indexSettings).build();

        Map<String, Object> result = config.getMemoryIndexMapping("non-existent-index");
        assertNull(result);
    }

    @Test
    public void testGetMemoryIndexMapping_ExistingIndex() {
        Map<String, Map<String, Object>> indexSettings = new HashMap<>();
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("field1", "value1");
        mapping.put("field2", "value2");
        indexSettings.put("test-index", mapping);

        MemoryConfiguration config = MemoryConfiguration.builder().indexSettings(indexSettings).build();

        Map<String, Object> result = config.getMemoryIndexMapping("test-index");
        assertNotNull(result);
        assertEquals("value1", result.get("field1"));
        assertEquals("value2", result.get("field2"));
    }

    @Test
    public void testGetMemoryIndexMapping_MultipleIndices() {
        Map<String, Map<String, Object>> indexSettings = new HashMap<>();

        Map<String, Object> mapping1 = new HashMap<>();
        mapping1.put("type", "knn_vector");
        mapping1.put("dimension", 768);
        indexSettings.put("index1", mapping1);

        Map<String, Object> mapping2 = new HashMap<>();
        mapping2.put("type", "rank_features");
        indexSettings.put("index2", mapping2);

        MemoryConfiguration config = MemoryConfiguration.builder().indexSettings(indexSettings).build();

        Map<String, Object> result1 = config.getMemoryIndexMapping("index1");
        assertNotNull(result1);
        assertEquals("knn_vector", result1.get("type"));
        assertEquals(768, result1.get("dimension"));

        Map<String, Object> result2 = config.getMemoryIndexMapping("index2");
        assertNotNull(result2);
        assertEquals("rank_features", result2.get("type"));
    }

    @Test
    public void testGetMemoryIndexMapping_EmptySettings() {
        Map<String, Map<String, Object>> indexSettings = new HashMap<>();
        MemoryConfiguration config = MemoryConfiguration.builder().indexSettings(indexSettings).build();

        Map<String, Object> result = config.getMemoryIndexMapping("any-index");
        assertNull(result);
    }

    // ==================== buildIndexPrefix CRLF Injection Tests ====================

    @Test
    public void testBuildIndexPrefix_RejectsCarriageReturn() {
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\rinjection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_RejectsLineFeed() {
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\ninjection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_RejectsCRLF() {
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\r\ninjection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_RejectsTab() {
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\tinjection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_RejectsNullCharacter() {
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\u0000injection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_RejectsOtherControlCharacters() {
        // Test ASCII control character 1 (SOH - Start of Header)
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\u0001injection").build()
        );
        assertTrue(exception.getMessage().contains("control characters"));
    }

    @Test
    public void testBuildIndexPrefix_BackslashRejectedByMetadataCreateIndexService() {
        // Verify that backslash is rejected by OpenSearch's built-in validation
        // MetadataCreateIndexService.validateIndexOrAliasName handles this
        OpenSearchParseException exception = assertThrows(
            OpenSearchParseException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test\\backslash").build()
        );
        // The error message should come from MetadataCreateIndexService
        assertTrue(
            exception.getMessage().contains("invalid")
                || exception.getMessage().contains("index")
                || exception.getMessage().contains("prefix")
        );
    }

    @Test
    public void testBuildIndexPrefix_AcceptsValidPrefix() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("valid-prefix-123").build();
        assertEquals("valid-prefix-123", config.getIndexPrefix());
    }

    @Test
    public void testBuildIndexPrefix_AcceptsHyphenAndUnderscore() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("valid_prefix-with-chars").build();
        assertEquals("valid_prefix-with-chars", config.getIndexPrefix());
    }

    // ==================== Retention Policy Tests ====================

    @Test
    public void testParse_WithRetentionPolicy() throws Exception {
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"sessions\":{\"retention_days\":30,\"max_count\":100},"
            + "\"long-term\":{\"retention_days\":90,\"max_count\":500}}}";

        XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration config = MemoryConfiguration.parse(parser);

        assertNotNull(config.getRetentionPolicy());
        assertEquals(2, config.getRetentionPolicy().size());

        RetentionRule sessionsRule = config.getRetentionPolicy().get(MemoryType.SESSIONS);
        assertNotNull(sessionsRule);
        assertEquals(Integer.valueOf(30), sessionsRule.getRetentionDays());
        assertEquals(Integer.valueOf(100), sessionsRule.getMaxCount());

        RetentionRule longTermRule = config.getRetentionPolicy().get(MemoryType.LONG_TERM);
        assertNotNull(longTermRule);
        assertEquals(Integer.valueOf(90), longTermRule.getRetentionDays());
        assertEquals(Integer.valueOf(500), longTermRule.getMaxCount());
    }

    @Test
    public void testParse_RetentionPolicy_WorkingKeyRejected() {
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"working\":{\"max_count\":10}}}";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
                .xContent()
                .createParser(
                    org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                    org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                    json
                );
            parser.nextToken();
            MemoryConfiguration.parse(parser);
        });
        assertTrue(e.getMessage().contains("Working memory retention cannot be configured directly"));
        assertTrue(e.getMessage().contains("sessions"));
    }

    @Test
    public void testParse_RetentionPolicy_HistoryWithRetentionDaysRejected() {
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"history\":{\"retention_days\":30}}}";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
                .xContent()
                .createParser(
                    org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                    org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                    json
                );
            parser.nextToken();
            MemoryConfiguration.parse(parser);
        });
        assertTrue(e.getMessage().contains("retention_days is not supported for history memory type"));
    }

    @Test
    public void testParse_RetentionPolicy_UnknownKeyRejected() {
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"unknown_type\":{\"max_count\":10}}}";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
                .xContent()
                .createParser(
                    org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                    org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                    json
                );
            parser.nextToken();
            MemoryConfiguration.parse(parser);
        });
        assertTrue(e.getMessage().contains("unknown memory type: unknown_type"));
    }

    @Test
    public void testRetentionPolicy_XContentRoundTrip() throws Exception {
        Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        policy.put(MemoryType.LONG_TERM, new RetentionRule(90, null));
        policy.put(MemoryType.HISTORY, new RetentionRule(null, 1000));

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(policy).build();

        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.core.xcontent.MediaTypeRegistry
            .contentBuilder(org.opensearch.common.xcontent.XContentType.JSON);
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String json = org.opensearch.ml.common.TestHelper.xContentBuilderToString(builder);

        XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration parsed = MemoryConfiguration.parse(parser);

        assertNotNull(parsed.getRetentionPolicy());
        assertEquals(3, parsed.getRetentionPolicy().size());
        assertEquals(config.getRetentionPolicy().get(MemoryType.SESSIONS), parsed.getRetentionPolicy().get(MemoryType.SESSIONS));
        assertEquals(config.getRetentionPolicy().get(MemoryType.LONG_TERM), parsed.getRetentionPolicy().get(MemoryType.LONG_TERM));
        assertEquals(config.getRetentionPolicy().get(MemoryType.HISTORY), parsed.getRetentionPolicy().get(MemoryType.HISTORY));
    }

    @Test
    public void testRetentionPolicy_StreamRoundTrip() throws Exception {
        Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.SESSIONS, new RetentionRule(7, 50));
        policy.put(MemoryType.LONG_TERM, new RetentionRule(365, 10000));

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(policy).build();

        org.opensearch.common.io.stream.BytesStreamOutput output = new org.opensearch.common.io.stream.BytesStreamOutput();
        config.writeTo(output);

        org.opensearch.core.common.io.stream.StreamInput input = output.bytes().streamInput();
        MemoryConfiguration deserialized = new MemoryConfiguration(input);

        assertNotNull(deserialized.getRetentionPolicy());
        assertEquals(2, deserialized.getRetentionPolicy().size());
        assertEquals(config.getRetentionPolicy().get(MemoryType.SESSIONS), deserialized.getRetentionPolicy().get(MemoryType.SESSIONS));
        assertEquals(config.getRetentionPolicy().get(MemoryType.LONG_TERM), deserialized.getRetentionPolicy().get(MemoryType.LONG_TERM));
    }

    @Test
    public void testRetentionPolicy_StreamRoundTrip_NullPolicy() throws Exception {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();

        org.opensearch.common.io.stream.BytesStreamOutput output = new org.opensearch.common.io.stream.BytesStreamOutput();
        config.writeTo(output);

        org.opensearch.core.common.io.stream.StreamInput input = output.bytes().streamInput();
        MemoryConfiguration deserialized = new MemoryConfiguration(input);

        assertNull(deserialized.getRetentionPolicy());
    }

    @Test
    public void testRetentionPolicy_StreamRoundTrip_ExplicitlyNullFlag() throws Exception {
        // An explicit "retention_policy": null update must survive a node boundary so update() wipes the policy.
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        config.setRetentionPolicyExplicitlyNull(true);

        org.opensearch.common.io.stream.BytesStreamOutput output = new org.opensearch.common.io.stream.BytesStreamOutput();
        config.writeTo(output);

        org.opensearch.core.common.io.stream.StreamInput input = output.bytes().streamInput();
        MemoryConfiguration deserialized = new MemoryConfiguration(input);

        assertTrue(deserialized.isRetentionPolicyExplicitlyNull());

        // And the flag defaults to false when not set.
        MemoryConfiguration defaultConfig = MemoryConfiguration.builder().indexPrefix("test").build();
        org.opensearch.common.io.stream.BytesStreamOutput defaultOutput = new org.opensearch.common.io.stream.BytesStreamOutput();
        defaultConfig.writeTo(defaultOutput);
        MemoryConfiguration deserializedDefault = new MemoryConfiguration(defaultOutput.bytes().streamInput());
        assertFalse(deserializedDefault.isRetentionPolicyExplicitlyNull());
    }

    @Test
    public void testUpdate_RetentionPolicy_FieldLevelMerge() {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        existingPolicy.put(MemoryType.LONG_TERM, new RetentionRule(90, 500));

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // Update sessions with only max_count — field-level merge preserves existing retention_days
        // Use 4-arg constructor to simulate JSON parse: max_count explicitly set, retention_days absent
        Map<MemoryType, RetentionRule> updatePolicy = new java.util.EnumMap<>(MemoryType.class);
        updatePolicy.put(MemoryType.SESSIONS, new RetentionRule(null, 200, false, true));

        MemoryConfiguration updateContent = MemoryConfiguration.builder().retentionPolicy(updatePolicy).build();
        config.update(updateContent);

        // Sessions: field-level merge — retention_days preserved (not explicitly set in update), max_count updated
        RetentionRule sessionsRule = config.getRetentionPolicy().get(MemoryType.SESSIONS);
        assertEquals(Integer.valueOf(30), sessionsRule.getRetentionDays());
        assertEquals(Integer.valueOf(200), sessionsRule.getMaxCount());

        // Long-term: untouched (type not in update)
        RetentionRule longTermRule = config.getRetentionPolicy().get(MemoryType.LONG_TERM);
        assertEquals(Integer.valueOf(90), longTermRule.getRetentionDays());
        assertEquals(Integer.valueOf(500), longTermRule.getMaxCount());
    }

    @Test
    public void testUpdate_RetentionPolicy_AddNewType() {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        Map<MemoryType, RetentionRule> updatePolicy = new java.util.EnumMap<>(MemoryType.class);
        updatePolicy.put(MemoryType.HISTORY, new RetentionRule(null, 1000));

        MemoryConfiguration updateContent = MemoryConfiguration.builder().retentionPolicy(updatePolicy).build();
        config.update(updateContent);

        assertEquals(2, config.getRetentionPolicy().size());
        assertNotNull(config.getRetentionPolicy().get(MemoryType.SESSIONS));
        assertEquals(Integer.valueOf(1000), config.getRetentionPolicy().get(MemoryType.HISTORY).getMaxCount());
    }

    @Test
    public void testUpdate_RetentionPolicy_NullIncomingDoesNotRemove() {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // Update without retention_policy field — should not touch existing
        MemoryConfiguration updateContent = MemoryConfiguration.builder().llmId("new-llm").build();
        config.update(updateContent);

        assertNotNull(config.getRetentionPolicy());
        assertEquals(1, config.getRetentionPolicy().size());
    }

    @Test
    public void testParse_RetentionPolicy_HistoryWithMaxCountOnly() throws Exception {
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"history\":{\"max_count\":500}}}";

        XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration config = MemoryConfiguration.parse(parser);

        assertNotNull(config.getRetentionPolicy());
        RetentionRule historyRule = config.getRetentionPolicy().get(MemoryType.HISTORY);
        assertNotNull(historyRule);
        assertNull(historyRule.getRetentionDays());
        assertEquals(Integer.valueOf(500), historyRule.getMaxCount());
    }

    @Test
    public void testRetentionPolicy_ConstructorValidation_RejectsWorkingKey() {
        Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.WORKING, new RetentionRule(null, 10));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(policy).build()
        );
        assertTrue(e.getMessage().contains("Working memory retention cannot be configured directly"));
    }

    @Test
    public void testRetentionPolicy_ConstructorValidation_RejectsHistoryRetentionDays() {
        Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.HISTORY, new RetentionRule(30, null));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(policy).build()
        );
        assertTrue(e.getMessage().contains("retention_days is not supported for history memory type"));
    }

    @Test
    public void testUpdate_RetentionPolicy_ExplicitNullWipesPolicy() throws Exception {
        // Setup: config with existing policy
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // Simulate parsing an update with "retention_policy": null
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":null}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        // Verify the flag is set
        assertTrue(updateContent.isRetentionPolicyExplicitlyNull());

        // Apply update
        config.update(updateContent);

        // Policy should be wiped
        assertNull(config.getRetentionPolicy());
        // The wipe intent must propagate so toXContent emits an explicit null (required for the
        // UpdateRequest.doc() partial merge to actually remove the stored policy).
        assertTrue(config.isRetentionPolicyExplicitlyNull());
        String serialized = org.opensearch.ml.common.TestHelper
            .xContentBuilderToString(
                config
                    .toXContent(
                        org.opensearch.common.xcontent.XContentFactory.jsonBuilder(),
                        org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS
                    )
            );
        assertTrue("wiped config must serialize an explicit retention_policy:null", serialized.contains("\"retention_policy\":null"));
    }

    @Test
    public void testUpdate_RetentionPolicy_SubsequentNonNullClearsWipeFlag() {
        // A wipe followed by a normal non-null retention update must clear the transient wipe flag,
        // otherwise a stale flag would emit a spurious retention_policy:null.
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // First: explicit-null wipe
        MemoryConfiguration wipe = MemoryConfiguration.builder().build();
        wipe.setRetentionPolicyExplicitlyNull(true);
        config.update(wipe);
        assertNull(config.getRetentionPolicy());
        assertTrue(config.isRetentionPolicyExplicitlyNull());

        // Then: a normal non-null policy update
        Map<MemoryType, RetentionRule> newPolicy = new java.util.EnumMap<>(MemoryType.class);
        newPolicy.put(MemoryType.LONG_TERM, new RetentionRule(90, null));
        MemoryConfiguration apply = MemoryConfiguration.builder().retentionPolicy(newPolicy).build();
        config.update(apply);

        assertNotNull(config.getRetentionPolicy());
        assertFalse("wipe flag must be cleared after a non-null policy update", config.isRetentionPolicyExplicitlyNull());
    }

    @Test
    public void testUpdate_RetentionPolicy_AbsentDoesNotWipe() throws Exception {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // Parse an update WITHOUT retention_policy field
        String json = "{\"index_prefix\":\"test\"}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        // Flag should NOT be set
        assertFalse(updateContent.isRetentionPolicyExplicitlyNull());

        // Apply update
        config.update(updateContent);

        // Policy should still be there
        assertNotNull(config.getRetentionPolicy());
        assertEquals(1, config.getRetentionPolicy().size());
    }

    @Test
    public void testUpdate_RetentionPolicy_ExplicitNullFieldRemovalPersists() throws Exception {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // Simulate parsing an update that explicitly clears max_count
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"sessions\":{\"max_count\":null}}}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        config.update(updateContent);

        // Merged rule: retention_days preserved, max_count cleared with the explicit-set flag carried through
        RetentionRule mergedRule = config.getRetentionPolicy().get(MemoryType.SESSIONS);
        assertEquals(Integer.valueOf(30), mergedRule.getRetentionDays());
        assertNull(mergedRule.getMaxCount());
        assertTrue(mergedRule.isMaxCountExplicitlySet());

        // Serialized update doc must emit the explicit null so the partial-update merge removes it
        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String serialized = builder.toString();
        assertTrue("expected explicit null max_count in: " + serialized, serialized.contains("\"max_count\":null"));
        assertTrue("expected retention_days preserved in: " + serialized, serialized.contains("\"retention_days\":30"));
    }

    @Test
    public void testUpdate_RetentionPolicy_ExplicitNullRetentionDaysPersists() throws Exception {
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"sessions\":{\"retention_days\":null}}}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        config.update(updateContent);

        RetentionRule mergedRule = config.getRetentionPolicy().get(MemoryType.SESSIONS);
        assertNull(mergedRule.getRetentionDays());
        assertEquals(Integer.valueOf(100), mergedRule.getMaxCount());
        assertTrue(mergedRule.isRetentionDaysExplicitlySet());

        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String serialized = builder.toString();
        assertTrue("expected explicit null retention_days in: " + serialized, serialized.contains("\"retention_days\":null"));
        assertTrue("expected max_count preserved in: " + serialized, serialized.contains("\"max_count\":100"));
    }

    @Test
    public void testUpdate_RetentionPolicy_AbsentFieldsOmittedAfterMerge() throws Exception {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();

        // Update introduces a rule that only mentions max_count; retention_days was never mentioned
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":{\"sessions\":{\"max_count\":200}}}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        config.update(updateContent);

        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String serialized = builder.toString();
        assertTrue("expected max_count in: " + serialized, serialized.contains("\"max_count\":200"));
        assertFalse("never-mentioned retention_days must be omitted in: " + serialized, serialized.contains("retention_days"));
    }

    @Test
    public void testRetentionPolicyExplicitNull_RoundTripsThroughXContent() throws Exception {
        // Parse a config with explicit "retention_policy": null (opt-out)
        String json = "{\"index_prefix\":\"test\",\"retention_policy\":null}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration config = MemoryConfiguration.parse(parser);
        assertTrue(config.isRetentionPolicyExplicitlyNull());

        // Serialize: the opt-out marker must be written as an explicit null field
        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String serialized = builder.toString();
        assertTrue("expected explicit null marker in: " + serialized, serialized.contains("\"retention_policy\":null"));

        // Parse back (simulates reading the container document from the index)
        org.opensearch.core.xcontent.XContentParser reparser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                serialized
            );
        reparser.nextToken();
        MemoryConfiguration roundTripped = MemoryConfiguration.parse(reparser);

        // Opt-out must survive the round trip so the retention job skips backfill
        assertTrue(roundTripped.isRetentionPolicyExplicitlyNull());
        assertNull(roundTripped.getRetentionPolicy());
    }

    @Test
    public void testToXContent_NoRetentionPolicy_OmitsField() throws Exception {
        // "Never had a policy" must remain field-absence (no null marker)
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        String serialized = builder.toString();
        assertFalse("retention_policy should be absent in: " + serialized, serialized.contains("retention_policy"));
    }

    @Test
    public void testUpdate_ExplicitNull_PropagatesOptOutFlagForPersistence() throws Exception {
        // Existing stored config with a policy
        Map<MemoryType, RetentionRule> existingPolicy = new java.util.EnumMap<>(MemoryType.class);
        existingPolicy.put(MemoryType.SESSIONS, new RetentionRule(30, 100));
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").retentionPolicy(existingPolicy).build();

        // PUT with "retention_policy": null
        String json = "{\"retention_policy\":null}";
        org.opensearch.core.xcontent.XContentParser parser = org.opensearch.common.xcontent.XContentType.JSON
            .xContent()
            .createParser(
                org.opensearch.core.xcontent.NamedXContentRegistry.EMPTY,
                org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE,
                json
            );
        parser.nextToken();
        MemoryConfiguration updateContent = MemoryConfiguration.parse(parser);

        config.update(updateContent);

        // Merged config must carry the opt-out so serialization writes the null marker,
        // which removes the old policy during the partial-update doc merge
        assertNull(config.getRetentionPolicy());
        assertTrue(config.isRetentionPolicyExplicitlyNull());

        org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
        config.toXContent(builder, org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS);
        assertTrue(builder.toString().contains("\"retention_policy\":null"));
    }

    @Test
    public void testUpdate_NewPolicyClearsOptOutFlag() throws Exception {
        // Config previously opted out
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        config.setRetentionPolicyExplicitlyNull(true);

        // PUT with a concrete policy opts back in
        Map<MemoryType, RetentionRule> newPolicy = new java.util.EnumMap<>(MemoryType.class);
        newPolicy.put(MemoryType.SESSIONS, new RetentionRule(7, 50));
        MemoryConfiguration updateContent = MemoryConfiguration.builder().retentionPolicy(newPolicy).build();

        config.update(updateContent);

        assertFalse(config.isRetentionPolicyExplicitlyNull());
        assertNotNull(config.getRetentionPolicy());
    }

    @Test
    public void testRetentionPolicyExplicitNull_RoundTripsThroughStream() throws Exception {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").build();
        config.setRetentionPolicyExplicitlyNull(true);

        org.opensearch.common.io.stream.BytesStreamOutput out = new org.opensearch.common.io.stream.BytesStreamOutput();
        config.writeTo(out);
        MemoryConfiguration deserialized = new MemoryConfiguration(out.bytes().streamInput());

        assertTrue(deserialized.isRetentionPolicyExplicitlyNull());
        assertNull(deserialized.getRetentionPolicy());
    }
}
