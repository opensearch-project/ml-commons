/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLDeployModelNodesRequest extends BaseNodesRequest<MLDeployModelNodesRequest> {

    @Getter
    private MLDeployModelInput mlDeployModelInput;

    public MLDeployModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        mlDeployModelInput = new MLDeployModelInput(in);
    }

    /**
     * Constructor
     * @param nodeIds nodeIds of nodes' stats to be retrieved
     * @param mlDeployModelInput deploy model input
     */
    public MLDeployModelNodesRequest(String[] nodeIds, MLDeployModelInput mlDeployModelInput) {
        super(nodeIds);
        this.mlDeployModelInput = mlDeployModelInput;
    }

    public MLDeployModelNodesRequest(DiscoveryNode[] nodeIds, MLDeployModelInput mlDeployModelInput) {
        super(nodeIds);
        this.mlDeployModelInput = mlDeployModelInput;
    }

    /**
     * Constructor
     *
     * @param nodes nodes of nodes' stats to be retrieved
     */
    public MLDeployModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        mlDeployModelInput = new MLDeployModelInput();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlDeployModelInput.writeTo(out);
    }

}
