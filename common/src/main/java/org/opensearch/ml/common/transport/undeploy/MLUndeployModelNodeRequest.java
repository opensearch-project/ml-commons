/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import java.io.IOException;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

public class MLUndeployModelNodeRequest extends TransportRequest {
    @Getter
    private MLUndeployModelNodesRequest mlUndeployModelNodesRequest;

    public MLUndeployModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(in);
    }

    public MLUndeployModelNodeRequest(MLUndeployModelNodesRequest request) {
        this.mlUndeployModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlUndeployModelNodesRequest.writeTo(out);
    }
}
