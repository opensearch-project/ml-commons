/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import java.io.IOException;
import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLDeployControllerNodeRequest extends BaseNodeRequest {
    @Getter
    private MLDeployControllerNodesRequest deployControllerNodesRequest;

    public MLDeployControllerNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.deployControllerNodesRequest = new MLDeployControllerNodesRequest(in);
    }

    public MLDeployControllerNodeRequest(MLDeployControllerNodesRequest request) {
        this.deployControllerNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        deployControllerNodesRequest.writeTo(out);
    }
}
