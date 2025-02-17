/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;

import org.opensearch.Version;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;
import lombok.Setter;

public class MLUndeployModelNodesRequest extends BaseNodesRequest<MLUndeployModelNodesRequest> {

    @Getter
    private String[] modelIds;
    @Getter
    @Setter
    private String tenantId;

    public MLUndeployModelNodesRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.modelIds = in.readOptionalStringArray();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
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
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalStringArray(modelIds);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

}
