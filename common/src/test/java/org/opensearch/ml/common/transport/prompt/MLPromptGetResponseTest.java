/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.TestHelper;

public class MLPromptGetResponseTest {
    MLPrompt mlPrompt;

    private final String expectedInputStr = "{\"prompt_id\":\"dummy promptId\",\"name\":\"some prompt\",\"description\":\"test\","
        + "\"version\":\"1\",\"prompt\":{\"system\":\"some system prompt\",\"user\":\"some user prompt\"}"
        + ",\"tags\":[\"test\"],\"tenant_id\":\"tenantId\",\"create_time\":1641600000000,\"last_update_time\":1641600000000}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
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
    public void constructor() {
        MLPromptGetResponse mlPromptGetResponse = MLPromptGetResponse.builder().mlPrompt(mlPrompt).build();
        assertEquals(mlPrompt, mlPromptGetResponse.getMlPrompt());
    }

    @Test
    public void testWriteTo() throws IOException {
        MLPromptGetResponse mlPromptGetResponse = MLPromptGetResponse.builder().mlPrompt(mlPrompt).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlPromptGetResponse.writeTo(bytesStreamOutput);
        MLPromptGetResponse parsed = new MLPromptGetResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlPrompt, parsed.getMlPrompt());
    }

    @Test
    public void testToXContent() throws IOException {
        MLPromptGetResponse mlPromptGetResponse = new MLPromptGetResponse(mlPrompt);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlPromptGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlPromptGetResponseContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals(expectedInputStr, mlPromptGetResponseContent);
    }

    @Test
    public void fromActionResponseWithPromptGetResponseSuccess() {
        MLPromptGetResponse mlPromptGetResponse = new MLPromptGetResponse(mlPrompt);
        MLPromptGetResponse parsed = MLPromptGetResponse.fromActionResponse(mlPromptGetResponse);
        assertSame(mlPromptGetResponse, parsed);
    }

    @Test
    public void fromActionResponseWithNonMLPromptGetResponseSuccess() {
        MLPromptGetResponse mlPromptGetResponse = new MLPromptGetResponse(mlPrompt);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlPromptGetResponse.writeTo(out);
            }
        };
        MLPromptGetResponse parsedResponse = MLPromptGetResponse.fromActionResponse(actionResponse);
        Assert.assertNotSame(parsedResponse, mlPromptGetResponse);
        Assert.assertEquals(mlPromptGetResponse.getMlPrompt(), parsedResponse.getMlPrompt());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionResponse into MLPromptGetResponse");
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLPromptGetResponse.fromActionResponse(actionResponse);
    }
}
