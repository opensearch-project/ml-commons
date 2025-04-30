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
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;

public class MLAgentUpdateRequestTests {

    String agentId;
    MLAgent mlAgent;

    @Before
    public void setUp() {
        agentId = "test_agent_id";
        mlAgent = MLAgent
            .builder()
            .name("test_agent")
            .appType("test_app")
            .type("flow")
            .tools(Collections.singletonList(MLToolSpec.builder().type("ListIndexTool").build()))
            .build();
    }

    @Test
    public void constructor_Agent() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(agentId, mlAgent);
        assertEquals(agentId, mlAgentUpdateRequest.getAgentId());
        assertEquals(mlAgent, mlAgentUpdateRequest.getMlAgent());

        ActionRequestValidationException validationException = mlAgentUpdateRequest.validate();
        assertNull(validationException);
    }

    @Test
    public void constructor_NullId() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(null, mlAgent);
        assertNull(mlAgentUpdateRequest.getAgentId());

        ActionRequestValidationException validationException = mlAgentUpdateRequest.validate();
        assertNotNull(validationException);
        assertTrue(validationException.toString().contains("Agent ID and ML Agent cannot be null"));
    }

    @Test
    public void constructor_NullAgent() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(agentId, null);
        assertNull(mlAgentUpdateRequest.getMlAgent());

        ActionRequestValidationException validationException = mlAgentUpdateRequest.validate();
        assertNotNull(validationException);
        assertTrue(validationException.toString().contains("Agent ID and ML Agent cannot be null"));
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(agentId, mlAgent);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlAgentUpdateRequest.writeTo(bytesStreamOutput);
        MLAgentUpdateRequest parsedRequest = new MLAgentUpdateRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(agentId, parsedRequest.getAgentId());
        assertEquals(mlAgent, parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(agentId, mlAgent);
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
        assertEquals(mlAgentUpdateRequest.getAgentId(), parsedRequest.getAgentId());
        assertEquals(mlAgentUpdateRequest.getMlAgent(), parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success_MLAgentUpdateRequest() {
        MLAgentUpdateRequest mlAgentUpdateRequest = new MLAgentUpdateRequest(agentId, mlAgent);
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
