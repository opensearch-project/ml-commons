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
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLModelStats;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatsInput;

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
     * Model stats which includes model level stats.
     *
     * Example: {model_id: { predict: { request_count: 1} }}
     */
    private Map<String, MLModelStats> modelStats;

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be read from
     */
    public MLStatsNodeResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.nodeStats = in.readMap(stream -> stream.readEnum(MLNodeLevelStat.class), StreamInput::readGenericValue);
        }
        if (in.readBoolean()) {
            this.algorithmStats = in.readMap(stream -> stream.readEnum(FunctionName.class), MLAlgoStats::new);
        }
        if (in.readBoolean()) {
            this.modelStats = in.readMap(stream -> stream.readOptionalString(), MLModelStats::new);
        }
    }

    public MLStatsNodeResponse(DiscoveryNode node, Map<MLNodeLevelStat, Object> nodeStats) {
        super(node);
        this.nodeStats = nodeStats;
    }

    public MLStatsNodeResponse(
        DiscoveryNode node,
        Map<MLNodeLevelStat, Object> nodeStats,
        Map<FunctionName, MLAlgoStats> algorithmStats,
        Map<String, MLModelStats> modelStats
    ) {
        super(node);
        this.nodeStats = nodeStats;
        this.algorithmStats = algorithmStats;
        this.modelStats = modelStats;
    }

    public boolean isEmpty() {
        return getNodeLevelStatSize() == 0 && getAlgorithmStatSize() == 0 && getModelStatSize() == 0;
    }

    /**
     * Creates a new MLStatsNodeResponse object and read the stats from an input stream
     *
     * @param in StreamInput to read from
     * @return MLStatsNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be read from
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
        if (modelStats != null) {
            out.writeBoolean(true);
            out.writeMap(modelStats, (stream, v) -> stream.writeOptionalString(v), (stream, stats) -> stats.writeTo(stream));
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
            builder.startObject(MLStatsInput.ALGORITHMS);
            for (Map.Entry<FunctionName, MLAlgoStats> stat : algorithmStats.entrySet()) {
                builder.startObject(stat.getKey().name().toLowerCase(Locale.ROOT));
                stat.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        if (modelStats != null) {
            builder.startObject(MLStatsInput.MODELS);
            for (Map.Entry<String, MLModelStats> stat : modelStats.entrySet()) {
                builder.startObject(stat.getKey());
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

    public int getModelStatSize() {
        return modelStats == null ? 0 : modelStats.size();
    }

    public boolean hasAlgorithmStats(FunctionName algorithm) {
        return algorithmStats != null && algorithmStats.containsKey(algorithm);
    }

    public boolean hasModelStats(String modelId) {
        return modelStats != null && modelStats.containsKey(modelId);
    }

    public MLAlgoStats getAlgorithmStats(FunctionName algorithm) {
        return algorithmStats == null ? null : algorithmStats.get(algorithm);
    }

    public MLModelStats getModelStats(String modelId) {
        return modelStats == null ? null : modelStats.get(modelId);
    }

    public void removeAlgorithmStats(FunctionName algorithm) {
        if (algorithmStats != null) {
            algorithmStats.remove(algorithm);
        }
    }

    public void removeModelStats(String modelId) {
        if (modelStats != null) {
            modelStats.remove(modelId);
        }
    }
}
