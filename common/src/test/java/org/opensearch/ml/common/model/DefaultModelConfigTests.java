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

public class DefaultModelConfigTests {

    DefaultModelConfig config;
    Function<XContentParser, DefaultModelConfig> function;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("test_key1", "test_value1");

        config = DefaultModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(DefaultModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .poolingMode(DefaultModelConfig.PoolingMode.MEAN)
            .normalizeResult(true)
            .modelMaxLength(512)
            .additionalConfig(additionalConfig)
            .build();

        function = parser -> {
            try {
                return DefaultModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse DefaultModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"pooling_mode\":\"MEAN\",\"normalize_result\":true,\"model_max_length\":512,\"additional_config\":{\"test_key1\":\"test_value1\"}}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model type is null");
        config = DefaultModelConfig.builder().build();
    }

    @Test
    public void parse() throws IOException {
        String content =
            "{\"wrong_field\":\"test_value\", \"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"query_prefix\":\"query: \",\"passage_prefix\":\"passage: \"}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void frameworkType_wrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong framework type");
        DefaultModelConfig.FrameworkType.from("test_wrong_value");
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(config);
    }

    public void readInputStream(DefaultModelConfig config) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        DefaultModelConfig parsedConfig = new DefaultModelConfig(streamInput);
        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getAdditionalConfig(), parsedConfig.getAdditionalConfig());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }

    @Test
    public void duplicateKeys() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Duplicate keys found in both all_config and additional_config: key1");

        String allConfig = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        Map<String, Object> additionalConfig = Map.of("key1", "value3");

        DefaultModelConfig.builder().allConfig(allConfig).modelType("testModelType").additionalConfig(additionalConfig).build();
    }
}
