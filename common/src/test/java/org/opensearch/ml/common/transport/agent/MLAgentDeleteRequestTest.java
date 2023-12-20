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

public class MLAgentDeleteRequestTest {
    String agentId;

    @Test
    public void constructor_AgentId() {
        agentId = "test-abc";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        assertEquals(mLAgentDeleteRequest.agentId,agentId);
    }

    @Test
    public void writeTo() throws IOException {
        agentId = "test-hij";

        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        BytesStreamOutput output = new BytesStreamOutput();
        mLAgentDeleteRequest.writeTo(output);

        MLAgentDeleteRequest mLAgentDeleteRequest1 = new MLAgentDeleteRequest(output.bytes().streamInput());

        assertEquals(mLAgentDeleteRequest.agentId, mLAgentDeleteRequest1.agentId);
        assertEquals(agentId, mLAgentDeleteRequest1.agentId);
    }

    @Test
    public void validate_Success() {
        agentId = "not-null";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);

        assertEquals(null, mLAgentDeleteRequest.validate());
    }

    @Test
    public void validate_Failure() {
        agentId = null;
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        assertEquals(null,mLAgentDeleteRequest.agentId);

        ActionRequestValidationException exception = addValidationError("ML agent id can't be null", null);
        mLAgentDeleteRequest.validate().equals(exception) ;
    }

    @Test
    public void fromActionRequest_Success() throws IOException {
        agentId = "test-lmn";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        assertEquals(mLAgentDeleteRequest.fromActionRequest(mLAgentDeleteRequest), mLAgentDeleteRequest);

    }
    @Test
    public void fromActionRequest_Success_fromActionRequest() throws IOException {
        agentId = "test-opq";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mLAgentDeleteRequest.writeTo(out);
            }
        };
        MLAgentDeleteRequest request = mLAgentDeleteRequest.fromActionRequest(actionRequest);
        assertEquals(request.agentId, agentId);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        agentId = "test-rst";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
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
        mLAgentDeleteRequest.fromActionRequest(actionRequest);
    }
}
