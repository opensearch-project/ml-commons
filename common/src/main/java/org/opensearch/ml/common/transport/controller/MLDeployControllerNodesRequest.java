/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import java.io.IOException;

public class MLDeployControllerNodesRequest extends BaseNodesRequest<MLDeployControllerNodesRequest> {

    @Getter
    private String modelId;

    public MLDeployControllerNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
    }

    public MLDeployControllerNodesRequest(String[] nodeIds, String modelId) {
        super(nodeIds);
        this.modelId = modelId;
    }

    public MLDeployControllerNodesRequest(DiscoveryNode[] nodeIds, String modelId) {
        super(nodeIds);
        this.modelId = modelId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
    }
}
