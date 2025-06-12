/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.profile.MLModelProfile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
public class MLProfileModelResponse implements ToXContentFragment, Writeable {
    @Setter
    private String[] targetWorkerNodes;

    @Setter
    private String[] workerNodes;

    private Map<String, MLModelProfile> mlModelProfileMap = new HashMap<>();

    private Map<String, MLTask> mlTaskMap = new HashMap<>();

    public MLProfileModelResponse(String[] targetWorkerNodes, String[] workerNodes) {
        this.targetWorkerNodes = targetWorkerNodes;
        this.workerNodes = workerNodes;
    }

    public MLProfileModelResponse(StreamInput in) throws IOException {
        this.workerNodes = in.readOptionalStringArray();
        this.targetWorkerNodes = in.readOptionalStringArray();
        if (in.readBoolean()) {
            this.mlModelProfileMap = in.readMap(StreamInput::readString, MLModelProfile::new);
        }
        if (in.readBoolean()) {
            this.mlTaskMap = in.readMap(StreamInput::readString, MLTask::new);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (targetWorkerNodes != null) {
            builder.field("target_worker_nodes", targetWorkerNodes);
        }
        if (workerNodes != null) {
            builder.field("worker_nodes", workerNodes);
        }
        if (mlModelProfileMap.size() > 0) {
            builder.startObject("nodes");
            for (Map.Entry<String, MLModelProfile> entry : mlModelProfileMap.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        if (mlTaskMap.size() > 0) {
            builder.startObject("tasks");
            for (Map.Entry<String, MLTask> entry : mlTaskMap.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeOptionalStringArray(workerNodes);
        streamOutput.writeOptionalStringArray(targetWorkerNodes);
        if (mlModelProfileMap.size() > 0) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(mlModelProfileMap, StreamOutput::writeString, (o, r) -> r.writeTo(o));
        } else {
            streamOutput.writeBoolean(false);
        }
        if (mlTaskMap.size() > 0) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(mlTaskMap, StreamOutput::writeString, (o, r) -> r.writeTo(o));
        } else {
            streamOutput.writeBoolean(false);
        }

    }
}
