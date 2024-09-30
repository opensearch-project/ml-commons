/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameType;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.PREDICTION)
public class MLPredictionOutput extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.PREDICTION;
    public static final String TASK_ID_FIELD = "task_id";
    public static final String STATUS_FIELD = "status";
    public static final String PREDICTION_RESULT_FIELD = "prediction_result";

    String taskId;
    String status;

    @ToString.Exclude
    DataFrame predictionResult;

    @Builder
    public MLPredictionOutput(String taskId, String status, DataFrame predictionResult) {
        super(OUTPUT_TYPE);
        this.taskId = taskId;
        this.status = status;
        this.predictionResult = predictionResult;
    }

    public MLPredictionOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.taskId = in.readOptionalString();
        this.status = in.readOptionalString();
        if (in.readBoolean()) {
            DataFrameType dataFrameType = in.readEnum(DataFrameType.class);
            switch (dataFrameType) {
                default:
                    predictionResult = new DefaultDataFrame(in);
                    break;
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(taskId);
        out.writeOptionalString(status);
        if (predictionResult != null) {
            out.writeBoolean(true);
            predictionResult.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
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
            builder.startObject(PREDICTION_RESULT_FIELD);
            predictionResult.toXContent(builder, params);
            builder.endObject();
        }

        builder.endObject();
        return builder;
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }
}
