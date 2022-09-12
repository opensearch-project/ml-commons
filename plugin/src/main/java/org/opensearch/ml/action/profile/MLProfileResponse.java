/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.List;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

public class MLProfileResponse extends BaseNodesResponse<MLProfileNodeResponse> implements ToXContentObject {

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException thrown when unable to read from stream
     */
    public MLProfileResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLProfileNodeResponse::readProfile), in.readList(FailedNodeException::new));
    }

    /**
     * Constructor
     *
     * @param clusterName name of cluster
     * @param nodes List of MLTaskProfileNodeResponses from nodes
     * @param failures List of failures from nodes
     */
    public MLProfileResponse(ClusterName clusterName, List<MLProfileNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLProfileNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLProfileNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLProfileNodeResponse::readProfile);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject("nodes");
        for (MLProfileNodeResponse mlTaskNodeProfile : getNodes()) {
            node = mlTaskNodeProfile.getNode();
            nodeId = node.getId();
            builder.startObject(nodeId);
            mlTaskNodeProfile.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
