/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLDeployModelNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    /**
     * Key is modelName + ":" + version
     */
    private Map<String, String> modelDeployStatus;

    public MLDeployModelNodeResponse(DiscoveryNode node, Map<String, String> modelDeployStatus) {
        super(node);
        this.modelDeployStatus = modelDeployStatus;
    }

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be read from
     */
    public MLDeployModelNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.modelDeployStatus = in.readMap(s -> s.readString(), s -> s.readString());
        }

    }

    /**
     * Creates a new MLDeployModelNodeResponse object and read the stats from an input stream
     *
     * @param in StreamInput to read from
     * @return MLDeployModelNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be read from
     */
    public static MLDeployModelNodeResponse readStats(StreamInput in) throws IOException {
        return new MLDeployModelNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (modelDeployStatus != null && modelDeployStatus.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(modelDeployStatus, (stream, v) -> stream.writeString(v), (stream, stats) -> stream.writeString(stats));
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("stats");
        if (modelDeployStatus != null && modelDeployStatus.size() > 0) {
            for (Map.Entry<String, String> stat : modelDeployStatus.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }
        }
        builder.endObject();
        return builder;
    }

}
