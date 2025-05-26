/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.remove;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class MLMcpToolsRemoveNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private final Boolean deleted;

    public MLMcpToolsRemoveNodeResponse(DiscoveryNode node, Boolean deleted) {
        super(node);
        this.deleted = deleted;
    }

    public MLMcpToolsRemoveNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.deleted = in.readBoolean();
    }

    public static MLMcpToolsRemoveNodeResponse readResponse(StreamInput in) throws IOException {
        return new MLMcpToolsRemoveNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(deleted);
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("deleted");
        builder.value(deleted);
        builder.endObject();
        return builder;
    }

}
