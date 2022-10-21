/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class MLUploadModelMetaResponse extends ActionResponse implements ToXContentObject {

    public static final String MODEL_ID_FIELD = "model_id";
    public static final String STATUS_FIELD = "status";

    private String modelId;
    private String status;

    public MLUploadModelMetaResponse(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.status = in.readString();
    }

    public MLUploadModelMetaResponse(String modelId, String status) {
        this.modelId = modelId;
        this.status= status;
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
}