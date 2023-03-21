/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.remote;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper=false)
@MLAlgoOutput(MLOutputType.REMOTE_INFERENCE)
public class RemoteModelOutput extends MLOutput {
    private static final MLOutputType OUTPUT_TYPE = MLOutputType.REMOTE_INFERENCE;
    public static final String TASK_ID_FIELD = "task_id";
    public static final String STATUS_FIELD = "status";
    public static final String PREDICTION_RESULT_FIELD = "prediction_result";

    String taskId;
    String status;
    String predictionResult;

    @Builder
    public RemoteModelOutput(String taskId, String status, String predictionResult) {
        super(OUTPUT_TYPE);
        this.taskId = taskId;
        this.status = status;
        this.predictionResult = predictionResult;
    }

    public RemoteModelOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.taskId = in.readOptionalString();
        this.status = in.readOptionalString();
        this.predictionResult = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(taskId);
        out.writeOptionalString(status);
        out.writeOptionalString(predictionResult);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (taskId != null) {
            builder.field(TASK_ID_FIELD, taskId);
        }
        if (status != null) {
            builder.field(STATUS_FIELD, status);
        }

        if (predictionResult != null) {
            builder.field(PREDICTION_RESULT_FIELD, predictionResult);
        }

        builder.endObject();
        return builder;
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }
}
