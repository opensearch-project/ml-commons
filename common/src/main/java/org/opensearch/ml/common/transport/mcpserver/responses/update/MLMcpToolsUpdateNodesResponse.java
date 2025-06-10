/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.update;

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
public class MLMcpToolsUpdateNodesResponse extends BaseNodesResponse<MLMcpToolsUpdateNodeResponse> implements ToXContentObject {

    public MLMcpToolsUpdateNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLMcpToolsUpdateNodeResponse::readResponse), in.readList(FailedNodeException::new));
    }

    public MLMcpToolsUpdateNodesResponse(
        ClusterName clusterName,
        List<MLMcpToolsUpdateNodeResponse> nodes,
        List<FailedNodeException> failures
    ) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLMcpToolsUpdateNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLMcpToolsUpdateNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLMcpToolsUpdateNodeResponse::readResponse);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        for (MLMcpToolsUpdateNodeResponse updated : getNodes()) {
            builder.startObject(updated.getNode().getId());
            builder.field("updated");
            builder.value(updated.getUpdated());
            builder.endObject();
        }
        for (FailedNodeException failedNodeException : failures()) {
            builder.startObject(failedNodeException.nodeId());
            builder.field("error");
            builder.value(failedNodeException.getRootCause().getMessage());
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
