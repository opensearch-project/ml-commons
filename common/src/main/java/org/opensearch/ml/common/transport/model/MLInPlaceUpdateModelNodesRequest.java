/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import java.io.IOException;

public class MLInPlaceUpdateModelNodesRequest extends BaseNodesRequest<MLInPlaceUpdateModelNodesRequest> {

    @Getter
    private String modelId;
    @Getter
    private boolean updatePredictorFlag;

    public MLInPlaceUpdateModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.updatePredictorFlag = in.readBoolean();
    }

    public MLInPlaceUpdateModelNodesRequest(String[] nodeIds, String modelId, boolean updatePredictorFlag) {
        super(nodeIds);
        this.modelId = modelId;
        this.updatePredictorFlag = updatePredictorFlag;
    }

    public MLInPlaceUpdateModelNodesRequest(DiscoveryNode[] nodeIds, String modelId, boolean updatePredictorFlag) {
        super(nodeIds);
        this.modelId = modelId;
        this.updatePredictorFlag = updatePredictorFlag;
    }

    public MLInPlaceUpdateModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        this.updatePredictorFlag = false;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeBoolean(updatePredictorFlag);
    }
}
