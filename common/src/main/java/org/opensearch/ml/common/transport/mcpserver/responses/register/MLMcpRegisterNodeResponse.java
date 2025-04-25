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
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class MLMcpRegisterNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private final Boolean created;

    public MLMcpRegisterNodeResponse(DiscoveryNode node, Boolean created) {
        super(node);
        this.created = created;
    }

    public MLMcpRegisterNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.created = in.readBoolean();
    }

    public static MLMcpRegisterNodeResponse readResponse(StreamInput in) throws IOException {
        return new MLMcpRegisterNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(created);
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("created");
        builder.value(created);
        builder.endObject();
        return builder;
    }

}
