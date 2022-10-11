/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;

import lombok.Getter;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.profile.MLProfileInput;

public class MLProfileRequest extends BaseNodesRequest<MLProfileRequest> {

    @Getter
    private MLProfileInput mlProfileInput;

    public MLProfileRequest(StreamInput input) throws IOException {
        super(input);
        mlProfileInput = new MLProfileInput(input);
    }

    /**
     * Constructor
     * @param nodeIds nodeIds of nodes' profiles to be retrieved
     */
    public MLProfileRequest(String[] nodeIds, MLProfileInput mlProfileInput) {
        super(nodeIds);
        this.mlProfileInput = mlProfileInput;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        mlProfileInput.writeTo(output);
    }
}
