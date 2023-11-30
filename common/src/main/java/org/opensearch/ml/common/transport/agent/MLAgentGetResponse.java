/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import lombok.Builder;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.MLAgent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MLAgentGetResponse extends ActionResponse implements ToXContentObject {
    MLAgent mlAgent;

    @Builder
    public MLAgentGetResponse(MLAgent mlAgent) {
        this.mlAgent = mlAgent;
    }

    public MLAgentGetResponse(StreamInput in) throws IOException {
        super(in);
        mlAgent = MLAgent.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException{
        mlAgent.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlAgent.toXContent(xContentBuilder, params);
    }

    public static MLAgentGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLAgentGetResponse) {
            return (MLAgentGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLAgentGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLAgentGetResponse", e);
        }
    }

}
