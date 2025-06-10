/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Builder;
import lombok.Data;

/**
 * This class extends {@link ActionRequest} and is used to register tools on nodes in the cluster.
 * It's used by {@link TransportNodesAction } which is used to send requests to multiple nodes in the cluster,
 * the {@link ActionRequest } contains only the actual requests that will be sent to the nodes.
 * The {@link MLMcpToolsRegisterNodesRequest} is an encapsulation of this class and node ids.
 */
@Data
public class MLMcpToolsRegisterNodeRequest extends ActionRequest {
    private List<McpToolRegisterInput> mcpTools;

    public MLMcpToolsRegisterNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = in.readList(McpToolRegisterInput::new);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Builder
    public MLMcpToolsRegisterNodeRequest(List<McpToolRegisterInput> mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(mcpTools);
    }

    public static MLMcpToolsRegisterNodeRequest fromActionRequest(TransportRequest actionRequest) {
        if (actionRequest instanceof MLMcpToolsRegisterNodeRequest) {
            return (MLMcpToolsRegisterNodeRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpToolsRegisterNodeRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpToolsRegisterNodeRequest", e);
        }
    }

}
