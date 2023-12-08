/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.transport.TransportRequest;
import java.io.IOException;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLInPlaceUpdateModelNodeRequest extends TransportRequest {
    @Getter
    private MLInPlaceUpdateModelNodesRequest mlInPlaceUpdateModelNodesRequest;

    public MLInPlaceUpdateModelNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mlInPlaceUpdateModelNodesRequest = new MLInPlaceUpdateModelNodesRequest(in);
    }

    public MLInPlaceUpdateModelNodeRequest(MLInPlaceUpdateModelNodesRequest request) {
        this.mlInPlaceUpdateModelNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlInPlaceUpdateModelNodesRequest.writeTo(out);
    }
}
