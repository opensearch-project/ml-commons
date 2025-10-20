/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration.EmbeddingConfig;

/**
 * Unit tests for MemoryConfiguration class methods.
 */
public class MemoryConfigurationTests {

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
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        String result = config.getIndexName(MemoryType.LONG_TERM);
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetIndexName_HistoryEnabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableHistory(false).build();

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
        MemoryConfiguration config = MemoryConfiguration.builder().build();

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetLongMemoryIndexName_WithCustomPrefix() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("long-term-prefix").build();

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains("long-term-prefix"));
        assertTrue(result.endsWith("-memory-long-term"));
    }

    @Test
    public void testGetLongMemoryIndexName_WithSystemIndex() {
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();

        String result = config.getLongMemoryIndexName();
        assertNotNull(result);
        assertTrue(result.contains(".plugins-ml-am-"));
        assertTrue(result.endsWith("-memory-long-term"));
    }

    // ==================== getLongMemoryHistoryIndexName Tests ====================

    @Test
    public void testGetLongMemoryHistoryIndexName_Enabled() {
        MemoryConfiguration config = MemoryConfiguration.builder().disableHistory(false).build();

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
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("history-prefix").disableHistory(false).build();

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

    // ==================== RemoteStore Tests ====================

    @Test
    public void testMemoryConfiguration_WithRemoteStore() {
        RemoteStore remoteStore = RemoteStore.builder().type("aoss").connectorId("ySf08JkBym-3qj1O2uub").build();

        MemoryConfiguration config = MemoryConfiguration
                .builder()
                .indexPrefix("test")
                .useSystemIndex(false)
                .remoteStore(remoteStore)
                .build();

        assertNotNull(config.getRemoteStore());
        assertEquals("aoss", config.getRemoteStore().getType());
        assertEquals("ySf08JkBym-3qj1O2uub", config.getRemoteStore().getConnectorId());
    }

    @Test
    public void testMemoryConfiguration_WithoutRemoteStore() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").useSystemIndex(false).build();

        assertNull(config.getRemoteStore());
    }

    @Test
    public void testMemoryConfiguration_UpdateRemoteStore() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").useSystemIndex(false).build();

        assertNull(config.getRemoteStore());

        RemoteStore remoteStore = RemoteStore.builder().type("aoss").connectorId("ySf08JkBym-3qj1O2uub").build();

        MemoryConfiguration updateContent = MemoryConfiguration.builder().remoteStore(remoteStore).build();

        config.update(updateContent);

        assertNotNull(config.getRemoteStore());
        assertEquals("aoss", config.getRemoteStore().getType());
        assertEquals("ySf08JkBym-3qj1O2uub", config.getRemoteStore().getConnectorId());
    }
}
