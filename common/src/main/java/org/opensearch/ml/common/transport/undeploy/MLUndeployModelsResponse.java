/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
public class MLUndeployModelsResponse extends ActionResponse implements ToXContentObject {
    private MLUndeployModelNodesResponse response;

    public MLUndeployModelsResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.response = new MLUndeployModelNodesResponse(in);
        }
    }

    public MLUndeployModelsResponse(MLUndeployModelNodesResponse response) {
        this.response = response;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (this.response != null) {
            out.writeBoolean(true);
            this.response.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (response != null) {
            response.toXContent(builder, params);
        } else {
            builder.startObject();
            builder.endObject();
        }
        return builder;
    }

    public static MLUndeployModelsResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLUndeployModelsResponse) {
            return (MLUndeployModelsResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUndeployModelsResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLUndeployModelsResponse", e);
        }
    }
}
