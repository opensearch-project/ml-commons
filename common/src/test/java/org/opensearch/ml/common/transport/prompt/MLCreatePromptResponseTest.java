/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;

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
import org.opensearch.ml.common.TestHelper;

public class MLCreatePromptResponseTest {
    String promptId;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        promptId = "test_prompt_id";
    }

    @Test
    public void constructor_PromptId() {
        MLCreatePromptResponse mlCreatePromptResponse = new MLCreatePromptResponse(promptId);
        Assert.assertEquals(promptId, mlCreatePromptResponse.getPromptId());
    }

    @Test
    public void TestWriteTo() throws IOException {
        MLCreatePromptResponse mlCreatePromptResponse = new MLCreatePromptResponse(promptId);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlCreatePromptResponse.writeTo(bytesStreamOutput);
        MLCreatePromptResponse parsed = new MLCreatePromptResponse(bytesStreamOutput.bytes().streamInput());
        Assert.assertEquals(promptId, parsed.getPromptId());
    }

    @Test
    public void testToXContent() throws IOException {
        MLCreatePromptResponse mlCreatePromptResponse = new MLCreatePromptResponse(promptId);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlCreatePromptResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String mlCreatePromptResponseContent = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"prompt_id\":\"test_prompt_id\"}", mlCreatePromptResponseContent);
    }

    @Test
    public void fromActionResponseWithCreatePromptResponseSuccess() {
        MLCreatePromptResponse mlCreatePromptResponse = new MLCreatePromptResponse(promptId);
        MLCreatePromptResponse parsed = MLCreatePromptResponse.fromActionResponse(mlCreatePromptResponse);
        Assert.assertSame(mlCreatePromptResponse, parsed);
    }

    @Test
    public void fromActionResponseWithNonMLCreatePromptResponseSuccess() {
        MLCreatePromptResponse mlCreatePromptResponse = new MLCreatePromptResponse(promptId);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlCreatePromptResponse.writeTo(out);
            }
        };
        MLCreatePromptResponse parsedResponse = MLCreatePromptResponse.fromActionResponse(actionResponse);
        Assert.assertNotSame(parsedResponse, mlCreatePromptResponse);
        Assert.assertEquals(mlCreatePromptResponse.getPromptId(), parsedResponse.getPromptId());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionResponse into MLCreatePromptResponse");
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLCreatePromptResponse.fromActionResponse(actionResponse);
    }
}
