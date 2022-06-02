/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Set;

import lombok.Getter;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatLevel;
import org.opensearch.ml.stats.MLStatsInput;

public class MLStatsNodesRequest extends BaseNodesRequest<MLStatsNodesRequest> {

    @Getter
    private MLStatsInput mlStatsInput;

    public MLStatsNodesRequest(StreamInput in) throws IOException {
        super(in);
        mlStatsInput = new MLStatsInput(in);
    }

    /**
     * Constructor
     * @param nodeIds nodeIds of nodes' stats to be retrieved
     * @param mlStatsInput ML status input
     */
    public MLStatsNodesRequest(String[] nodeIds, MLStatsInput mlStatsInput) {
        super(nodeIds);
        this.mlStatsInput = mlStatsInput;
    }

    /**
     * Constructor
     *
     * @param nodes nodes of nodes' stats to be retrieved
     */
    public MLStatsNodesRequest(DiscoveryNode... nodes) {
        super(nodes);
        mlStatsInput = new MLStatsInput();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlStatsInput.writeTo(out);
    }

    public void addNodeLevelStats(Set<MLNodeLevelStat> stats) {
        mlStatsInput.getTargetStatLevels().add(MLStatLevel.NODE);
        this.mlStatsInput.getNodeLevelStats().addAll(stats);
    }
}
