/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLExecuteAgentRequestTests {

    private String agentId;
    private String method;
    private Map<String, String> parameters;

    @Before
    public void setUp() {
        agentId = "test_agent_id";
        method = "POST";
        parameters = Collections.singletonMap("input", "foo");
    }

    @Test
    public void testConstructorAndSerialization() throws IOException {
        MLExecuteAgentRequest request = new MLExecuteAgentRequest(agentId, method, parameters);
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLExecuteAgentRequest newRequest = new MLExecuteAgentRequest(in);
        assertEquals(request.getAgentId(), newRequest.getAgentId());
        assertEquals(request.getMethod(), newRequest.getMethod());
        assertEquals(request.getParameters(), newRequest.getParameters());
    }

    @Test
    public void testValidate() {
        MLExecuteAgentRequest request = new MLExecuteAgentRequest(agentId, method, parameters);
        assertNull(request.validate());
    }

    @Test
    public void testFromActionRequest_SelfConversion() {
        MLExecuteAgentRequest request = new MLExecuteAgentRequest(agentId, method, parameters);
        MLExecuteAgentRequest selfRequest = MLExecuteAgentRequest.fromActionRequest(request);
        assertEquals(request, selfRequest);
    }

    @Test
    public void testFromActionRequestWithIOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test exception");
            }
        };

        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> MLExecuteAgentRequest.fromActionRequest(actionRequest));
        assertEquals("failed to parse ActionRequest into MLExecuteAgentRequest", e.getMessage());
        assertEquals("test exception", e.getCause().getMessage());
    }
}
