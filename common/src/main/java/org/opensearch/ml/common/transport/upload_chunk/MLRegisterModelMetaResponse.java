/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

public class MLRegisterModelMetaResponse extends ActionResponse implements ToXContentObject {

    public static final String MODEL_ID_FIELD = "model_id";
    public static final String STATUS_FIELD = "status";

    @Getter
    private String modelId;
    @Getter
    private String status;

    public MLRegisterModelMetaResponse(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.status = in.readString();
    }

    public MLRegisterModelMetaResponse(String modelId, String status) {
        this.modelId = modelId;
        this.status = status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
