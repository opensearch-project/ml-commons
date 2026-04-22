/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.MLAgent;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLMcpConnectorListToolsResponse extends ActionResponse implements ToXContentObject {

    private final List<McpToolInfo> tools;

    @Builder
    public MLMcpConnectorListToolsResponse(List<McpToolInfo> tools) {
        this.tools = tools == null ? Collections.emptyList() : tools;
    }

    public MLMcpConnectorListToolsResponse(StreamInput in) throws IOException {
        super(in);
        this.tools = in.readList(McpToolInfo::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(tools);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startArray(MLAgent.TOOLS_FIELD);
        for (McpToolInfo tool : tools) {
            tool.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public static MLMcpConnectorListToolsResponse fromActionResponse(ActionResponse response) {
        if (response instanceof MLMcpConnectorListToolsResponse) {
            return (MLMcpConnectorListToolsResponse) response;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            response.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpConnectorListToolsResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLMcpConnectorListToolsResponse", e);
        }
    }
}
