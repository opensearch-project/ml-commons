/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.unload;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

public class UnloadModelNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    /**
     * Key is modelName + ":" + version
     */
    private Map<String, String> modelUnloadStatus;

    public UnloadModelNodeResponse(DiscoveryNode node, Map<String, String> modelUnloadStatus) {
        super(node);
        this.modelUnloadStatus = modelUnloadStatus;
    }
    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public UnloadModelNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelUnloadStatus = in.readMap(s -> s.readString(), s-> s.readString());
        }
    }

    /**
     * Creates a new UnloadNodeResponse object and reMLs in the stats from an input stream
     *
     * @param in StreamInput to reML from
     * @return UnloadModelNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public static UnloadModelNodeResponse readStats(StreamInput in) throws IOException {
        return new UnloadModelNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (modelUnloadStatus != null) {
            out.writeBoolean(true);
            out.writeMap(modelUnloadStatus, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (modelUnloadStatus != null) {
            for (Map.Entry<String, String> stat : modelUnloadStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

    public boolean isEmpty() {
        return modelUnloadStatus == null || modelUnloadStatus.size() == 0;
    }
}
