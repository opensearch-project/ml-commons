/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;

import lombok.Getter;

import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

public class MLStatsNodeRequest extends BaseNodeRequest {
    @Getter
    private MLStatsNodesRequest mlStatsNodesRequest;

    /**
     * Constructor
     */
    public MLStatsNodeRequest() {
        super();
    }

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
