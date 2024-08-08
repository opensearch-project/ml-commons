/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLDeployModelNodeRequest extends BaseNodeRequest {
    @Getter
    private MLDeployModelNodesRequest MLDeployModelNodesRequest;

    public MLDeployModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.MLDeployModelNodesRequest = new MLDeployModelNodesRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLStatsNodesRequest
     */
    public MLDeployModelNodeRequest(MLDeployModelNodesRequest request) {
        this.MLDeployModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        MLDeployModelNodesRequest.writeTo(out);
    }
}
