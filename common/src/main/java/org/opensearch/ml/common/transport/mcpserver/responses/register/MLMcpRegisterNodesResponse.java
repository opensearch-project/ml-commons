/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.register;

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
public class MLMcpRegisterNodesResponse extends BaseNodesResponse<MLMcpRegisterNodeResponse> implements ToXContentObject {

    public MLMcpRegisterNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLMcpRegisterNodeResponse::readResponse), in.readList(FailedNodeException::new));
    }

    public MLMcpRegisterNodesResponse(ClusterName clusterName, List<MLMcpRegisterNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLMcpRegisterNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLMcpRegisterNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLMcpRegisterNodeResponse::readResponse);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        for (MLMcpRegisterNodeResponse created : getNodes()) {
            builder.startObject(created.getNode().getId());
            builder.field("created");
            builder.value(created.getCreated());
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
