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

package org.opensearch.ml.common.transport.training;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLTrainingTaskResponse extends ActionResponse {
    String status;
    String taskId;

    @Builder
    public MLTrainingTaskResponse(String status, String taskId) {
        this.status = status;
        this.taskId = taskId;
    }

    public MLTrainingTaskResponse(StreamInput in) throws IOException {
        this.status = in.readString();
        this.taskId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(status);
        out.writeString(taskId);
    }

    public static MLTrainingTaskResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLTrainingTaskResponse) {
            return (MLTrainingTaskResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTrainingTaskResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTrainingTaskResponse", e);
        }
    }
}
