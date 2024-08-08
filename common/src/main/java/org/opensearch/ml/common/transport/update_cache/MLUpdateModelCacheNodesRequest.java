/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update_cache;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLUpdateModelCacheNodesRequest extends BaseNodesRequest<MLUpdateModelCacheNodesRequest> {

    @Getter
    private String modelId;

    public MLUpdateModelCacheNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
    }

    public MLUpdateModelCacheNodesRequest(String[] nodeIds, String modelId) {
        super(nodeIds);
        this.modelId = modelId;
    }

    public MLUpdateModelCacheNodesRequest(DiscoveryNode[] nodeIds, String modelId) {
        super(nodeIds);
        this.modelId = modelId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
    }
}
