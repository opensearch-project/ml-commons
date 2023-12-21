/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.controller;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
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
import org.opensearch.search.SearchModule;

public class MLModelControllerTest {

    private MLModelController modelController;

    private MLModelController modelControllerNull;

    private final String expectedInputStr = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":" +
            "{\"testUser\":{\"rate_limit_number\":\"1\",\"rate_limit_unit\":\"MILLISECONDS\"}}}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MLRateLimiter rateLimiter = MLRateLimiter.builder()
                .rateLimitNumber("1")
                .rateLimitUnit(TimeUnit.MILLISECONDS)
                .build();

        modelControllerNull = MLModelController.builder()
                .modelId("testModelId").build();

        modelController = MLModelController.builder()
                .modelId("testModelId")
                .userRateLimiterConfig(new HashMap<>() {{
                    put("testUser", rateLimiter);
                }})
                .build();

    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(modelController, parsedInput -> {
            assertEquals("testModelId", parsedInput.getModelId());
            assertEquals(modelController.getUserRateLimiterConfig().get("testUser").getRateLimitNumber(),
                    parsedInput.getUserRateLimiterConfig().get("testUser").getRateLimitNumber());
        });
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        modelController.setUserRateLimiterConfig(null);
        readInputStream(modelController, parsedInput -> {
            assertNull(parsedInput.getUserRateLimiterConfig());
        });
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(modelController);
        assertEquals(expectedInputStr, jsonStr);
    }


    @Test
    public void testToXContentIncomplete() throws Exception {
        final String expectedIncompleteInputStr =
                "{\"model_id\":\"testModelId\"}";
        String jsonStr = serializationWithToXContent(modelControllerNull);
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void testToXContentWithNullMLRateLimiterInUserRateLimiterConfig() throws Exception {
        // Notice that MLModelController will throw an exception if it parses this output string, check parseWithNullMLRateLimiterInUserRateLimiterConfigFieldWithException test below.
        final String expectedOutputStrWithNullField =
                "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{\"testUser\":null}}";
        MLModelController modelControllerWithNewUserAndEmptyRateLimiter = MLModelController.builder()
                .modelId("testModelId")
                .userRateLimiterConfig(new HashMap<>(){{put("testUser", null);}})
                .build();
        String jsonStr = serializationWithToXContent(modelControllerWithNewUserAndEmptyRateLimiter);
        assertEquals(expectedOutputStrWithNullField, jsonStr);
    }

    @Test
    public void parseSuccess() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> assertEquals("testModelId", parsedInput.getModelId()));
    }

    @Test
    // Notice that this won't throw an IllegalStateException, which is pretty different from usual
    public void parseWithoutUserRateLimiterConfigFieldWithNoException() throws Exception {
        final String expectedIncompleteInputStr = "{\"model_id\":\"testModelId\"}";
        final String expectedOutputStr = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{}}";

        testParseFromJsonString(expectedIncompleteInputStr, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    // Notice that this won't throw an IllegalStateException, which is pretty different from usual
    public void parseWithNullUserRateLimiterConfigFieldWithNoException() throws Exception {
        final String expectedInputStrWithNullField = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":null}";
        final String expectedOutputStr = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{}}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    // Notice that this won't throw an IllegalStateException, which is pretty different from usual
    public void parseWithNewUserAndEmptyRateLimiterFieldWithNoException() throws Exception {
        final String expectedInputStrWithEmptyField = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":" +
                "{\"testUser\":{}}}";

        testParseFromJsonString(expectedInputStrWithEmptyField, parsedInput -> {
            try {
                assertEquals(expectedInputStrWithEmptyField, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithNullField() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        final String expectedInputStrWithNullField = "{\"model_id\":null,\"user_rate_limiter_config\":" +
                "{\"testUser\":{\"rate_limit_number\":\"1\",\"rate_limit_unit\":\"MILLISECONDS\"}}}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalField() throws Exception {
        final String expectedInputStrWithIllegalField = "{\"model_id\":\"testModelId\",\"illegal_field\":\"This field need to be skipped.\",\"user_rate_limiter_config\":" +
                "{\"testUser\":{\"rate_limit_number\":\"1\",\"rate_limit_unit\":\"MILLISECONDS\"}}}";

        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    // This will throw a ParsingException because MLRateLimiter parser cannot parse null field.
    public void parseWithNullMLRateLimiterInUserRateLimiterConfigFieldWithException() throws Exception {
        exceptionRule.expect(RuntimeException.class);
        final String expectedInputStrWithNullField = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{\"testUser\":null}}";
        final String expectedOutputStr = "{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{\"testUser\":null}}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalRateLimiterFieldWithException() throws Exception {
        exceptionRule.expect(RuntimeException.class);
        final String expectedInputStrWithIllegalField = "{\"model_id\":\"testModelId\",\"illegal_field\":\"This field need to be skipped.\",\"user_rate_limiter_config\":" +
                "{\"testUser\":\"Some illegal content that MLRateLimiter parser cannot parse.\"}}";

        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testRateLimiterUpdate() {
        MLModelController modelControllerWithEmptyUserRateLimiterConfig = MLModelController.builder()
                .modelId("testModelId")
                .userRateLimiterConfig(new HashMap<>())
                .build();

        MLModelController modelControllerWithNewUserAndEmptyRateLimiter = MLModelController.builder()
                .modelId("testModelId")
                .userRateLimiterConfig(new HashMap<>(){{put("testUser", MLRateLimiter.builder().build());}})
                .build();

        modelControllerWithEmptyUserRateLimiterConfig.update(modelControllerNull);
        assertTrue(modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().isEmpty());
        modelControllerWithEmptyUserRateLimiterConfig.update(modelControllerWithEmptyUserRateLimiterConfig);
        assertTrue(modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().isEmpty());
        modelControllerWithEmptyUserRateLimiterConfig.update(modelControllerWithNewUserAndEmptyRateLimiter);
        assertNull(modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().get("testUser").getRateLimitNumber());
        assertNull(modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().get("testUser").getRateLimitUnit());
        modelControllerWithEmptyUserRateLimiterConfig.update(modelController);
        assertEquals("1", modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().get("testUser").getRateLimitNumber());
        assertEquals(TimeUnit.MILLISECONDS, modelControllerWithEmptyUserRateLimiterConfig.getUserRateLimiterConfig().get("testUser").getRateLimitUnit());

    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLModelController> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLModelController parsedInput = MLModelController.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLModelController input, Consumer<MLModelController> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLModelController parsedInput = new MLModelController(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLModelController input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }
}
