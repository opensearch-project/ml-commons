/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.prompt;

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
import org.opensearch.ml.common.MLPrompt;
import org.opensearch.ml.common.TestHelper;

public class MLPromptTest {
    private MLPrompt mlPrompt;

    private final String expectedInputStr = "{\"prompt_id\":\"dummy promptId\",\"name\":\"some prompt\",\"description\":\"test\","
        + "\"version\":\"1\",\"prompt\":{\"system\":\"some system prompt\",\"user\":\"some user prompt\"}"
        + ",\"tags\":[\"test\"],\"tenant_id\":\"tenantId\",\"create_time\":1641600000000,\"last_update_time\":1641600000000}";

    @Before
    public void setup() {
        Instant time = Instant.parse("2022-01-08T00:00:00Z");
        Map<String, String> testPrompt = new HashMap<>();
        testPrompt.put("system", "some system prompt");
        testPrompt.put("user", "some user prompt");
        List<String> testTags = List.of("test");
        mlPrompt = MLPrompt
            .builder()
            .promptId("dummy promptId")
            .name("some prompt")
            .description("test")
            .version("1")
            .prompt(testPrompt)
            .tags(testTags)
            .tenantId("tenantId")
            .createTime(time)
            .lastUpdateTime(time)
            .build();
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlPrompt.writeTo(output);
        MLPrompt mlPrompt2 = new MLPrompt(output.bytes().streamInput());
        Assert.assertEquals(mlPrompt, mlPrompt2);
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlPrompt.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlPromptContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals(expectedInputStr, mlPromptContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        MLPrompt prompt = MLPrompt.builder().build();
        prompt.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlPromptContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{}", mlPromptContent);
    }

    @Test
    public void testParse() throws IOException {
        String jsonStr = expectedInputStr;

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        MLPrompt parsed = MLPrompt.parse(parser);
        assertEquals("dummy promptId", parsed.getPromptId());
        assertEquals("some prompt", parsed.getName());
        assertEquals("test", parsed.getDescription());
        assertEquals("1", parsed.getVersion());
        assertEquals(mlPrompt.getPrompt(), parsed.getPrompt());
        assertEquals(mlPrompt.getTags(), parsed.getTags());
        assertEquals(mlPrompt.getTenantId(), parsed.getTenantId());
        assertEquals(mlPrompt.getCreateTime(), parsed.getCreateTime());
        assertEquals(mlPrompt.getLastUpdateTime(), parsed.getLastUpdateTime());
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(mlPrompt);
    }

    public void readInputStream(MLPrompt mlPrompt) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlPrompt.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLPrompt parsed = MLPrompt.fromStream(streamInput);
        assertEquals(mlPrompt.getPromptId(), parsed.getPromptId());
        assertEquals(mlPrompt.getName(), parsed.getName());
        assertEquals(mlPrompt.getDescription(), parsed.getDescription());
        assertEquals(mlPrompt.getVersion(), parsed.getVersion());
        assertEquals(mlPrompt.getPrompt(), parsed.getPrompt());
        assertEquals(mlPrompt.getTags(), parsed.getTags());
        assertEquals(mlPrompt.getTenantId(), parsed.getTenantId());
        assertEquals(mlPrompt.getCreateTime(), parsed.getCreateTime());
        assertEquals(mlPrompt.getLastUpdateTime(), parsed.getLastUpdateTime());
    }
}
