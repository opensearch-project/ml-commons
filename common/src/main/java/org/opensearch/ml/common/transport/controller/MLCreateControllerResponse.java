/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import lombok.Builder;
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
public class MLCreateControllerResponse extends ActionResponse implements ToXContentObject {

    public static final String MODEL_ID_FIELD = "model_id";
    public static final String STATUS_FIELD = "status";

    @Getter
    String modelId;
    String status;

    public MLCreateControllerResponse(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.status = in.readString();
    }

    @Builder
    public MLCreateControllerResponse(String modelId, String status) {
        this.modelId = modelId;
        this.status = status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }

    public static MLCreateControllerResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLCreateControllerResponse) {
            return (MLCreateControllerResponse) actionResponse;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateControllerResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLCreateControllerResponse", e);
        }
    }
}
