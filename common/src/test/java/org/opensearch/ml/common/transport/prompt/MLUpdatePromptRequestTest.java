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

public class MLUpdatePromptRequestTest {
    private MLUpdatePromptInput mlUpdatePromptInput;
    private MLUpdatePromptRequest mlUpdatePromptRequest;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        Map<String, String> testPrompt = new HashMap<>();
        testPrompt.put("system", "test system prompt");
        testPrompt.put("user", "test user prompt");
        mlUpdatePromptInput = MLUpdatePromptInput
            .builder()
            .name("test_prompt")
            .description("this is a test prompt")
            .prompt(testPrompt)
            .tags(List.of("test_tag"))
            .build();
        mlUpdatePromptRequest = MLUpdatePromptRequest.builder().promptId("test_prompt_id").mlUpdatePromptInput(mlUpdatePromptInput).build();
    }

    @Test
    public void validateSuccess() throws IOException {
        Assert.assertNull(mlUpdatePromptRequest.validate());
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput streamOutput = new BytesStreamOutput();
        mlUpdatePromptRequest.writeTo(streamOutput);
        MLUpdatePromptRequest parsedRequest = new MLUpdatePromptRequest(streamOutput.bytes().streamInput());
        Assert.assertEquals(mlUpdatePromptInput.getName(), parsedRequest.getMlUpdatePromptInput().getName());
        Assert.assertEquals(mlUpdatePromptInput.getDescription(), parsedRequest.getMlUpdatePromptInput().getDescription());
        Assert.assertEquals(mlUpdatePromptInput.getPrompt(), parsedRequest.getMlUpdatePromptInput().getPrompt());
        Assert.assertEquals(mlUpdatePromptInput.getTags(), parsedRequest.getMlUpdatePromptInput().getTags());
    }

    @Test
    public void validateWithNullMLUpdatePromptInputException() {
        MLUpdatePromptRequest mlUpdatePromptRequest = MLUpdatePromptRequest.builder().promptId("prompt_id").build();
        ActionRequestValidationException exception = mlUpdatePromptRequest.validate();
        Assert.assertEquals("Validation Failed: 1: Update Prompt Input can't be null;", exception.getMessage());
    }

    @Test
    public void validateWithNullMLUpdatePromptIDException() {
        MLUpdatePromptRequest mlUpdatePromptRequest = MLUpdatePromptRequest.builder().mlUpdatePromptInput(mlUpdatePromptInput).build();
        ActionRequestValidationException exception = mlUpdatePromptRequest.validate();
        Assert.assertEquals("Validation Failed: 1: ML prompt id can't be null;", exception.getMessage());
    }

    @Test
    public void validateWithNullPromptInputAndNullPromptIdException() {
        MLUpdatePromptRequest mlUpdatePromptRequest = MLUpdatePromptRequest.builder().build();
        ActionRequestValidationException exception = mlUpdatePromptRequest.validate();
        Assert
            .assertEquals("Validation Failed: 1: ML prompt id can't be null;2: Update Prompt Input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithUpdatePromptRequestSuccess() {
        MLUpdatePromptRequest parsedRequest = MLUpdatePromptRequest.fromActionRequest(mlUpdatePromptRequest);
        Assert.assertSame(parsedRequest, mlUpdatePromptRequest);
    }

    @Test
    public void fromActionRequestWithNonMLUpdatePromptRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlUpdatePromptRequest.writeTo(out);
            }
        };
        MLUpdatePromptRequest parsedRequest = MLUpdatePromptRequest.fromActionRequest(actionRequest);
        assertNotSame(mlUpdatePromptRequest, parsedRequest);
        assertEquals(mlUpdatePromptRequest.getMlUpdatePromptInput(), parsedRequest.getMlUpdatePromptInput());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLUpdatePromptRequest");
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
        MLUpdatePromptRequest.fromActionRequest(actionRequest);
    }
}
