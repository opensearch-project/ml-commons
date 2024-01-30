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
public class MLUndeployControllerNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    private Map<String, String> controllerUndeployStatus;

    public MLUndeployControllerNodeResponse(DiscoveryNode node, Map<String, String> controllerUndeployStatus) {
        super(node);
        this.controllerUndeployStatus = controllerUndeployStatus;
    }

    public MLUndeployControllerNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.controllerUndeployStatus = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    public static MLUndeployControllerNodeResponse readStats(StreamInput in) throws IOException {
        return new MLUndeployControllerNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (!isControllerUndeployStatusEmpty()) {
            out.writeBoolean(true);
            out.writeMap(controllerUndeployStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (!isControllerUndeployStatusEmpty()) {
            for (Map.Entry<String, String> stat : controllerUndeployStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isControllerUndeployStatusEmpty() {
        return controllerUndeployStatus == null || controllerUndeployStatus.isEmpty();
    }
}
