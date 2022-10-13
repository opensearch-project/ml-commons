/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class LoadModelNodeRequest extends BaseNodeRequest {
    @Getter
    private LoadModelNodesRequest loadModelNodesRequest;

    public LoadModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.loadModelNodesRequest = new LoadModelNodesRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLStatsNodesRequest
     */
    public LoadModelNodeRequest(LoadModelNodesRequest request) {
        this.loadModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        loadModelNodesRequest.writeTo(out);
    }
}
