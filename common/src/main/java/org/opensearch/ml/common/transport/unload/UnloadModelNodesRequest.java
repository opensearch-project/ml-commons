/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.unload;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class UnloadModelNodesRequest extends BaseNodesRequest<UnloadModelNodesRequest> {

    @Getter
    private String[] modelIds;

    public UnloadModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelIds = in.readOptionalStringArray();
    }

    public UnloadModelNodesRequest(String[] nodeIds, String[] modelIds) {
        super(nodeIds);
        this.modelIds = modelIds;
    }

    public UnloadModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalStringArray(modelIds);
    }

}
