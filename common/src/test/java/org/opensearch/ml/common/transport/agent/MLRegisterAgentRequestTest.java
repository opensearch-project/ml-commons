/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class MLRegisterAgentRequestTest {

    MLAgent mlAgent;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        mlAgent = MLAgent.builder()
                .name("test_agent")
                .appType("test_app")
                .type("flow")
                .tools(Arrays.asList(MLToolSpec.builder().type("CatIndexTool").build()))
                .build();
    }

    @Test
    public void constructor_Agent() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        assertEquals(mlAgent, registerAgentRequest.getMlAgent());

        ActionRequestValidationException validationException = registerAgentRequest.validate();
        assertNull(validationException);
    }

    @Test
    public void constructor_NullAgent() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest((MLAgent) null);
        assertNull(registerAgentRequest.getMlAgent());

        ActionRequestValidationException validationException = registerAgentRequest.validate();
        assertNotNull(validationException);
        assertTrue(validationException.toString().contains("ML agent can't be null"));
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        registerAgentRequest.writeTo(bytesStreamOutput);
        MLRegisterAgentRequest parsedRequest = new MLRegisterAgentRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlAgent, parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                registerAgentRequest.writeTo(out);
            }
        };
        MLRegisterAgentRequest parsedRequest = MLRegisterAgentRequest.fromActionRequest(actionRequest);
        assertNotSame(registerAgentRequest, parsedRequest);
        assertEquals(registerAgentRequest.getMlAgent(), parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success_MLRegisterAgentRequest() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        MLRegisterAgentRequest parsedRequest = MLRegisterAgentRequest.fromActionRequest(registerAgentRequest);
        assertSame(registerAgentRequest, parsedRequest);
    }

    @Test
    public void fromActionRequest_Exception() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLRegisterAgentRequest");
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
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
        MLRegisterAgentRequest.fromActionRequest(actionRequest);
    }
}
