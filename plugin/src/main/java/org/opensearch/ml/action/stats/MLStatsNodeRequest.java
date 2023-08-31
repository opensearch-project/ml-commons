/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;

import org.opensearch.transport.TransportRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLStatsNodeRequest extends TransportRequest {
    @Getter
    private MLStatsNodesRequest mlStatsNodesRequest;

    public MLStatsNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mlStatsNodesRequest = new MLStatsNodesRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLStatsNodesRequest
     */
    public MLStatsNodeRequest(MLStatsNodesRequest request) {
        this.mlStatsNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlStatsNodesRequest.writeTo(out);
    }
}
