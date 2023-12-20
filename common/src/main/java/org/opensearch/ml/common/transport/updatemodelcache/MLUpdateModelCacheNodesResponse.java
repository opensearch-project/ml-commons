/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.updatemodelcache;

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

public class MLUpdateModelCacheNodesResponse extends BaseNodesResponse<MLUpdateModelCacheNodeResponse> implements ToXContentObject {

    public MLUpdateModelCacheNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLUpdateModelCacheNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    public MLUpdateModelCacheNodesResponse(ClusterName clusterName, List<MLUpdateModelCacheNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLUpdateModelCacheNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLUpdateModelCacheNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLUpdateModelCacheNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        String nodeId;
        DiscoveryNode node;
        builder.startObject();
        for (MLUpdateModelCacheNodeResponse updateStats : getNodes()) {
            if (!updateStats.isModelUpdateStatusEmpty()) {
                node = updateStats.getNode();
                nodeId = node.getId();
                builder.startObject(nodeId);
                updateStats.toXContent(builder, params);
                builder.endObject();
            }
        }
        builder.endObject();
        return builder;
    }
}
