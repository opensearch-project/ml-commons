/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLPromptGetRequestTest {
    private String promptId;
    private MLPromptGetRequest mlPromptGetRequest;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        promptId = "test_promptId";
        mlPromptGetRequest = new MLPromptGetRequest(promptId, null);
    }

    @Test
    public void constructor() {
        assertEquals(mlPromptGetRequest.getPromptId(), promptId);
        assertNull(mlPromptGetRequest.getTenantId());
    }

    @Test
    public void validateSuccess() throws IOException {
        assertNull(mlPromptGetRequest.validate());
    }

    @Test
    public void WriteToSuccess() throws IOException {
        BytesStreamOutput streamOutput = new BytesStreamOutput();
        mlPromptGetRequest.writeTo(streamOutput);
        MLPromptGetRequest parsedRequest = new MLPromptGetRequest(streamOutput.bytes().streamInput());
        assertEquals(parsedRequest.getPromptId(), mlPromptGetRequest.getPromptId());
    }

    @Test
    public void validateWithNullPromptIdException() {
        MLPromptGetRequest mlPromptGetRequest = MLPromptGetRequest.builder().build();
        ActionRequestValidationException exception = mlPromptGetRequest.validate();
        Assert.assertEquals("Validation Failed: 1: ML prompt id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithPromptGetRequestSuccess() {
        MLPromptGetRequest parsedRequest = MLPromptGetRequest.fromActionRequest(mlPromptGetRequest);
        assertSame(parsedRequest, mlPromptGetRequest);
    }

    @Test
    public void fromActionRequestWithNonMLPromptGetRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput output) throws IOException {
                mlPromptGetRequest.writeTo(output);
            }
        };
        MLPromptGetRequest parsedRequest = MLPromptGetRequest.fromActionRequest(actionRequest);
        assertNotSame(parsedRequest, mlPromptGetRequest);
        assertEquals(mlPromptGetRequest.getPromptId(), parsedRequest.getPromptId());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLPromptGetRequest");
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
        MLPromptGetRequest.fromActionRequest(actionRequest);
    }
}
