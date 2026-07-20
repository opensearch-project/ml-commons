/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.common.CommonValue.VERSION_3_0_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_1_0;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class BaseModelConfigTests {

    BaseModelConfig config;
    Function<XContentParser, BaseModelConfig> function;

    @Before
    public void setUp() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        config = BaseModelConfig
            .baseModelConfigBuilder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .embeddingDimension(768)
            .frameworkType(BaseModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .passagePrefix("passage: ")
            .queryPrefix("query: ")
            .build();

        function = parser -> {
            try {
                return BaseModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse BaseModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"additional_config\":{\"space_type\":\"l2\"},"
                + "\"embedding_dimension\":768,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"query_prefix\":\"query: \",\"passage_prefix\":\"passage: \"}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config = BaseModelConfig.baseModelConfigBuilder().build()
        );
        assertEquals("model type is null", exception.getMessage());
    }

    @Test
    public void parse() throws IOException {
        String content =
            "{\"wrong_field\":\"test_value\", \"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"query_prefix\":\"query: \",\"passage_prefix\":\"passage: \"}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void testStreamInputVersionAfter_3_1_0() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(VERSION_3_1_0);
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(VERSION_3_1_0);
        BaseModelConfig parsedConfig = new BaseModelConfig(streamInput);

        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getAdditionalConfig(), parsedConfig.getAdditionalConfig());
        assertEquals(config.getEmbeddingDimension(), parsedConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getQueryPrefix(), parsedConfig.getQueryPrefix());
        assertEquals(config.getPassagePrefix(), parsedConfig.getPassagePrefix());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }

    @Test
    public void testStreamInputVersionBefore_3_1_0() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(VERSION_3_0_0);
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(VERSION_3_0_0);
        BaseModelConfig parsedConfig = new BaseModelConfig(streamInput);

        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertNull(parsedConfig.getAdditionalConfig());
        assertEquals(config.getEmbeddingDimension(), parsedConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getQueryPrefix(), parsedConfig.getQueryPrefix());
        assertEquals(config.getPassagePrefix(), parsedConfig.getPassagePrefix());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }

    @Test
    public void duplicateKeys() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            String allConfig = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
            Map<String, Object> additionalConfig = Map.of("key1", "value3");

            BaseModelConfig
                .baseModelConfigBuilder()
                .allConfig(allConfig)
                .modelType("testModelType")
                .additionalConfig(additionalConfig)
                .build();
        });
        assertEquals("Duplicate keys found in both all_config and additional_config: key1", exception.getMessage());
    }
}
