/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;
@Log4j2
public class LoadModelNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    /**
     * Key is modelName + ":" + version
     */
    private Map<String, String> modelLoadStatus;

    public LoadModelNodeResponse(DiscoveryNode node, Map<String, String> modelLoadStatus) {
        super(node);
        this.modelLoadStatus = modelLoadStatus;
    }
    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public LoadModelNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelLoadStatus = in.readMap(s -> s.readString(), s-> s.readString());
        }

    }

    /**
     * Creates a new UnloadNodeResponse object and reMLs in the stats from an input stream
     *
     * @param in StreamInput to reML from
     * @return UnloadModelNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public static LoadModelNodeResponse readStats(StreamInput in) throws IOException {
        return new LoadModelNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (modelLoadStatus != null && modelLoadStatus.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(modelLoadStatus, (stream, v) -> stream.writeString(v), (stream, stats) -> stream.writeString(stats));
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (modelLoadStatus != null && modelLoadStatus.size() > 0) {
            for (Map.Entry<String, String> stat : modelLoadStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

}
