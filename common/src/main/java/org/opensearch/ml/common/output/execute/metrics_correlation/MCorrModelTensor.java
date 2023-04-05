/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metrics_correlation;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Data
public class MCorrModelTensor implements Writeable, ToXContentObject {
    private float[] range;
    private float[] event;
    private long[] metrics;

    @Builder
    public MCorrModelTensor(float[] range, float[] event, long[] metrics) {
        this.range = range;
        this.event = event;
        this.metrics = metrics;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (range != null) {
            builder.field("range", range);
        }
        if (event != null) {
            builder.field("event", event);
        }
        if (metrics != null) {
            builder.field("metrics", metrics);
        }
        builder.endObject();
        return builder;
    }

    public MCorrModelTensor(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.range = in.readFloatArray();
        }
        if (in.readBoolean()) {
            this.event = in.readFloatArray();
        }
        if (in.readBoolean()) {
            this.metrics = in.readLongArray();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (range != null) {
            out.writeBoolean(true);
            out.writeFloatArray(range);
        } else {
            out.writeBoolean(false);
        }

        if (event != null) {
            out.writeBoolean(true);
            out.writeFloatArray(event);
        } else {
            out.writeBoolean(false);
        }
        if (metrics != null) {
            out.writeBoolean(true);
            out.writeLongArray(metrics);
        } else {
            out.writeBoolean(false);
        }
    }
}
