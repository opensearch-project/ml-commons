/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

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

public class MLDeployModelNodesResponse extends BaseNodesResponse<MLDeployModelNodeResponse> implements ToXContentObject {

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException thrown when unable to read from stream
     */
    public MLDeployModelNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLDeployModelNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    /**
     * Constructor
     *
     * @param clusterName name of cluster
     * @param nodes List of MLStatsNodeResponses from nodes
     * @param failures List of failures from nodes
     */
    public MLDeployModelNodesResponse(ClusterName clusterName, List<MLDeployModelNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLDeployModelNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLDeployModelNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLDeployModelNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (MLDeployModelNodeResponse deployStats : getNodes()) {
            node = deployStats.getNode();
            nodeId = node.getId();
            builder.startObject(nodeId);
            deployStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
