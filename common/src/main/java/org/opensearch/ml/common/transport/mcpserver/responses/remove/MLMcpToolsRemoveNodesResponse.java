/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.mcpserver.responses.remove;

import java.io.IOException;
import java.util.List;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLMcpToolsRemoveNodesResponse extends BaseNodesResponse<MLMcpToolsRemoveNodeResponse> implements ToXContentObject {

    public MLMcpToolsRemoveNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLMcpToolsRemoveNodeResponse::readResponse), in.readList(FailedNodeException::new));
    }

    public MLMcpToolsRemoveNodesResponse(
        ClusterName clusterName,
        List<MLMcpToolsRemoveNodeResponse> nodes,
        List<FailedNodeException> failures
    ) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLMcpToolsRemoveNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLMcpToolsRemoveNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLMcpToolsRemoveNodeResponse::readResponse);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        for (MLMcpToolsRemoveNodeResponse removed : getNodes()) {
            builder.startObject(removed.getNode().getId());
            builder.field("removed");
            builder.value(removed.getDeleted());
            builder.endObject();
        }
        for (FailedNodeException failure : failures()) {
            builder.startObject(failure.nodeId());
            builder.field("not_found_exception");
            builder.value(failure.getRootCause().getMessage());
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
