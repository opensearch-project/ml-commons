/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class RemoteModelConfigTests {

    RemoteModelConfig config;
    Function<XContentParser, RemoteModelConfig> function;

    @Before
    public void setUp() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        config = RemoteModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .poolingMode(RemoteModelConfig.PoolingMode.MEAN)
            .embeddingDimension(100)
            .normalizeResult(true)
            .modelMaxLength(512)
            .additionalConfig(additionalConfig)
            .build();
        function = parser -> {
            try {
                return RemoteModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse RemoteModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\","
                + "\"embedding_dimension\":100,"
                + "\"framework_type\":\"SENTENCE_TRANSFORMERS\","
                + "\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\","
                + "\"pooling_mode\":\"MEAN\",\"normalize_result\":true,"
                + "\"model_max_length\":512,"
                + "\"additional_config\":{\"space_type\":\"l2\"}}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config = RemoteModelConfig.builder().build()
        );
        assertEquals("model type is null", exception.getMessage());
    }

    @Test
    public void textEmbedding_MissingEmbeddingDimension() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RemoteModelConfig.builder().modelType("text_embedding").additionalConfig(Map.of("space_type", "l2")).build()
        );
        assertEquals("Embedding dimension must be provided for remote text embedding model", exception.getMessage());
    }

    @Test
    public void textEmbedding_MissingFrameworkType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RemoteModelConfig.builder().modelType("text_embedding").embeddingDimension(100).build()
        );
        assertEquals("Framework type must be provided for remote text embedding model", exception.getMessage());
    }

    @Test
    public void textEmbedding_MissingSpaceType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            RemoteModelConfig
                .builder()
                .modelType("text_embedding")
                .embeddingDimension(100)
                .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .build();
        });
        assertEquals("Space type must be provided in additional_config for remote text embedding model", exception.getMessage());
    }

    @Test
    public void textEmbedding_ValidConfig() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        RemoteModelConfig config = RemoteModelConfig
            .builder()
            .modelType("text_embedding")
            .embeddingDimension(100)
            .additionalConfig(additionalConfig)
            .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .build();
        assertEquals("text_embedding", config.getModelType());
        assertEquals(Integer.valueOf(100), config.getEmbeddingDimension());
        assertEquals("l2", config.getAdditionalConfig().get("space_type"));
    }

    @Test
    public void parse() throws IOException {
        String content = "{\"model_type\":\"testModelType\","
            + "\"embedding_dimension\":100,"
            + "\"framework_type\":\"SENTENCE_TRANSFORMERS\","
            + "\"pooling_mode\":\"MEAN\",\"normalize_result\":true,"
            + "\"model_max_length\":512,"
            + "\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\","
            + "\"additional_config\":{\"space_type\":\"l2\"},"
            + "\"wrong_filed\":\"test_value\"}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void frameworkType_wrongValue() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RemoteModelConfig.FrameworkType.from("test_wrong_value")
        );
        assertEquals("Wrong framework type", exception.getMessage());
    }

    @Test
    public void poolingMode_wrongValue() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RemoteModelConfig.PoolingMode.from("test_wrong_value")
        );
        assertEquals("Wrong pooling method", exception.getMessage());
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(config);
    }

    public void readInputStream(RemoteModelConfig config) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        RemoteModelConfig parsedConfig = new RemoteModelConfig(streamInput);
        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getEmbeddingDimension(), parsedConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getPoolingMode(), parsedConfig.getPoolingMode());
        assertEquals(config.isNormalizeResult(), parsedConfig.isNormalizeResult());
        assertEquals(config.getModelMaxLength(), parsedConfig.getModelMaxLength());
        assertEquals(config.getAdditionalConfig(), parsedConfig.getAdditionalConfig());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }

    @Test
    public void readInputStream_VersionCompatibility() throws IOException {
        // Test with older version
        BytesStreamOutput oldOut = new BytesStreamOutput();
        Version oldVersion = Version.V_3_0_0;
        oldOut.setVersion(oldVersion);
        config.writeTo(oldOut);

        StreamInput oldIn = oldOut.bytes().streamInput();
        oldIn.setVersion(oldVersion);
        RemoteModelConfig oldConfig = new RemoteModelConfig(oldIn);

        // Verify essential fields with old version
        assertEquals(config.getModelType(), oldConfig.getModelType());
        assertEquals(config.getAllConfig(), oldConfig.getAllConfig());
        assertEquals(config.getEmbeddingDimension(), oldConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), oldConfig.getFrameworkType());
        assertEquals(config.getPoolingMode(), oldConfig.getPoolingMode());
        assertEquals(config.isNormalizeResult(), oldConfig.isNormalizeResult());
        assertEquals(config.getModelMaxLength(), oldConfig.getModelMaxLength());
        assertNull(oldConfig.getAdditionalConfig());
        assertEquals(config.getWriteableName(), oldConfig.getWriteableName());

        // Test with newer version
        BytesStreamOutput currentOut = new BytesStreamOutput();
        currentOut.setVersion(Version.V_3_1_0);
        config.writeTo(currentOut);

        StreamInput currentIn = currentOut.bytes().streamInput();
        currentIn.setVersion(Version.V_3_1_0);
        RemoteModelConfig newConfig = new RemoteModelConfig(currentIn);

        // Verify fields with current version
        assertEquals(config.getModelType(), newConfig.getModelType());
        assertEquals(config.getAllConfig(), newConfig.getAllConfig());
        assertEquals(config.getEmbeddingDimension(), newConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), newConfig.getFrameworkType());
        assertEquals(config.getPoolingMode(), newConfig.getPoolingMode());
        assertEquals(config.isNormalizeResult(), newConfig.isNormalizeResult());
        assertEquals(config.getModelMaxLength(), newConfig.getModelMaxLength());
        assertEquals(config.getAdditionalConfig(), newConfig.getAdditionalConfig());
        assertEquals(config.getWriteableName(), newConfig.getWriteableName());
    }
}
