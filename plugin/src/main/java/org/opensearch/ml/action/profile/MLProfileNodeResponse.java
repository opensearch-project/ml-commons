/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import lombok.Getter;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;

public class MLProfileNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    /**
     * Node level MLTasks.
     */
    @Getter
    private Map<String, MLTask> mlNodeTasks;

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
    }

    public MLProfileNodeResponse(DiscoveryNode node, Map<String, MLTask> nodeTasks) {
        super(node);
        this.mlNodeTasks = nodeTasks;
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
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (mlNodeTasks != null) {
            for (Map.Entry<String, MLTask> task : mlNodeTasks.entrySet()) {
                builder.field(task.getKey().toLowerCase(Locale.ROOT), task.getValue());
            }
        }
        return builder;
    }

    public boolean isEmpty() {
        return getNodeTasksSize() == 0;
    }

    public int getNodeTasksSize() {
        return mlNodeTasks == null ? 0 : mlNodeTasks.size();
    }
}
