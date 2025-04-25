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
        this.tools = in.readList(StreamInput::readString);
    }

    @Builder
    public MLMcpToolsRemoveNodeRequest(List<String> tools) {
        this.tools = tools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (tools != null) {
            out.writeBoolean(true);
            for (String tool : tools) {
                out.writeString(tool);
            }
        } else {
            out.writeBoolean(false);
        }
    }

}
