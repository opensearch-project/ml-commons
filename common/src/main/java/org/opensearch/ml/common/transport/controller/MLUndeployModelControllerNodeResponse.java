/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

@Getter
@Log4j2
public class MLUndeployModelControllerNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    private Map<String, String> modelControllerUndeployStatus;

    public MLUndeployModelControllerNodeResponse(DiscoveryNode node, Map<String, String> modelControllerUndeployStatus) {
        super(node);
        this.modelControllerUndeployStatus = modelControllerUndeployStatus;
    }

    public MLUndeployModelControllerNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelControllerUndeployStatus = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    public static MLUndeployModelControllerNodeResponse readStats(StreamInput in) throws IOException {
        return new MLUndeployModelControllerNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (!isModelControllerUndeployStatusEmpty()) {
            out.writeBoolean(true);
            out.writeMap(modelControllerUndeployStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (!isModelControllerUndeployStatusEmpty()) {
            for (Map.Entry<String, String> stat : modelControllerUndeployStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isModelControllerUndeployStatusEmpty() {
        return modelControllerUndeployStatus == null || modelControllerUndeployStatus.isEmpty();
    }
}
