/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class MLUndeployModelNodeRequest extends BaseNodeRequest {
    @Getter
    private MLUndeployModelNodesRequest MLUndeployModelNodesRequest;

    public MLUndeployModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.MLUndeployModelNodesRequest = new MLUndeployModelNodesRequest(in);
    }

    public MLUndeployModelNodeRequest(MLUndeployModelNodesRequest request) {
        this.MLUndeployModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        MLUndeployModelNodesRequest.writeTo(out);
    }
}
