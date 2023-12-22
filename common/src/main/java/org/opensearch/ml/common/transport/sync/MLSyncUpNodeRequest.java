/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import java.io.IOException;

public class MLSyncUpNodeRequest extends TransportRequest {
    @Getter
    private MLSyncUpNodesRequest syncUpNodesRequest;

    public MLSyncUpNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.syncUpNodesRequest = new MLSyncUpNodesRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLStatsNodesRequest
     */
    public MLSyncUpNodeRequest(MLSyncUpNodesRequest request) {
        this.syncUpNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        syncUpNodesRequest.writeTo(out);
    }
}
