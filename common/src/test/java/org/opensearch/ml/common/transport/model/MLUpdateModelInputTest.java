/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.search.SearchModule;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

public class MLUpdateModelInputTest {

    private MLUpdateModelInput updateModelInput;
    private final String expectedInputStr = "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\",\"model_version\":"
            +
            "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":" +
            "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":" +
            "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
            +
            "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"updated_connector\":" +
            "{\"name\":\"test\",\"version\":\"1\",\"protocol\":\"http\",\"parameters\":{\"param1\":\"value1\"},\"credential\":"
            +
            "{\"api_key\":\"credential_value\"},\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":"
            +
            "\"https://api.openai.com/v1/chat/completions\",\"headers\":{\"Authorization\":\"Bearer ${credential.api_key}\"},\"request_body\":"
            +
            "\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages} }\"}]},\"connector_id\":"
            +
            "\"test-connector_id\",\"connector\":{\"description\":\"updated description\",\"version\":\"1\"},\"last_updated_time\":1}";

    private final String expectedOutputStrForUpdateRequestDoc = "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\",\"model_version\":"
            +
            "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":" +
            "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":" +
            "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
            +
            "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector\":" +
            "{\"name\":\"test\",\"version\":\"1\",\"protocol\":\"http\",\"parameters\":{\"param1\":\"value1\"},\"credential\":"
            +
            "{\"api_key\":\"credential_value\"},\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":"
            +
            "\"https://api.openai.com/v1/chat/completions\",\"headers\":{\"Authorization\":\"Bearer ${credential.api_key}\"},\"request_body\":"
            +
            "\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages} }\"}]},\"connector_id\":"
            +
            "\"test-connector_id\",\"last_updated_time\":1}";

    private final String expectedOutputStr = "{\"model_id\":null,\"name\":\"name\",\"description\":\"description\",\"model_group_id\":"
            +
            "\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":" +
            "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":" +
            "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
            +
            "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":" +
            "\"test-connector_id\"}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MLModelConfig config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();

        Connector updatedConnector = HttpConnector
                .builder()
                .name("test")
                .protocol("http")
                .version("1")
                .credential(Map.of("api_key", "credential_value"))
                .parameters(Map.of("param1", "value1"))
                .actions(
                        Arrays
                                .asList(
                                        ConnectorAction
                                                .builder()
                                                .actionType(ConnectorAction.ActionType.PREDICT)
                                                .method("POST")
                                                .url("https://api.openai.com/v1/chat/completions")
                                                .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                                                .requestBody(
                                                        "{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                                                .build()))
                .build();

        MLCreateConnectorInput updateContent = MLCreateConnectorInput
                .builder()
                .updateConnector(true)
                .version("1")
                .description("updated description")
                .build();

        MLRateLimiter rateLimiter = MLRateLimiter.builder()
                .limit("1")
                .unit(TimeUnit.MILLISECONDS)
                .build();

        updateModelInput = MLUpdateModelInput.builder()
                .modelId("test-model_id")
                .modelGroupId("modelGroupId")
                .version("2")
                .name("name")
                .description("description")
                .isEnabled(false)
                .rateLimiter(rateLimiter)
                .modelConfig(config)
                .updatedConnector(updatedConnector)
                .connectorId("test-connector_id")
                .connector(updateContent)
                .lastUpdateTime(Instant.ofEpochMilli(1))
                .build();
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(updateModelInput, parsedInput -> {
            assertEquals("test-model_id", parsedInput.getModelId());
            assertEquals(updateModelInput.getName(), parsedInput.getName());
        });
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        updateModelInput.setModelConfig(null);
        readInputStream(updateModelInput, parsedInput -> {
            assertNull(parsedInput.getModelConfig());
        });
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(updateModelInput);
        assertEquals(expectedOutputStrForUpdateRequestDoc, jsonStr);
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        String expectedIncompleteInputStr = "{\"model_id\":\"test-model_id\"}";
        updateModelInput = MLUpdateModelInput.builder()
                .modelId("test-model_id").build();
        String jsonStr = serializationWithToXContent(updateModelInput);
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parseSuccess() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals("name", parsedInput.getName());
        });
    }

    @Test
    public void parseWithNullFieldWithoutModel() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        String expectedInputStrWithNullField = "{\"model_id\":\"test-model_id\",\"name\":null,\"description\":\"description\",\"model_version\":"
                +
                "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":" +
                "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":" +
                "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
                +
                "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":\"test-connector_id\"}";
        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalFieldWithoutModel() throws Exception {
        String expectedInputStrWithIllegalField = "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\",\"model_version\":"
                +
                "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":" +
                "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":" +
                "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
                +
                "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"updated_connector\":" +
                "{\"name\":\"test\",\"version\":\"1\",\"protocol\":\"http\",\"parameters\":{\"param1\":\"value1\"},\"credential\":"
                +
                "{\"api_key\":\"credential_value\"},\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":"
                +
                "\"https://api.openai.com/v1/chat/completions\",\"headers\":{\"Authorization\":\"Bearer ${credential.api_key}\"},\"request_body\":"
                +
                "\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages} }\"}]},\"connector_id\":"
                +
                "\"test-connector_id\",\"connector\":{\"description\":\"updated description\",\"version\":\"1\"},\"last_updated_time\":1,\"illegal_field\":\"This field need to be skipped.\"}";
        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLUpdateModelInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLUpdateModelInput parsedInput = MLUpdateModelInput.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLUpdateModelInput input, Consumer<MLUpdateModelInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLUpdateModelInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }

}
