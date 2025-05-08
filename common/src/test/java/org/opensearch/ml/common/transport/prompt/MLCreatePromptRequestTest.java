/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLCreatePromptRequestTest {
    private MLCreatePromptInput mlCreatePromptInput;
    private MLCreatePromptRequest mlCreatePromptRequest;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, String> testPrompt = new HashMap<>();
        testPrompt.put("system", "test system prompt");
        testPrompt.put("user", "test user prompt");
        mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name("test_prompt")
            .description("this is a test prompt")
            .version("1")
            .prompt(testPrompt)
            .tags(List.of("test_tag"))
            .build();
        mlCreatePromptRequest = MLCreatePromptRequest.builder().mlCreatePromptInput(mlCreatePromptInput).build();
    }

    @Test
    public void validateSuccess() throws IOException {
        Assert.assertNull(mlCreatePromptRequest.validate());
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput streamOutput = new BytesStreamOutput();
        mlCreatePromptRequest.writeTo(streamOutput);
        MLCreatePromptRequest parsedRequest = new MLCreatePromptRequest(streamOutput.bytes().streamInput());
        Assert.assertEquals(mlCreatePromptRequest.getMlCreatePromptInput().getName(), parsedRequest.getMlCreatePromptInput().getName());
        Assert
            .assertEquals(
                mlCreatePromptRequest.getMlCreatePromptInput().getDescription(),
                parsedRequest.getMlCreatePromptInput().getDescription()
            );
        Assert
            .assertEquals(mlCreatePromptRequest.getMlCreatePromptInput().getVersion(), parsedRequest.getMlCreatePromptInput().getVersion());
        Assert.assertEquals(mlCreatePromptRequest.getMlCreatePromptInput().getPrompt(), parsedRequest.getMlCreatePromptInput().getPrompt());
        Assert.assertEquals(mlCreatePromptRequest.getMlCreatePromptInput().getTags(), parsedRequest.getMlCreatePromptInput().getTags());
    }

    @Test
    public void validateWithNullMLCreatePromptInputException() {
        MLCreatePromptRequest mlCreatePromptRequest = MLCreatePromptRequest.builder().build();
        ActionRequestValidationException exception = mlCreatePromptRequest.validate();
        Assert.assertEquals("Validation Failed: 1: ML Prompt Input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithCreatePromptRequestSuccess() {
        MLCreatePromptRequest parsedRequest = MLCreatePromptRequest.fromActionRequest(mlCreatePromptRequest);
        Assert.assertSame(parsedRequest, mlCreatePromptRequest);
    }

    @Test
    public void fromActionRequestWithNonMLCreatePromptRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlCreatePromptRequest.writeTo(out);
            }
        };
        MLCreatePromptRequest parsedRequest = MLCreatePromptRequest.fromActionRequest(actionRequest);
        assertNotSame(parsedRequest, mlCreatePromptRequest);
        assertEquals(mlCreatePromptRequest.getMlCreatePromptInput(), parsedRequest.getMlCreatePromptInput());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLCreatePromptRequest");
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLCreatePromptRequest.fromActionRequest(actionRequest);
    }
}
