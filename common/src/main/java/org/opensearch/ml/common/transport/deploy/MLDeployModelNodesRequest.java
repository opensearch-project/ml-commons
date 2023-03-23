/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class MLDeployModelNodesRequest extends BaseNodesRequest<MLDeployModelNodesRequest> {

    @Getter
    private MLDeployModelInput MLDeployModelInput;

    public MLDeployModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        MLDeployModelInput = new MLDeployModelInput(in);
    }

    /**
     * Constructor
     * @param nodeIds nodeIds of nodes' stats to be retrieved
     * @param MLDeployModelInput deploy model input
     */
    public MLDeployModelNodesRequest(String[] nodeIds, MLDeployModelInput MLDeployModelInput) {
        super(nodeIds);
        this.MLDeployModelInput = MLDeployModelInput;
    }

    public MLDeployModelNodesRequest(DiscoveryNode[] nodeIds, MLDeployModelInput MLDeployModelInput) {
        super(nodeIds);
        this.MLDeployModelInput = MLDeployModelInput;
    }

    /**
     * Constructor
     *
     * @param nodes nodes of nodes' stats to be retrieved
     */
    public MLDeployModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        MLDeployModelInput = new MLDeployModelInput();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        MLDeployModelInput.writeTo(out);
    }

}
