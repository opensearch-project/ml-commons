/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.register;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class MLMcpToolsRegisterNodeResponse extends BaseNodeResponse implements ToXContentObject {

    private final Boolean created;

    public MLMcpToolsRegisterNodeResponse(DiscoveryNode node, Boolean created) {
        super(node);
        this.created = created;
    }

    public MLMcpToolsRegisterNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.created = in.readBoolean();
    }

    public static MLMcpToolsRegisterNodeResponse readResponse(StreamInput in) throws IOException {
        return new MLMcpToolsRegisterNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(created);
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(getNode().getId());
        builder.value(created);
        builder.endObject();
        return builder;
    }

}
