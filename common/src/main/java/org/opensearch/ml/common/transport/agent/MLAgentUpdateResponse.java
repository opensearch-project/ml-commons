/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.MLAgent;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLAgentUpdateResponse extends ActionResponse implements ToXContentObject {

    MLAgent mlAgent;

    @Builder
    public MLAgentUpdateResponse(MLAgent mlAgent) {
        this.mlAgent = mlAgent;
    }

    public MLAgentUpdateResponse(StreamInput in) throws IOException {
        super(in);
        mlAgent = MLAgent.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mlAgent.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return mlAgent.toXContent(builder, params);
    }

    public static MLAgentUpdateResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLAgentUpdateResponse) {
            return (MLAgentUpdateResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLAgentUpdateResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLAgentUpdateResponse", e);
        }
    }
}
