/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import java.io.IOException;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.action.support.nodes.BaseNodeRequest;


public class MLUndeployModelControllerNodeRequest extends BaseNodeRequest {
    @Getter
    private MLUndeployModelControllerNodesRequest undeployModelControllerNodesRequest;

    public MLUndeployModelControllerNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.undeployModelControllerNodesRequest = new MLUndeployModelControllerNodesRequest(in);
    }

    public MLUndeployModelControllerNodeRequest(MLUndeployModelControllerNodesRequest request) {
        this.undeployModelControllerNodesRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        undeployModelControllerNodesRequest.writeTo(out);
    }

}
