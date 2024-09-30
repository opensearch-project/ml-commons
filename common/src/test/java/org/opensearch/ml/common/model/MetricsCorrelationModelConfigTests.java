/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
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

public class MetricsCorrelationModelConfigTests {

    MetricsCorrelationModelConfig config;
    Function<XContentParser, MetricsCorrelationModelConfig> function;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        config = MetricsCorrelationModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .build();
        function = parser -> {
            try {
                return MetricsCorrelationModelConfig.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse MetricsCorrelationModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        config.toXContent(builder, EMPTY_PARAMS);
        String configContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"}",
            configContent
        );
    }

    @Test
    public void nullFields_ModelType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model type is null");
        config = MetricsCorrelationModelConfig.builder().build();
    }

    @Test
    public void parse() throws IOException {
        String content =
            "{\"wrong_field\":\"test_value\", \"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"}";
        TestHelper.testParseFromString(config, content, function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(config);
    }

    public void readInputStream(MetricsCorrelationModelConfig config) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        config.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MetricsCorrelationModelConfig parsedConfig = new MetricsCorrelationModelConfig(streamInput);
        assertEquals(config.getModelType(), parsedConfig.getModelType());
        assertEquals(config.getAllConfig(), parsedConfig.getAllConfig());
        assertEquals(config.getWriteableName(), parsedConfig.getWriteableName());
    }
}
