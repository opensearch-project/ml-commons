package org.opensearch.ml.common.transport.agent;

import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;

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
    public void fromActionRequest() throws IOException {
        agentId = "test-lmn";
        MLAgentDeleteRequest mLAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        assertEquals(mLAgentDeleteRequest.fromActionRequest(mLAgentDeleteRequest), mLAgentDeleteRequest);

    }
}
