/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import lombok.Getter;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

@Getter
public class MLUndeployModelNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private Map<String, String> modelUndeployStatus;
    private Map<String, Integer> modelWorkerNodeCounts;

    public MLUndeployModelNodeResponse(DiscoveryNode node,
                                       Map<String, String> modelUndeployStatus,
                                       Map<String, Integer> modelWorkerNodeCounts) {
        super(node);
        this.modelUndeployStatus = modelUndeployStatus;
        this.modelWorkerNodeCounts = modelWorkerNodeCounts;
    }

    public MLUndeployModelNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelUndeployStatus = in.readMap(s -> s.readString(), s-> s.readString());
        }
        if (in.readBoolean()) {
            this.modelWorkerNodeCounts = in.readMap(s -> s.readString(), s-> s.readInt());
        }
    }

    public static MLUndeployModelNodeResponse readStats(StreamInput in) throws IOException {
        return new MLUndeployModelNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (modelUndeployStatus != null) {
            out.writeBoolean(true);
            out.writeMap(modelUndeployStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (modelWorkerNodeCounts != null) {
            out.writeBoolean(true);
            out.writeMap(modelWorkerNodeCounts, StreamOutput::writeString, StreamOutput::writeInt);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (modelUndeployStatus != null) {
            for (Map.Entry<String, String> stat : modelUndeployStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isEmpty() {
        return modelUndeployStatus == null || modelUndeployStatus.size() == 0;
    }
}
