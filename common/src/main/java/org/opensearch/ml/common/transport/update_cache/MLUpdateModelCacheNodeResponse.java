/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update_cache;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class MLUpdateModelCacheNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    private Map<String, String> modelUpdateStatus;

    public MLUpdateModelCacheNodeResponse(DiscoveryNode node, Map<String, String> modelUpdateStatus) {
        super(node);
        this.modelUpdateStatus = modelUpdateStatus;
    }

    public MLUpdateModelCacheNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelUpdateStatus = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    public static MLUpdateModelCacheNodeResponse readStats(StreamInput in) throws IOException {
        return new MLUpdateModelCacheNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (!isModelUpdateStatusEmpty()) {
            out.writeBoolean(true);
            out.writeMap(modelUpdateStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        // Similar to deploy on node or undeploy on node response, this stat map is used to track update status on each node.
        builder.startObject("stats");
        if (!isModelUpdateStatusEmpty()) {
            for (Map.Entry<String, String> stat : modelUpdateStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isModelUpdateStatusEmpty() {
        return modelUpdateStatus == null || modelUpdateStatus.isEmpty();
    }
}
