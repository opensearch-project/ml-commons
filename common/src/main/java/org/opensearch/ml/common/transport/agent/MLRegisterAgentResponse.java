/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
public class MLRegisterAgentResponse extends ActionResponse implements ToXContentObject {
    public static final String AGENT_ID_FIELD = "agent_id";

    private String agentId;

    public MLRegisterAgentResponse(StreamInput in) throws IOException {
        super(in);
        this.agentId = in.readString();
    }

    public MLRegisterAgentResponse(String agentId) {
        this.agentId= agentId;
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

    public static MLRegisterAgentResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLRegisterAgentResponse) {
            return (MLRegisterAgentResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterAgentResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLRegisterAgentResponse", e);
        }
    }
}
