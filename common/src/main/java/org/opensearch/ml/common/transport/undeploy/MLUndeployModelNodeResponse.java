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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

@Getter
public class MLUndeployModelNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private Map<String, String> modelUndeployStatus;
    private Map<String, String[]> modelWorkerNodeBeforeRemoval;

    public MLUndeployModelNodeResponse(DiscoveryNode node,
                                       Map<String, String> modelUndeployStatus,
                                       Map<String, String[]> modelWorkerNodeBeforeRemoval
    ) {
        super(node);
        this.modelUndeployStatus = modelUndeployStatus;
        this.modelWorkerNodeBeforeRemoval = modelWorkerNodeBeforeRemoval;
    }

    public MLUndeployModelNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelUndeployStatus = in.readMap(s -> s.readString(), s-> s.readString());
        }
        if (in.readBoolean()) {
            this.modelWorkerNodeBeforeRemoval = in.readMap(s -> s.readString(), s-> s.readOptionalStringArray());
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
        if (modelWorkerNodeBeforeRemoval != null) {
            out.writeBoolean(true);
            out.writeMap(modelWorkerNodeBeforeRemoval, StreamOutput::writeString, StreamOutput::writeOptionalStringArray);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
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
