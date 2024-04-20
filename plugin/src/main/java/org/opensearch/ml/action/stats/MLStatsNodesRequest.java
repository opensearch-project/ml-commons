/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.opensearch.Version;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatLevel;
import org.opensearch.ml.stats.MLStatsInput;

import lombok.Getter;
import lombok.Setter;

public class MLStatsNodesRequest extends BaseNodesRequest<MLStatsNodesRequest> {

    @Getter
    private MLStatsInput mlStatsInput;
    @Getter
    @Setter
    private Set<String> hiddenModelIds = Collections.emptySet();

    public MLStatsNodesRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        mlStatsInput = new MLStatsInput(in);
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            hiddenModelIds = in.readSet(StreamInput::readString);
        }
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
        Version streamOutputVersion = out.getVersion();
        mlStatsInput.writeTo(out);
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            out.writeCollection(hiddenModelIds, StreamOutput::writeString);
        }
    }

    public void addNodeLevelStats(Set<MLNodeLevelStat> stats) {
        mlStatsInput.getTargetStatLevels().add(MLStatLevel.NODE);
        this.mlStatsInput.getNodeLevelStats().addAll(stats);
    }
}
