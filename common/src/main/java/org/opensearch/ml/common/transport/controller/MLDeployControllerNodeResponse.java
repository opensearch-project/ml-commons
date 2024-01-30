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
public class MLDeployControllerNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    private Map<String, String> controllerDeployStatus;

    public MLDeployControllerNodeResponse(DiscoveryNode node, Map<String, String> controllerDeployStatus) {
        super(node);
        this.controllerDeployStatus = controllerDeployStatus;
    }

    public MLDeployControllerNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.controllerDeployStatus = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    public static MLDeployControllerNodeResponse readStats(StreamInput in) throws IOException {
        return new MLDeployControllerNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (!isControllerDeployStatusEmpty()) {
            out.writeBoolean(true);
            out.writeMap(controllerDeployStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (!isControllerDeployStatusEmpty()) {
            for (Map.Entry<String, String> stat : controllerDeployStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isControllerDeployStatusEmpty() {
        return controllerDeployStatus == null || controllerDeployStatus.isEmpty();
    }
}
