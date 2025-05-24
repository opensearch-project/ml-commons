/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MLCreatePromptInputTest {
    private MLCreatePromptInput mlCreatePromptInput;
    private Map<String, String> testPrompt;

    private static final String TEST_PROMPT_NAME = "test_prompt";
    private static final String TEST_PROMPT_DESCRIPTION = "for test";
    private static final String TEST_PROMPT_VERSION = "1";
    private static final List<String> TEST_PROMPT_TAGS = List.of("test_tag");
    private static final String TEST_PROMPT_TENANTID = "tenant_id";

    private final String expectedInputStr = "{\"name\":\"test_prompt\",\"description\":\"for test\",\"version\":\"1\",\"prompt\":"
        + "{\"system\":\"test system prompt\",\"user\":\"test user prompt\"},\"tags\":[\"test_tag\"],"
        + "\"tenant_id\":\"tenant_id\"}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        testPrompt = new HashMap<>();
        testPrompt.put("system", "test system prompt");
        testPrompt.put("user", "test user prompt");

        mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(testPrompt)
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void constructMLCreatePromptInput_NullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt name field is null");
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(null)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(testPrompt)
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void constructMLCreatePromptInput_NullPrompt() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt prompt field cannot be empty or null");
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(null)
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void constructMLCreatePromptInput_EmptyPromptField() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt prompt field cannot be empty or null");
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(new HashMap<>())
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void constructMLCreatePromptInput_NonSystemPromptParameter() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt prompt field requires system parameter");
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(Map.of("user", "test user prompt"))
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void constructMLCreatePromptInput_NonUserPromptParameter() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt prompt field requires user parameter");
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name(TEST_PROMPT_NAME)
            .description(TEST_PROMPT_DESCRIPTION)
            .version(TEST_PROMPT_VERSION)
            .prompt(Map.of("system", "test system prompt"))
            .tags(TEST_PROMPT_TAGS)
            .tenantId(TEST_PROMPT_TENANTID)
            .build();
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlCreatePromptInput.writeTo(bytesStreamOutput);
        MLCreatePromptInput mlCreatePromptInput2 = new MLCreatePromptInput(bytesStreamOutput.bytes().streamInput());
        Assert.assertEquals(mlCreatePromptInput, mlCreatePromptInput2);
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlCreatePromptInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlCreatePromptInputContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals(expectedInputStr, mlCreatePromptInputContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt name field is null");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput.builder().build();
        mlCreatePromptInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlCreatePromptInputContent = TestHelper.xContentBuilderToString(builder);
    }

    @Test
    public void readInputstreamSuccess() throws IOException {
        readInputStream();
    }

    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlCreatePromptInput.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreatePromptInput parsed = new MLCreatePromptInput(streamInput);
        assertEquals(mlCreatePromptInput.getName(), parsed.getName());
        assertEquals(mlCreatePromptInput.getDescription(), parsed.getDescription());
        assertEquals(mlCreatePromptInput.getVersion(), parsed.getVersion());
        assertEquals(mlCreatePromptInput.getPrompt(), parsed.getPrompt());
        assertEquals(mlCreatePromptInput.getTags(), parsed.getTags());
        assertEquals(mlCreatePromptInput.getTenantId(), parsed.getTenantId());
    }

    @Test
    public void testParse() throws IOException {
        String jsonStr = expectedInputStr;

        XContentParser parser = createParser(jsonStr);

        MLCreatePromptInput parsed = MLCreatePromptInput.parse(parser);
        assertEquals(TEST_PROMPT_NAME, parsed.getName());
        assertEquals(TEST_PROMPT_DESCRIPTION, parsed.getDescription());
        assertEquals(TEST_PROMPT_VERSION, parsed.getVersion());
        assertEquals(testPrompt, parsed.getPrompt());
        assertEquals(TEST_PROMPT_TAGS, parsed.getTags());
        assertEquals(TEST_PROMPT_TENANTID, parsed.getTenantId());
    }

    @Test
    public void testParse_MissingNameField_ShouldThrowException() throws IOException {
        String jsonMissingName = "{\"description\":\"for test\",\"version\":\"1\",\"prompt\":"
            + "{\"system\":\"test system prompt\",\"user\":\"test user prompt\"},\"tags\":[\"test_tag\"],"
            + "\"tenant_id\":\"tenant_id\"}";

        XContentParser parser = createParser(jsonMissingName);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt name field is null");
        MLCreatePromptInput.parse(parser);
    }

    @Test
    public void testParse_MissingPromptField_ShouldThrowException() throws IOException {
        String jsonMissingPrompt = "{\"name\":\"test_prompt\",\"description\":\"for test\",\"version\":\"1\","
            + "\"tags\":[\"test_tag\"],\"tenant_id\":\"tenant_id\"}";

        XContentParser parser = createParser(jsonMissingPrompt);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("MLPrompt prompt field cannot be empty or null");
        MLCreatePromptInput.parse(parser);
    }

    public XContentParser createParser(String jsonStr) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        return parser;
    }
}
