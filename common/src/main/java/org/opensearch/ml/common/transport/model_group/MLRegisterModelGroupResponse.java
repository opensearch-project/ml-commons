/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

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

import lombok.Getter;

@Getter
public class MLRegisterModelGroupResponse extends ActionResponse implements ToXContentObject {

    public static final String MODEL_GROUP_ID_FIELD = "model_group_id";
    public static final String STATUS_FIELD = "status";

    @Getter
    private String modelGroupId;
    private String status;

    public MLRegisterModelGroupResponse(StreamInput in) throws IOException {
        super(in);
        this.modelGroupId = in.readString();
        this.status = in.readString();
    }

    public MLRegisterModelGroupResponse(String modelGroupId, String status) {
        this.modelGroupId = modelGroupId;
        this.status = status;
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

    public static MLRegisterModelGroupResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLRegisterModelGroupResponse) {
            return (MLRegisterModelGroupResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelGroupResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLRegisterModelGroupResponse", e);
        }

    }
}
