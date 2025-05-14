/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class RemoteModelConfigTests {

    RemoteModelConfig config;
    Function<XContentParser, RemoteModelConfig> function;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        config = RemoteModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
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
                + "\"additional_config\":{\"space_type\":\"l2\"}}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model type is null");
        config = RemoteModelConfig.builder().build();
    }

    @Test
    public void textEmbedding_MissingEmbeddingDimension() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Embedding dimension must be provided for remote text embedding model");
        RemoteModelConfig.builder().modelType("text_embedding").additionalConfig(Map.of("space_type", "l2")).build();
    }

    @Test
    public void textEmbedding_MissingFrameworkType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Framework type must be provided for remote text embedding model");
        RemoteModelConfig.builder().modelType("text_embedding").embeddingDimension(100).build();
    }

    @Test
    public void textEmbedding_MissingSpaceType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Space type must be provided in additional_config for remote text embedding model");
        RemoteModelConfig
            .builder()
            .modelType("text_embedding")
            .embeddingDimension(100)
            .frameworkType(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .build();
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
            + "\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\","
            + "\"additional_config\":{\"space_type\":\"l2\"}}";
        TestHelper.testParseFromString(config, content, function);
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
        // assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }
}
