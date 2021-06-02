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

package org.opensearch.ml.model;

import java.io.IOException;
import java.time.Instant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

@Getter
@EqualsAndHashCode
public class MLTask implements Writeable {
    private final String taskId;
    private final MLTaskType taskType;
    @Setter
    private MLTaskState state;
    private final Instant createTime;
    private final String modelId;
    @Setter
    private String error;

    @Builder
    public MLTask(String taskId, MLTaskType taskType, MLTaskState state, Instant createTime, String modelId, String error) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.state = state;
        this.createTime = createTime;
        this.modelId = modelId;
        this.error = error;
    }

    public MLTask(StreamInput input) throws IOException {
        this.taskId = input.readOptionalString();
        this.taskType = input.readEnum(MLTaskType.class);
        this.state = input.readEnum(MLTaskState.class);
        this.createTime = input.readOptionalInstant();
        this.modelId = input.readOptionalString();
        this.error = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(taskId);
        out.writeEnum(taskType);
        out.writeEnum(state);
        out.writeOptionalInstant(createTime);
        out.writeOptionalString(modelId);
        out.writeOptionalString(error);
    }
}
