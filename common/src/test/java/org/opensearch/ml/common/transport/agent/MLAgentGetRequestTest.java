/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.agent;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.opensearch.action.ValidateActions.addValidationError;

public class MLAgentGetRequestTest {
    String agentId;

    @Test
    public void constructor_AgentId() {
        agentId = "test-abc";
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);
        assertEquals(mLAgentGetRequest.getAgentId(),agentId);
    }

    @Test
    public void writeTo() throws IOException {
        agentId = "test-hij";

        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);
        BytesStreamOutput output = new BytesStreamOutput();
        mLAgentGetRequest.writeTo(output);

        MLAgentGetRequest mLAgentGetRequest1 = new MLAgentGetRequest(output.bytes().streamInput());

        assertEquals(mLAgentGetRequest1.getAgentId(), mLAgentGetRequest.getAgentId());
        assertEquals(mLAgentGetRequest1.getAgentId(), agentId);
    }

    @Test
    public void validate_Success() {
        agentId = "not-null";
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);

        assertEquals(null, mLAgentGetRequest.validate());
    }

    @Test
    public void validate_Failure() {
        agentId = null;
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);
        assertEquals(null,mLAgentGetRequest.agentId);

        ActionRequestValidationException exception = addValidationError("ML agent id can't be null", null);
        mLAgentGetRequest.validate().equals(exception) ;
    }
    @Test
    public void fromActionRequest_Success()  throws IOException {
        agentId = "test-lmn";
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);
        assertEquals(mLAgentGetRequest.fromActionRequest(mLAgentGetRequest), mLAgentGetRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        agentId = "test-opq";
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mLAgentGetRequest.writeTo(out);
            }
        };
        MLAgentGetRequest request = mLAgentGetRequest.fromActionRequest(actionRequest);
        assertEquals(request.agentId, agentId);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        agentId = "test-rst";
        MLAgentGetRequest mLAgentGetRequest = new MLAgentGetRequest(agentId);
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
        mLAgentGetRequest.fromActionRequest(actionRequest);
    }
}


