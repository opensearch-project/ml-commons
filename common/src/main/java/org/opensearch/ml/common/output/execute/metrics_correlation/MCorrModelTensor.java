/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metrics_correlation;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Data;

@Data
public class MCorrModelTensor implements Writeable, ToXContentObject {

    public static final String EVENT_WINDOW = "event_window";
    public static final String EVENT_PATTERN = "event_pattern";
    public static final String SUSPECTED_METRICS = "suspected_metrics";

    private float[] event_window;
    private float[] event_pattern;
    private long[] suspected_metrics;

    @Builder
    public MCorrModelTensor(float[] event_window, float[] event_pattern, long[] suspected_metrics) {
        this.event_window = event_window;
        this.event_pattern = event_pattern;
        this.suspected_metrics = suspected_metrics;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (event_window != null) {
            builder.field(EVENT_WINDOW, event_window);
        }
        if (event_pattern != null) {
            builder.field(EVENT_PATTERN, event_pattern);
        }
        if (suspected_metrics != null) {
            builder.field(SUSPECTED_METRICS, suspected_metrics);
        }
        builder.endObject();
        return builder;
    }

    public MCorrModelTensor(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.event_window = in.readFloatArray();
        }
        if (in.readBoolean()) {
            this.event_pattern = in.readFloatArray();
        }
        if (in.readBoolean()) {
            this.suspected_metrics = in.readLongArray();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (event_window != null) {
            out.writeBoolean(true);
            out.writeFloatArray(event_window);
        } else {
            out.writeBoolean(false);
        }

        if (event_pattern != null) {
            out.writeBoolean(true);
            out.writeFloatArray(event_pattern);
        } else {
            out.writeBoolean(false);
        }
        if (suspected_metrics != null) {
            out.writeBoolean(true);
            out.writeLongArray(suspected_metrics);
        } else {
            out.writeBoolean(false);
        }
    }
}
