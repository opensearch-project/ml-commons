/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import java.io.IOException;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

@Log4j2
public class MLPredictRequestStats implements ToXContentFragment, Writeable {

    private final Long count;
    private final Double max;
    private final Double min;
    private final Double average;
    private final Double p50;
    private final Double p90;
    private final Double p99;

    @Builder
    public MLPredictRequestStats(Long count, Double max, Double min, Double average, Double p50, Double p90, Double p99) {
        this.count = count;
        this.max = max;
        this.min = min;
        this.average = average;
        this.p50 = p50;
        this.p90 = p90;
        this.p99 = p99;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (count != null) {
            builder.field("count", count);
        }
        if (max != null) {
            builder.field("max", max);
        }
        if (min != null) {
            builder.field("min", min);
        }
        if (average != null) {
            builder.field("average", average);
        }
        if (p50 != null) {
            builder.field("p50", p50);
        }
        if (p90 != null) {
            builder.field("p90", p90);
        }
        if (p99 != null) {
            builder.field("p99", p99);
        }
        builder.endObject();
        return builder;
    }

    public MLPredictRequestStats(StreamInput in) throws IOException {
        this.count = in.readOptionalLong();
        this.max = in.readOptionalDouble();
        this.min = in.readOptionalDouble();
        this.average = in.readOptionalDouble();
        this.p50 = in.readOptionalDouble();
        this.p90 = in.readOptionalDouble();
        this.p99 = in.readOptionalDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalLong(count);
        out.writeOptionalDouble(max);
        out.writeOptionalDouble(min);
        out.writeOptionalDouble(average);
        out.writeOptionalDouble(p50);
        out.writeOptionalDouble(p90);
        out.writeOptionalDouble(p99);
    }
}
