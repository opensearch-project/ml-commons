/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;

import lombok.Builder;
import lombok.Getter;

@Getter
@MLAlgoOutput(MLOutputType.TRAINING)
public class MLTrainingOutput extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.TRAINING;
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String TASK_ID_FIELD = "task_id";
    public static final String STATUS_FIELD = "status";
    private String modelId;
    private String taskId;
    private String status;

    @Builder
    public MLTrainingOutput(String modelId, String taskId, String status) {
        super(OUTPUT_TYPE);
        this.modelId = modelId;
        this.taskId = taskId;
        this.status = status;
    }

    public MLTrainingOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.modelId = in.readOptionalString();
        this.taskId = in.readOptionalString();
        this.status = in.readOptionalString();
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(modelId);
        out.writeOptionalString(taskId);
        out.writeOptionalString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (taskId != null) {
            builder.field(TASK_ID_FIELD, taskId);
        }
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
