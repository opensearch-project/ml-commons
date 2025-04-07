/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.ML_TASK_OUTPUT)
public class MLTaskOutput extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.ML_TASK_OUTPUT;
    public static final String TASK_ID_FIELD = "task_id";
    public static final String STATUS_FIELD = "status";

    // This field will be created for offline batch prediction tasks containing details of the batch job as outputted by the remote server.
    public static final String EXECUTE_RESPONSE_FIELD = "execute_response";

    String taskId;
    String status;
    Map<String, Object> executeResponse;

    @Builder
    public MLTaskOutput(String taskId, String status, Map<String, Object> executeResponse) {
        super(OUTPUT_TYPE);
        this.taskId = taskId;
        this.status = status;
        this.executeResponse = executeResponse;
    }

    public MLTaskOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.taskId = in.readOptionalString();
        this.status = in.readOptionalString();
        if (in.readBoolean()) {
            this.executeResponse = in.readMap(s -> s.readString(), s -> s.readGenericValue());
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(taskId);
        out.writeOptionalString(status);
        if (executeResponse != null) {
            out.writeBoolean(true);
            out.writeMap(executeResponse, StreamOutput::writeString, StreamOutput::writeGenericValue);
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

        if (executeResponse != null) {
            builder.field(EXECUTE_RESPONSE_FIELD, executeResponse);
        }

        builder.endObject();
        return builder;
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }
}
