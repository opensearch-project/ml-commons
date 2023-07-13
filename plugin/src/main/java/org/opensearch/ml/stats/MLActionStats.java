/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLActionStats implements ToXContentFragment, Writeable {

    /**
     * Action level stats.
     * Key: MLActionLevelStat enum.
     * Value: stats value.
     *
     * Example: { ml_action_request_count: 10}
     */
    private Map<MLActionLevelStat, Object> actionStats;

    public MLActionStats(StreamInput in) throws IOException {
        this.actionStats = in.readMap(stream -> stream.readEnum(MLActionLevelStat.class), StreamInput::readGenericValue);
    }

    public MLActionStats(Map<MLActionLevelStat, Object> algoActionStatMap) {
        this.actionStats = algoActionStatMap;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(actionStats, (stream, v) -> stream.writeEnum(v), StreamOutput::writeGenericValue);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (actionStats != null && actionStats.size() > 0) {
            for (Map.Entry<MLActionLevelStat, Object> entry : actionStats.entrySet()) {
                builder.field(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        return builder;
    }

    public Object getActionStat(MLActionLevelStat actionLevelStat) {
        return actionStats == null ? null : actionStats.get(actionLevelStat);
    }

    public int getActionStatSize() {
        return actionStats == null ? 0 : actionStats.size();
    }
}
