/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.opensearch.Version;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.profile.MLProfileInput;

import lombok.Getter;
import lombok.Setter;

public class MLProfileRequest extends BaseNodesRequest<MLProfileRequest> {

    @Getter
    private MLProfileInput mlProfileInput;
    @Getter
    @Setter
    private Set<String> hiddenModelIds = Collections.emptySet();

    public MLProfileRequest(StreamInput input) throws IOException {
        super(input);
        Version streamInputVersion = input.getVersion();
        mlProfileInput = new MLProfileInput(input);
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            hiddenModelIds = input.readSet(StreamInput::readString);
        }
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
        Version streamOutputVersion = output.getVersion();
        super.writeTo(output);
        mlProfileInput.writeTo(output);
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            output.writeCollection(hiddenModelIds, StreamOutput::writeString);
        }
    }
}
