/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.prediction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLPredictionTaskResponse extends ActionResponse implements ToXContentObject {
    String taskId;

    String status;

    @ToString.Exclude
    DataFrame predictionResult;

    @Builder
    public MLPredictionTaskResponse(String taskId, String status, DataFrame predictionResult) {
        this.taskId = taskId;
        this.status = status;
        this.predictionResult = predictionResult;
    }

    public MLPredictionTaskResponse(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readString();
        this.status = in.readString();
        this.predictionResult = DataFrameBuilder.load(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(taskId);
        out.writeString(status);
        predictionResult.writeTo(out);

    }

    public static MLPredictionTaskResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLPredictionTaskResponse) {
            return (MLPredictionTaskResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPredictionTaskResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPredictionTaskRequest", e);
        }
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder xContentBuilder, final Params params) throws IOException {
        xContentBuilder.startObject();
        xContentBuilder.field("TaskId", taskId);
        xContentBuilder.field("Status", status);
        xContentBuilder.field("PredictionResult", predictionResult);
        xContentBuilder.endObject();
        return xContentBuilder;
    }
}
