/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.*;

public class MLRegisterAgentResponseTest {
    String agentId;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        agentId = "test_agent_id";
    }

    @Test
    public void constructor_AgentId() {
        MLRegisterAgentResponse response = new MLRegisterAgentResponse(agentId);
        assertEquals(agentId, response.getAgentId());
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLRegisterAgentResponse registerAgentResponse = new MLRegisterAgentResponse(agentId);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        registerAgentResponse.writeTo(bytesStreamOutput);
        MLRegisterAgentResponse parsedResponse = new MLRegisterAgentResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(agentId, parsedResponse.getAgentId());
    }

    @Test
    public void toXContent() throws IOException {
        MLRegisterAgentResponse registerAgentResponse = new MLRegisterAgentResponse(agentId);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        registerAgentResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"agent_id\":\"test_agent_id\"}", jsonStr);
    }

    @Test
    public void fromActionResponse_Success() {
        MLRegisterAgentResponse registerAgentResponse = new MLRegisterAgentResponse(agentId);
        ActionResponse actionResponse = new ActionResponse() {

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                registerAgentResponse.writeTo(out);
            }
        };
        MLRegisterAgentResponse parsedResponse = MLRegisterAgentResponse.fromActionResponse(actionResponse);
        assertNotSame(registerAgentResponse, parsedResponse);
        assertEquals(registerAgentResponse.getAgentId(), parsedResponse.getAgentId());
    }

    @Test
    public void fromActionResponse_Success_MLRegisterAgentResponse() {
        MLRegisterAgentResponse registerAgentResponse = new MLRegisterAgentResponse(agentId);
        MLRegisterAgentResponse parsedResponse = MLRegisterAgentResponse.fromActionResponse(registerAgentResponse);
        assertSame(registerAgentResponse, parsedResponse);
    }

    @Test
    public void fromActionResponse_Exception() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionResponse into MLRegisterAgentResponse");
        MLRegisterAgentResponse registerAgentResponse = new MLRegisterAgentResponse(agentId);
        ActionResponse actionResponse = new ActionResponse() {

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLRegisterAgentResponse.fromActionResponse(actionResponse);
    }

}
