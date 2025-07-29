/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

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
}
