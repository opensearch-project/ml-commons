/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLRegisterAgentResponse extends ActionResponse implements ToXContentObject {
    public static final String AGENT_ID_FIELD = "agent_id";

    private String agentId;

    public MLRegisterAgentResponse(StreamInput in) throws IOException {
        super(in);
        this.agentId = in.readString();
    }

    public MLRegisterAgentResponse(String status) {
        this.agentId= status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(agentId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(AGENT_ID_FIELD, agentId);
        builder.endObject();
        return builder;
    }
}
