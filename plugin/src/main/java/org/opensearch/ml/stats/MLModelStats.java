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

public class MLModelStats implements ToXContentFragment, Writeable {

    /**
     * Model stats.
     * Key: Model Id.
     * Value: MLActionStats which contains action stat/value map.
     *
     * Example: {predict: { request_count: 1}}
     */
    private Map<ActionName, MLActionStats> modelStats;
    private Boolean isHidden;

    public MLModelStats(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.modelStats = in.readMap(stream -> stream.readEnum(ActionName.class), MLActionStats::new);
        }
        this.isHidden = in.readOptionalBoolean();
    }

    public MLModelStats(Map<ActionName, MLActionStats> modelStats, Boolean isHidden) {
        this.modelStats = modelStats;
        this.isHidden = isHidden;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (modelStats != null && modelStats.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(modelStats, (stream, v) -> stream.writeEnum(v), (stream, stats) -> stats.writeTo(stream));
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isHidden);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (modelStats != null && modelStats.size() > 0) {
            for (Map.Entry<ActionName, MLActionStats> entry : modelStats.entrySet()) {
                builder.startObject(entry.getKey().name().toLowerCase(Locale.ROOT));
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
        }
        if (isHidden != null) {
            builder.field("is_hidden", isHidden);
        }
        return builder;
    }

    public MLActionStats getActionStats(ActionName action) {
        return modelStats == null ? null : modelStats.get(action);
    }
}
