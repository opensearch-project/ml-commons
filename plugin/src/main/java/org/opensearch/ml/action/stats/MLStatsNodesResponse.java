/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */


package org.opensearch.ml.action.stats;

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

public class MLStatsNodesResponse extends BaseNodesResponse<MLStatsNodeResponse> implements ToXContentObject {
    private static final String NODES_KEY = "nodes";

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException thrown when unable to read from stream
     */
    public MLStatsNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLStatsNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    /**
     * Constructor
     *
     * @param clusterName name of cluster
     * @param nodes List of MLStatsNodeResponses from nodes
     * @param failures List of failures from nodes
     */
    public MLStatsNodesResponse(ClusterName clusterName, List<MLStatsNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLStatsNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLStatsNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject(NODES_KEY);
        for (MLStatsNodeResponse mlStats : getNodes()) {
            node = mlStats.getNode();
            nodeId = node.getId();
            builder.startObject(nodeId);
            mlStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
