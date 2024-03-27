/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MLDeployingSettingTests {

    private MLDeploySetting deploySetting;

    private MLDeploySetting deploySettingNull;

    private final String expectedInputStr = "{\"is_auto_deploy_enabled\":true}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        deploySetting = MLDeploySetting.builder()
                .isAutoDeployEnabled(true)
                .build();

        deploySettingNull = MLDeploySetting.builder().build();

    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(deploySetting, parsedInput -> {
            assertTrue(parsedInput.getIsAutoDeployEnabled());
        });
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(deploySetting);
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        final String expectedIncompleteInputStr = "{}";

        String jsonStr = serializationWithToXContent(deploySettingNull);
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parseSuccess() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertTrue(parsedInput.getIsAutoDeployEnabled());
        });
    }

    @Test
    public void parseWithIllegalArgumentNull() throws Exception {
        exceptionRule.expect(JsonParseException.class);
        final String expectedInputStrWithNullField = "{\"is_auto_deploy_enabled\":null}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals("{}", serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalArgumentInteger() throws Exception {
        exceptionRule.expect(JsonParseException.class);
        final String expectedInputStrWithNullField = "{\"is_auto_deploy_enabled\":364}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals("{}", serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalField() throws Exception {
        final String expectedInputStrWithIllegalField = "{\"is_auto_deploy_enabled\":true," +
                "\"illegal_field\":\"This field need to be skipped.\"}";

        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLDeploySetting> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE,
                        expectedInputStr);
        parser.nextToken();
        MLDeploySetting parsedInput = MLDeploySetting.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLDeploySetting input, Consumer<MLDeploySetting> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLDeploySetting parsedInput = new MLDeploySetting(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLDeploySetting input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }
}
