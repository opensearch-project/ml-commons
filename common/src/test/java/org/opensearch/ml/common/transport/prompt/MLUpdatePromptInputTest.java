/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MLUpdatePromptInputTest {
    private MLUpdatePromptInput mlUpdatePromptInput;
    private Map<String, String> testPrompt;

    private static final String TEST_PROMPT_NAME = "test_prompt";
    private static final String TEST_PROMPT_DESCRIPTION = "for test";
    private static final String TEST_PROMPT_VERSION = "2";
    private static final List<String> TEST_PROMPT_TAGS = List.of("test_tag");
    private static final String TEST_PROMPT_TENANTID = "tenant_id";
    private static final Instant TEST_PROMPT_LAST_UPDATED_TIME = Instant.ofEpochMilli(1);

    private final String expectedInputStr = "{\"name\":\"test_prompt\",\"description\":\"for test\",\"version\":\"2\","
        + "\"prompt\":{\"system\":\"test system prompt\",\"user\":\"test user prompt\"},\"tags\":[\"test_tag\"],"
        + "\"tenant_id\":\"tenant_id\",\"last_updated_time\":1}";

    @Before
    public void setUp() {
        testPrompt = new HashMap<>();
        testPrompt.put("system", "test system prompt");
        testPrompt.put("user", "test user prompt");

        mlUpdatePromptInput = MLUpdatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(testPrompt)
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .lastUpdateTime(TEST_PROMPT_LAST_UPDATED_TIME)
            .build();
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUpdatePromptInput.writeTo(bytesStreamOutput);
        MLUpdatePromptInput mlUpdatePromptInput2 = new MLUpdatePromptInput(bytesStreamOutput.bytes().streamInput());
        Assert.assertEquals(mlUpdatePromptInput, mlUpdatePromptInput2);
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlUpdatePromptInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlUpdatePromptInputContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals(expectedInputStr, mlUpdatePromptInputContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        MLUpdatePromptInput mlUpdatePromptInput = MLUpdatePromptInput.builder().build();
        mlUpdatePromptInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlUpdatePromptInputContent = TestHelper.xContentBuilderToString(builder);
    }

    @Test
    public void readInputstreamSuccess() throws IOException {
        readInputStream();
    }

    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUpdatePromptInput.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdatePromptInput parsed = new MLUpdatePromptInput(streamInput);
        assertEquals(mlUpdatePromptInput.getName(), parsed.getName());
        assertEquals(mlUpdatePromptInput.getDescription(), parsed.getDescription());
        assertEquals(mlUpdatePromptInput.getPrompt(), parsed.getPrompt());
        assertEquals(mlUpdatePromptInput.getTags(), parsed.getTags());
        assertEquals(mlUpdatePromptInput.getTenantId(), parsed.getTenantId());
    }

    @Test
    public void testParse() throws IOException {
        String jsonStr = expectedInputStr;

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        MLUpdatePromptInput parsed = MLUpdatePromptInput.parse(parser);
        assertEquals(TEST_PROMPT_NAME, parsed.getName());
        assertEquals(TEST_PROMPT_DESCRIPTION, parsed.getDescription());
        assertEquals(testPrompt, parsed.getPrompt());
        assertEquals(TEST_PROMPT_TAGS, parsed.getTags());
        assertEquals(TEST_PROMPT_TENANTID, parsed.getTenantId());
    }
}
