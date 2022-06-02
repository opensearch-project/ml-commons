/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLNodeLevelStat;

public class MLStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    /**
     * Node level stats.
     */
    private Map<MLNodeLevelStat, Object> nodeStats;
    /**
     * Algorithm stats which includes stats level stats.
     *
     * Example: {kmeans: { train: { request_count: 1} }}
     */
    private Map<FunctionName, MLAlgoStats> algorithmStats;

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public MLStatsNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.nodeStats = in.readMap(stream -> stream.readEnum(MLNodeLevelStat.class), StreamInput::readGenericValue);
        }
        if (in.readBoolean()) {
            this.algorithmStats = in.readMap(stream -> stream.readEnum(FunctionName.class), MLAlgoStats::new);
        }
    }

    public MLStatsNodeResponse(DiscoveryNode node, Map<MLNodeLevelStat, Object> nodeStats) {
        super(node);
        this.nodeStats = nodeStats;
    }

    public MLStatsNodeResponse(DiscoveryNode node, Map<MLNodeLevelStat, Object> nodeStats, Map<FunctionName, MLAlgoStats> algorithmStats) {
        super(node);
        this.nodeStats = nodeStats;
        this.algorithmStats = algorithmStats;
    }

    public boolean isEmpty() {
        return getNodeLevelStatSize() == 0 && getAlgorithmStatSize() == 0;
    }

    /**
     * Creates a new MLStatsNodeResponse object and reMLs in the stats from an input stream
     *
     * @param in StreamInput to reML from
     * @return MLStatsNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public static MLStatsNodeResponse readStats(StreamInput in) throws IOException {
        return new MLStatsNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (nodeStats != null) {
            out.writeBoolean(true);
            out.writeMap(nodeStats, (stream, v) -> stream.writeEnum(v), StreamOutput::writeGenericValue);
        } else {
            out.writeBoolean(false);
        }
        if (algorithmStats != null) {
            out.writeBoolean(true);
            out.writeMap(algorithmStats, (stream, v) -> stream.writeEnum(v), (stream, stats) -> stats.writeTo(stream));
        } else {
            out.writeBoolean(false);
        }
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (nodeStats != null) {
            for (Map.Entry<MLNodeLevelStat, Object> stat : nodeStats.entrySet()) {
                builder.field(stat.getKey().name().toLowerCase(Locale.ROOT), stat.getValue());
            }
        }
        if (algorithmStats != null) {
            builder.startObject("algorithms");
            for (Map.Entry<FunctionName, MLAlgoStats> stat : algorithmStats.entrySet()) {
                builder.startObject(stat.getKey().name().toLowerCase(Locale.ROOT));
                stat.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        return builder;
    }

    public Object getNodeLevelStat(MLNodeLevelStat nodeLevelStat) {
        return nodeStats == null ? null : nodeStats.get(nodeLevelStat);
    }

    public int getNodeLevelStatSize() {
        return nodeStats == null ? 0 : nodeStats.size();
    }

    public int getAlgorithmStatSize() {
        return algorithmStats == null ? 0 : algorithmStats.size();
    }

    public boolean hasAlgorithmStats(FunctionName algorithm) {
        return algorithmStats == null ? false : algorithmStats.containsKey(algorithm);
    }

    public MLAlgoStats getAlgorithmStats(FunctionName algorithm) {
        return algorithmStats == null ? null : algorithmStats.get(algorithm);
    }

    public void removeAlgorithmStats(FunctionName algorithm) {
        if (algorithmStats != null) {
            algorithmStats.remove(algorithm);
        }
    }
}
