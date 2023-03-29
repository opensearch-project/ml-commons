/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

public class MLCreateModelGroupResponse extends ActionResponse implements ToXContentObject {

    public static final String MODEL_GROUP_ID_FIELD = "model_group_id";
    public static final String STATUS_FIELD = "status";

    private String modelGroupId;
    private String status;

    public MLCreateModelGroupResponse(StreamInput in) throws IOException {
        super(in);
        this.modelGroupId = in.readString();
        this.status = in.readString();
    }

    public MLCreateModelGroupResponse(String modelId, String status) {
        this.modelGroupId = modelId;
        this.status= status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelGroupId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
