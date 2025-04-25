/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Builder;
import lombok.Data;

@Data
public class MLMcpToolsRegisterNodeRequest extends TransportRequest {
    private McpTools mcpTools;

    public MLMcpToolsRegisterNodeRequest(StreamInput in) throws IOException {
        this.mcpTools = new McpTools(in);
    }

    @Builder
    public MLMcpToolsRegisterNodeRequest(McpTools mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mcpTools.writeTo(out);
    }

}
