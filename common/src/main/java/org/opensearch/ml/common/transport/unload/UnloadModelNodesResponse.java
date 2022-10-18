/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.unload;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;

public class UnloadModelNodesResponse extends BaseNodesResponse<UnloadModelNodeResponse> implements ToXContentObject {

    public UnloadModelNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(UnloadModelNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    public UnloadModelNodesResponse(ClusterName clusterName, List<UnloadModelNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<UnloadModelNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<UnloadModelNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(UnloadModelNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (UnloadModelNodeResponse unloadStats : getNodes()) {
            if (!unloadStats.isEmpty()) {
                node = unloadStats.getNode();
                nodeId = node.getId();
                builder.startObject(nodeId);
                unloadStats.toXContent(builder, params);
                builder.endObject();
            }
        }
        builder.endObject();
        return builder;
    }
}
