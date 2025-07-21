/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class TextEmbeddingModelConfigTests {

    TextEmbeddingModelConfig config;
    Function<XContentParser, TextEmbeddingModelConfig> function;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .passagePrefix("passage: ")
            .queryPrefix("query: ")
            .build();
        function = parser -> {
            try {
                return TextEmbeddingModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse TextEmbeddingModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"additional_config\":{\"space_type\":\"l2\"},\"query_prefix\":\"query: \",\"passage_prefix\":\"passage: \"}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model type is null");
        config = TextEmbeddingModelConfig.builder().build();
    }

    @Test
    public void nullFields_EmbeddingDimension() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("embedding dimension is null");
        config = TextEmbeddingModelConfig.builder().modelType("testModelType").build();
    }

    @Test
    public void nullFields_FrameworkType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("framework type is null");
        config = TextEmbeddingModelConfig.builder().modelType("testModelType").embeddingDimension(100).build();
    }

    @Test
    public void parse() throws IOException {
        String content =
            "{\"wrong_field\":\"test_value\", \"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"query_prefix\":\"query: \",\"passage_prefix\":\"passage: \",\"additional_config\":{\"space_type\":\"l2\"}}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void frameworkType_wrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong framework type");
        TextEmbeddingModelConfig.FrameworkType.from("test_wrong_value");
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(config);
    }

    public void readInputStream(TextEmbeddingModelConfig config) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        TextEmbeddingModelConfig parsedConfig = new TextEmbeddingModelConfig(streamInput);
        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getEmbeddingDimension(), parsedConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
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
        TextEmbeddingModelConfig oldConfig = new TextEmbeddingModelConfig(oldIn);

        // Verify essential fields with old version
        assertEquals(config.getModelType(), oldConfig.getModelType());
        assertEquals(config.getAllConfig(), oldConfig.getAllConfig());
        assertNull(oldConfig.getAdditionalConfig());
        assertEquals(config.getEmbeddingDimension(), oldConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), oldConfig.getFrameworkType());
        assertEquals(config.getWriteableName(), oldConfig.getWriteableName());
        assertEquals(config.getQueryPrefix(), oldConfig.getQueryPrefix());
        assertEquals(config.getPassagePrefix(), oldConfig.getPassagePrefix());

        // Test with newer version
        BytesStreamOutput currentOut = new BytesStreamOutput();
        currentOut.setVersion(Version.V_3_1_0);
        config.writeTo(currentOut);

        StreamInput currentIn = currentOut.bytes().streamInput();
        currentIn.setVersion(Version.V_3_1_0);
        TextEmbeddingModelConfig newConfig = new TextEmbeddingModelConfig(currentIn);

        // Verify fields with current version
        assertEquals(config.getModelType(), newConfig.getModelType());
        assertEquals(config.getAllConfig(), newConfig.getAllConfig());
        assertEquals(config.getAdditionalConfig(), newConfig.getAdditionalConfig());
        assertEquals(config.getEmbeddingDimension(), newConfig.getEmbeddingDimension());
        assertEquals(config.getFrameworkType(), newConfig.getFrameworkType());
        assertEquals(config.getWriteableName(), newConfig.getWriteableName());
        assertEquals(config.getQueryPrefix(), newConfig.getQueryPrefix());
        assertEquals(config.getPassagePrefix(), newConfig.getPassagePrefix());
    }
}
