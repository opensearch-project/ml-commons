/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

public class MLPromptDeleteRequestTest {
    private String promptId;
    private MLPromptDeleteRequest mlPromptDeleteRequest;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        promptId = "test_promptId";
        mlPromptDeleteRequest = new MLPromptDeleteRequest(promptId, null);
    }

    @Test
    public void constructor() {
        MLPromptDeleteRequest mlPromptDeleteRequest = new MLPromptDeleteRequest(promptId, null);
        assertNotNull(mlPromptDeleteRequest);
        assertEquals(promptId, mlPromptDeleteRequest.getPromptId());
        assertNull(mlPromptDeleteRequest.getTenantId());
    }

    @Test
    public void validateSuccess() throws IOException {
        assertNull(mlPromptDeleteRequest.validate());
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput streamOutput = new BytesStreamOutput();
        mlPromptDeleteRequest.writeTo(streamOutput);
        MLPromptDeleteRequest parsedRequest = new MLPromptDeleteRequest(streamOutput.bytes().streamInput());
        assertEquals(mlPromptDeleteRequest.getPromptId(), parsedRequest.getPromptId());
    }

    @Test
    public void validateWithNullPromptIdException() {
        MLPromptDeleteRequest mlPromptDeleteRequest = MLPromptDeleteRequest.builder().build();
        ActionRequestValidationException exception = mlPromptDeleteRequest.validate();
        Assert.assertEquals("Validation Failed: 1: ML prompt id can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithPromptDeleteRequestSuccess() {
        MLPromptDeleteRequest parsedRequest = MLPromptDeleteRequest.fromActionRequest(mlPromptDeleteRequest);
        assertSame(mlPromptDeleteRequest, parsedRequest);
    }

    @Test
    public void fromActionRequestWithNonMLPromptDeleteRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput output) throws IOException {
                mlPromptDeleteRequest.writeTo(output);
            }
        };
        MLPromptDeleteRequest parsedRequest = MLPromptDeleteRequest.fromActionRequest(actionRequest);
        assertNotSame(mlPromptDeleteRequest, parsedRequest);
        assertEquals(mlPromptDeleteRequest.getPromptId(), parsedRequest.getPromptId());
    }

    @Test
    public void fromActionRequestException() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLPromptDeleteRequest");
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
        MLPromptDeleteRequest.fromActionRequest(actionRequest);
    }
}
