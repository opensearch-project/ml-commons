/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.List;

public class MLSyncUpNodesResponse extends BaseNodesResponse<MLSyncUpNodeResponse> {

    public MLSyncUpNodesResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(MLSyncUpNodeResponse::readStats), in.readList(FailedNodeException::new));
    }

    public MLSyncUpNodesResponse(ClusterName clusterName, List<MLSyncUpNodeResponse> nodes, List<FailedNodeException> failures) {
        super(clusterName, nodes, failures);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<MLSyncUpNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<MLSyncUpNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(MLSyncUpNodeResponse::readStats);
    }

}
