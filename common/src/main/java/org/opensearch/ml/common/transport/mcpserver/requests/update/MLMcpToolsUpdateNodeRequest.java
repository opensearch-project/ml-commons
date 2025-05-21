/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.update;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

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
public class MLMcpToolsUpdateNodeRequest extends ActionRequest {
    private List<UpdateMcpTool> mcpTools;

    public MLMcpToolsUpdateNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = in.readList(UpdateMcpTool::new);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Builder
    public MLMcpToolsUpdateNodeRequest(List<UpdateMcpTool> mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(mcpTools);
    }

    public static MLMcpToolsUpdateNodeRequest fromActionRequest(TransportRequest actionRequest) {
        if (actionRequest instanceof MLMcpToolsUpdateNodeRequest) {
            return (MLMcpToolsUpdateNodeRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpToolsUpdateNodeRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpToolsUpdateNodeRequest", e);
        }
    }

}
