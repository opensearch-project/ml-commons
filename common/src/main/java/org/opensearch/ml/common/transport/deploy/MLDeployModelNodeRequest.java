/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import java.io.IOException;

public class MLDeployModelNodeRequest extends TransportRequest {
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
