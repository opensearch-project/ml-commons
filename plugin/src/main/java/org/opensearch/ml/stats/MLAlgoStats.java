/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

public class MLAlgoStats implements ToXContentFragment, Writeable {

    /**
     * Algorithm stats.
     * Key: ActionName enum.
     * Value: MLActionStats which contains action stat/value map.
     *
     * Example: {train: { request_count: 1}}
     */
    private Map<ActionName, MLActionStats> algoStats;

    public MLAlgoStats(StreamInput in) throws IOException {
        this.algoStats = in.readMap(stream -> stream.readEnum(ActionName.class), MLActionStats::new);
    }

    public MLAlgoStats(Map<ActionName, MLActionStats> algoStats) {
        this.algoStats = algoStats;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(algoStats, (stream, v) -> stream.writeEnum(v), (stream, stats) -> stats.writeTo(stream));
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (algoStats != null && algoStats.size() > 0) {
            for (Map.Entry<ActionName, MLActionStats> entry : algoStats.entrySet()) {
                builder.startObject(entry.getKey().name().toLowerCase(Locale.ROOT));
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
        }
        return builder;
    }

    public MLActionStats getActionStats(ActionName action) {
        return algoStats == null ? null : algoStats.get(action);
    }
}
