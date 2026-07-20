/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class QuestionAnsweringModelConfigTests {

    QuestionAnsweringModelConfig config;
    Function<XContentParser, QuestionAnsweringModelConfig> function;

    @Before
    public void setUp() {
        config = QuestionAnsweringModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .normalizeResult(false)
            .frameworkType(QuestionAnsweringModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .build();
        function = parser -> {
            try {
                return QuestionAnsweringModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse QuestionAnsweringModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\",\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config = QuestionAnsweringModelConfig.builder().build()
        );
        assertEquals("model type is null", exception.getMessage());
    }

    @Test
    public void nullFields_FrameworkType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config = QuestionAnsweringModelConfig.builder().modelType("testModelType").build()
        );
        assertEquals("framework type is null", exception.getMessage());
    }

    @Test
    public void parse() throws IOException {
        String content =
            "{\"wrong_field\":\"test_value\", \"model_type\":\"testModelType\",\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"normalize_result\":false,\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void frameworkType_wrongValue() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuestionAnsweringModelConfig.FrameworkType.from("test_wrong_value")
        );
        assertEquals("Wrong framework type", exception.getMessage());
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(config);
    }

    public void readInputStream(QuestionAnsweringModelConfig config) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        QuestionAnsweringModelConfig parsedConfig = new QuestionAnsweringModelConfig(streamInput);
        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getFrameworkType(), parsedConfig.getFrameworkType());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }
}
