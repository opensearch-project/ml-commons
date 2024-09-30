/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Getter;

public class MLUndeployControllerNodeRequest extends TransportRequest {
    @Getter
    private MLUndeployControllerNodesRequest undeployControllerNodesRequest;

    public MLUndeployControllerNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.undeployControllerNodesRequest = new MLUndeployControllerNodesRequest(in);
    }

    public MLUndeployControllerNodeRequest(MLUndeployControllerNodesRequest request) {
        this.undeployControllerNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        undeployControllerNodesRequest.writeTo(out);
    }

}
