/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update_cache;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import java.io.IOException;

public class MLUpdateModelCacheNodesRequest extends BaseNodesRequest<MLUpdateModelCacheNodesRequest> {

    @Getter
    private String modelId;
    @Getter
    private boolean predictorUpdate;

    public MLUpdateModelCacheNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.predictorUpdate = in.readBoolean();
    }

    public MLUpdateModelCacheNodesRequest(String[] nodeIds, String modelId, boolean predictorUpdate) {
        super(nodeIds);
        this.modelId = modelId;
        this.predictorUpdate = predictorUpdate;
    }

    public MLUpdateModelCacheNodesRequest(DiscoveryNode[] nodeIds, String modelId, boolean predictorUpdate) {
        super(nodeIds);
        this.modelId = modelId;
        this.predictorUpdate = predictorUpdate;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeBoolean(predictorUpdate);
    }
}
