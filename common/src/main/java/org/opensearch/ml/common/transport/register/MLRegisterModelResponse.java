/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

@Getter
public class MLRegisterModelResponse extends ActionResponse implements ToXContentObject {
    public static final String TASK_ID_FIELD = "task_id";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String STATUS_FIELD = "status";

    private String taskId;
    private String status;
    private String modelId;

    public MLRegisterModelResponse(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readString();
        this.status = in.readString();
        this.modelId = in.readOptionalString();
    }

    public MLRegisterModelResponse(String taskId, String status) {
        this.taskId = taskId;
        this.status = status;
    }

    public MLRegisterModelResponse(String taskId, String status, String modelId) {
        this.taskId = taskId;
        this.status = status;
        this.modelId = modelId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(taskId);
        out.writeString(status);
        out.writeOptionalString(modelId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(TASK_ID_FIELD, taskId);
        builder.field(STATUS_FIELD, status);
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLRegisterModelResponse) {
            return (MLRegisterModelResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLRegisterModelResponse", e);
        }
    }
}
