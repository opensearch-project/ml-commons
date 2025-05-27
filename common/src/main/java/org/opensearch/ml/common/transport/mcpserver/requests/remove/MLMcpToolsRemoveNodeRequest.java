/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.remove;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Builder;
import lombok.Data;

@Data
public class MLMcpToolsRemoveNodeRequest extends TransportRequest {
    private List<String> tools;

    public MLMcpToolsRemoveNodeRequest(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.tools = in.readList(StreamInput::readString);
        }
    }

    @Builder
    public MLMcpToolsRemoveNodeRequest(List<String> tools) {
        this.tools = tools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (tools != null) {
            out.writeBoolean(true);
            out.writeStringArray(tools.toArray(new String[0]));
        } else {
            out.writeBoolean(false);
        }
    }

}
