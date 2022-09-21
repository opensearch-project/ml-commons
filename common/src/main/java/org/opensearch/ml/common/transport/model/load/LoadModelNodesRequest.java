/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class LoadModelNodesRequest extends BaseNodesRequest<LoadModelNodesRequest> {

    @Getter
    private LoadModelInput loadModelInput;

    public LoadModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        loadModelInput = new LoadModelInput(in);
    }

    /**
     * Constructor
     * @param nodeIds nodeIds of nodes' stats to be retrieved
     * @param loadModelInput load model input
     */
    public LoadModelNodesRequest(String[] nodeIds, LoadModelInput loadModelInput) {
        super(nodeIds);
        this.loadModelInput = loadModelInput;
    }

    public LoadModelNodesRequest(DiscoveryNode[] nodeIds, LoadModelInput loadModelInput) {
        super(nodeIds);
        this.loadModelInput = loadModelInput;
    }

    /**
     * Constructor
     *
     * @param nodes nodes of nodes' stats to be retrieved
     */
    public LoadModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        loadModelInput = new LoadModelInput();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        loadModelInput.writeTo(out);
    }

}
