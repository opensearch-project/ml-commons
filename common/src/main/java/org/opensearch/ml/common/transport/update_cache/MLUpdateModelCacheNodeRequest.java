/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update_cache;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

public class MLUpdateModelCacheNodeRequest extends BaseNodeRequest {
    @Getter
    private MLUpdateModelCacheNodesRequest updateModelCacheNodesRequest;

    public MLUpdateModelCacheNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.updateModelCacheNodesRequest = new MLUpdateModelCacheNodesRequest(in);
    }

    public MLUpdateModelCacheNodeRequest(MLUpdateModelCacheNodesRequest request) {
        this.updateModelCacheNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        updateModelCacheNodesRequest.writeTo(out);
    }
}
