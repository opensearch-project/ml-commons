/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import lombok.Getter;
import lombok.Setter;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;

public class MLUndeployModelNodesRequest extends BaseNodesRequest<MLUndeployModelNodesRequest> {

    @Getter
    private String[] modelIds;
    @Getter
    @Setter
    private String tenantId;

    public MLUndeployModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.modelIds = in.readOptionalStringArray();
        // TODO: will do bwc check later.
        this.tenantId = in.readOptionalString();
    }

    public MLUndeployModelNodesRequest(String[] nodeIds, String[] modelIds) {
        super(nodeIds);
        this.modelIds = modelIds;
    }

    public MLUndeployModelNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalStringArray(modelIds);
        out.writeOptionalString(tenantId);
    }

}
