/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import java.io.IOException;
import java.util.List;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLUndeployModelNodesResponse extends BaseNodesResponse<MLUndeployModelNodeResponse> implements ToXContentObject {

    public MLUndeployModelNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLUndeployModelNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    public MLUndeployModelNodesResponse(
        ClusterName clusterName,
        List<MLUndeployModelNodeResponse> nodes,
        List<FailedNodeException> failures
    ) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLUndeployModelNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLUndeployModelNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLUndeployModelNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (MLUndeployModelNodeResponse undeployStats : getNodes()) {
            if (!undeployStats.isEmpty()) {
                node = undeployStats.getNode();
                nodeId = node.getId();
                builder.startObject(nodeId);
                undeployStats.toXContent(builder, params);
                builder.endObject();
            }
        }
        builder.endObject();
        return builder;
    }
}
