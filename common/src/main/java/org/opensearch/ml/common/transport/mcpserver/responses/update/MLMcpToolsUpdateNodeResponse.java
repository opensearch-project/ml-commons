/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.update;

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
public class MLMcpToolsUpdateNodeResponse extends BaseNodeResponse implements ToXContentObject {

    private final Boolean updated;

    public MLMcpToolsUpdateNodeResponse(DiscoveryNode node, Boolean updated) {
        super(node);
        this.updated = updated;
    }

    public MLMcpToolsUpdateNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.updated = in.readBoolean();
    }

    public static MLMcpToolsUpdateNodeResponse readResponse(StreamInput in) throws IOException {
        return new MLMcpToolsUpdateNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(updated);
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(getNode().getId());
        builder.value(updated);
        builder.endObject();
        return builder;
    }

}
