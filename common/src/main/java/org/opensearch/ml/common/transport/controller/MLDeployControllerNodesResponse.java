/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;

public class MLDeployControllerNodesResponse extends BaseNodesResponse<MLDeployControllerNodeResponse>
        implements ToXContentObject {

    public MLDeployControllerNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLDeployControllerNodeResponse::readStats),
                in.readList(FailedNodeException::new));
    }

    public MLDeployControllerNodesResponse(ClusterName clusterName, List<MLDeployControllerNodeResponse> nodes,
            List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLDeployControllerNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLDeployControllerNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLDeployControllerNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (MLDeployControllerNodeResponse deployStats : getNodes()) {
            if (!deployStats.isControllerDeployStatusEmpty()) {
                node = deployStats.getNode();
                nodeId = node.getId();
                builder.startObject(nodeId);
                deployStats.toXContent(builder, params);
                builder.endObject();
            }
        }
        builder.endObject();
        return builder;
    }
}
