/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.sync;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class MLSyncUpNodeRequest extends BaseNodeRequest {
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
