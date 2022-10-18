/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.unload;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

public class UnloadModelNodeRequest extends BaseNodeRequest {
    @Getter
    private UnloadModelNodesRequest unloadModelNodesRequest;

    public UnloadModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.unloadModelNodesRequest = new UnloadModelNodesRequest(in);
    }

    public UnloadModelNodeRequest(UnloadModelNodesRequest request) {
        this.unloadModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        unloadModelNodesRequest.writeTo(out);
    }
}
