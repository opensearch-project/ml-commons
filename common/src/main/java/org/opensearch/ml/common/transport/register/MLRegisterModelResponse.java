/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import lombok.Getter;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLRegisterModelResponse extends ActionResponse implements ToXContentObject {
    public static final String TASK_ID_FIELD = "task_id";
    public static final String MODEL_ID_TASK = "model_id";
    public static final String STATUS_FIELD = "status";

    private String taskId;
    private String modelId;
    private String status;

    public MLRegisterModelResponse(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readOptionalString();
        this.modelId = in.readOptionalString();
        this.status = in.readOptionalString();
    }

    public MLRegisterModelResponse(String taskId, String modelId, String status) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.status= status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(taskId);
        out.writeOptionalString(modelId);
        out.writeOptionalString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (taskId != null) {
            builder.field(TASK_ID_FIELD, taskId);
        }
        if (modelId != null) {
            builder.field(MODEL_ID_TASK, modelId);
        }
        if (status != null) {
            builder.field(STATUS_FIELD, status);
        }
        builder.endObject();
        return builder;
    }
}
