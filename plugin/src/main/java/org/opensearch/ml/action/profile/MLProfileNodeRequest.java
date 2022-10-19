/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;

import lombok.Getter;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

public class MLProfileNodeRequest extends TransportRequest {
    @Getter
    private MLProfileRequest mlProfileRequest;

    public MLProfileNodeRequest(StreamInput in) throws IOException {
        super(in);
        this.mlProfileRequest = new MLProfileRequest(in);
    }

    /**
     * Constructor
     *
     * @param request MLTaskProfileRequest
     */
    public MLProfileNodeRequest(MLProfileRequest request) {
        this.mlProfileRequest = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlProfileRequest.writeTo(out);
    }
}
