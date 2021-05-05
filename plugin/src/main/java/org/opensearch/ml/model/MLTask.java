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

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import java.io.IOException;
import java.time.Instant;

@Data
public class MLTask implements Writeable {
    private final String taskId;
    private final String taskType;
    private String state;
    private final Instant createTime;
    private final String modelId;

    @Builder
    public MLTask(String taskId, String taskType, String state, Instant createTime, String modelId) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.state = state;
        this.createTime = createTime;
        this.modelId = modelId;
    }

    public MLTask(StreamInput input) throws IOException {
        this.taskId = input.readOptionalString();
        this.taskType = input.readOptionalString();
        this.state = input.readOptionalString();
        this.createTime = input.readOptionalInstant();
        this.modelId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(taskId);
        out.writeOptionalString(taskType);
        out.writeOptionalString(state);
        out.writeOptionalInstant(createTime);
        out.writeOptionalString(modelId);
    }
}
