/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.server;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

public class MLMcpServerResponse extends ActionResponse implements ToXContentObject {
    @Getter
    private Boolean acknowledgedResponse;
    @Getter
    private String mcpResponse;
    @Getter
    private Map<String, Object> error;

    public MLMcpServerResponse(Boolean acknowledgedResponse, String mcpResponse, Map<String, Object> error) {
        this.acknowledgedResponse = acknowledgedResponse;
        this.mcpResponse = mcpResponse;
        this.error = error;
    }

    public MLMcpServerResponse(StreamInput in) throws IOException {
        super(in);
        acknowledgedResponse = in.readOptionalBoolean();
        mcpResponse = in.readOptionalString();
        if (in.readBoolean()) {
            error = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(acknowledgedResponse);
        out.writeOptionalString(mcpResponse);
        if (error != null) {
            out.writeBoolean(true);
            out.writeMap(error, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (acknowledgedResponse != null) {
            builder.field("acknowledged", acknowledgedResponse);
        }
        if (mcpResponse != null) {
            builder.field("mcpResponse", mcpResponse);
        }
        if (error != null) {
            builder.field("error", error);
        }
        builder.endObject();
        return builder;
    }
}
