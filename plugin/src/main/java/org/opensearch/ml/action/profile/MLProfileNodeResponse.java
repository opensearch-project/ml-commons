/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Map;

import lombok.Getter;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.profile.MLModelProfile;

@Getter
public class MLProfileNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    /**
     * Node level MLTasks.
     */
    private Map<String, MLTask> mlNodeTasks;
    /**
     * Node level ML model profile.
     */
    private Map<String, MLModelProfile> mlNodeModels;

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public MLProfileNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.mlNodeTasks = in.readMap(StreamInput::readString, MLTask::new);
        }
        if (in.readBoolean()) {
            this.mlNodeModels = in.readMap(StreamInput::readString, MLModelProfile::new);
        }
    }

    public MLProfileNodeResponse(DiscoveryNode node, Map<String, MLTask> nodeTasks, Map<String, MLModelProfile> mlNodeModels) {
        super(node);
        this.mlNodeTasks = nodeTasks;
        this.mlNodeModels = mlNodeModels;
    }

    public static MLProfileNodeResponse readProfile(StreamInput in) throws IOException {
        return new MLProfileNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (mlNodeTasks != null) {
            out.writeBoolean(true);
            out.writeMap(mlNodeTasks, StreamOutput::writeString, (o, r) -> r.writeTo(o));
        } else {
            out.writeBoolean(false);
        }
        if (mlNodeModels != null) {
            out.writeBoolean(true);
            out.writeMap(mlNodeModels, StreamOutput::writeString, (o, r) -> r.writeTo(o));
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (mlNodeTasks != null && mlNodeTasks.size() > 0) {
            builder.startObject("tasks");
            for (Map.Entry<String, MLTask> entry : mlNodeTasks.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        if (mlNodeModels != null && mlNodeModels.size() > 0) {
            builder.startObject("models");
            for (Map.Entry<String, MLModelProfile> entry : mlNodeModels.entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }
        return builder;
    }

    public boolean isEmpty() {
        return (mlNodeTasks == null || mlNodeTasks.size() == 0) && (mlNodeModels == null || mlNodeModels.size() == 0);
    }

    public int getNodeTasksSize() {
        return mlNodeTasks == null ? 0 : mlNodeTasks.size();
    }

    public int getNodeModelsSize() {
        return mlNodeModels == null ? 0 : mlNodeModels.size();
    }
}
