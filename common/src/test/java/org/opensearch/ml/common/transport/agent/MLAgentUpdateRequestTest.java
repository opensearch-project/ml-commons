/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.agent.MLToolSpec;

public class MLAgentUpdateRequestTest {

    MLAgentUpdateInput mlAgentUpdateInput;

    @Before
    public void setUp() {
        mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId("test_agent_id")
            .name("test_agent")
            .appType("test_app")
            .tools(Collections.singletonList(MLToolSpec.builder().type("ListIndexTool").build()))
            .build();
    }

    @Test
    public void constructor_Input() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(mlAgentUpdateInput);
        assertEquals(mlAgentUpdateInput, mlAgentUpdateRequest.getMlAgentUpdateInput());

        ActionRequestValidationException validationException = mlAgentUpdateRequest.validate();
        assertNull(validationException);
    }

    @Test
    public void constructor_NullInput() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest((MLAgentUpdateInput) null);
        assertNull(mlAgentUpdateRequest.getMlAgentUpdateInput());

        ActionRequestValidationException validationException = mlAgentUpdateRequest.validate();
        assertNotNull(validationException);
        assertTrue(validationException.toString().contains("ML Agent Update Input cannot be null"));
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(mlAgentUpdateInput);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlAgentUpdateRequest.writeTo(bytesStreamOutput);
        MLAgentUpdateRequest parsedRequest = new MLAgentUpdateRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlAgentUpdateInput.getAgentId(), parsedRequest.getMlAgentUpdateInput().getAgentId());
        assertEquals(mlAgentUpdateInput.getName(), parsedRequest.getMlAgentUpdateInput().getName());
    }

    @Test
    public void fromActionRequest_Success() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(mlAgentUpdateInput);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlAgentUpdateRequest.writeTo(out);
            }
        };
        MLAgentUpdateRequest parsedRequest = MLAgentUpdateRequest.fromActionRequest(actionRequest);
        assertNotSame(mlAgentUpdateRequest, parsedRequest);
        assertEquals(mlAgentUpdateRequest.getMlAgentUpdateInput().getAgentId(), parsedRequest.getMlAgentUpdateInput().getAgentId());
        assertEquals(mlAgentUpdateRequest.getMlAgentUpdateInput().getName(), parsedRequest.getMlAgentUpdateInput().getName());
    }

    @Test
    public void fromActionRequest_Success_MLAgentUpdateRequest() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(mlAgentUpdateInput);
        MLAgentUpdateRequest parsedRequest = MLAgentUpdateRequest.fromActionRequest(mlAgentUpdateRequest);
        assertSame(mlAgentUpdateRequest, parsedRequest);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
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
        MLAgentUpdateRequest.fromActionRequest(actionRequest);
    }
}
