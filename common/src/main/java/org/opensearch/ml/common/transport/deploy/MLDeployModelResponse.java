/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

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
import org.opensearch.ml.common.MLTaskType;

import lombok.Getter;

@Getter
public class MLDeployModelResponse extends ActionResponse implements ToXContentObject {
    public static final String TASK_ID_FIELD = "task_id";
    public static final String TASK_TYPE_FIELD = "task_type";
    public static final String STATUS_FIELD = "status";

    private String taskId;
    private MLTaskType taskType;
    private String status;

    public MLDeployModelResponse(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readString();
        this.taskType = in.readEnum(MLTaskType.class);
        this.status = in.readString();
    }

    public MLDeployModelResponse(String taskId, MLTaskType mlTaskType, String status) {
        this.taskId = taskId;
        this.taskType = mlTaskType;
        this.status = status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(taskId);
        out.writeEnum(taskType);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(TASK_ID_FIELD, taskId);
        if (taskType != null) {
            builder.field(TASK_TYPE_FIELD, taskType);
        }
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }

    public static MLDeployModelResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLDeployModelResponse) {
            return (MLDeployModelResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLDeployModelResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLDeployModelResponse", e);
        }

    }
}
