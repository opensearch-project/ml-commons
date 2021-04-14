/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
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
