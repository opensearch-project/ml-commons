/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLSyncUpNodesRequest extends BaseNodesRequest<MLSyncUpNodesRequest> {

    @Getter
    private MLSyncUpInput syncUpInput;

    public MLSyncUpNodesRequest(StreamInput in) throws IOException {
        super(in);
        syncUpInput = new MLSyncUpInput(in);
    }

    public MLSyncUpNodesRequest(String[] nodeIds, MLSyncUpInput syncUpInput) {
        super(nodeIds);
        this.syncUpInput = syncUpInput;
    }

    public MLSyncUpNodesRequest(DiscoveryNode[] nodeIds, MLSyncUpInput syncUpInput) {
        super(nodeIds);
        this.syncUpInput = syncUpInput;
    }

    public MLSyncUpNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        syncUpInput = new MLSyncUpInput();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        syncUpInput.writeTo(out);
    }

}
