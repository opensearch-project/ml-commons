/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Builder;
import lombok.Data;

@Data
public class MLMcpToolsRegisterNodeRequest extends ActionRequest {
    private McpTools mcpTools;

    public MLMcpToolsRegisterNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = new McpTools(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Builder
    public MLMcpToolsRegisterNodeRequest(McpTools mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mcpTools.writeTo(out);
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
