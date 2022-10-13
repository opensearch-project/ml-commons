/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

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

public class LoadModelNodesResponse extends BaseNodesResponse<LoadModelNodeResponse> implements ToXContentObject {

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException thrown when unable to read from stream
     */
    public LoadModelNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(LoadModelNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    /**
     * Constructor
     *
     * @param clusterName name of cluster
     * @param nodes List of MLStatsNodeResponses from nodes
     * @param failures List of failures from nodes
     */
    public LoadModelNodesResponse(ClusterName clusterName, List<LoadModelNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<LoadModelNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<LoadModelNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(LoadModelNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (LoadModelNodeResponse loadStats : getNodes()) {
            node = loadStats.getNode();
            nodeId = node.getId();
            builder.startObject(nodeId);
            loadStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
