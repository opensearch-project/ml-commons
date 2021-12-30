/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 * Output of localized results.
 */
@Data
public class Output implements ToXContent {

    private Map<String, Result> results = new HashMap<>(); // aggregation name to result.

    /**
     * Localized entity.
     */
    @Data
    public static class Entity implements ToXContent {

        private List<String> key; // key of the entity
        private double contributionValue; // computed contribution of the entity
        private double baseValue; // base value of the entity
        private double newValue;  // new value of the entity

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field("key", this.key);
            builder.field("contribution", this.contributionValue);
            builder.field("baseValue", this.baseValue);
            builder.field("newValue", this.newValue);
            builder.endObject();
            return builder;
        }
    }

    /**
     * Localized entities are bucketized by time.
     */
    @Data
    @ToString(exclude = {"base", "counter", "completed"})
    @EqualsAndHashCode(exclude = {"base", "counter", "completed"})
    public static class Bucket implements ToXContent {

        private long startTime; // start time of the bucket
        private long endTime;   // end time of the bucket
        private double overallAggValue; // overall value of the bucket 
        private List<Entity> entities = null; // localized entities of the bucket

        private Optional<Bucket> base = Optional.empty();
        private Optional<Counter> counter = Optional.empty();
        private AtomicBoolean completed = null;

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field("start", this.startTime);
            builder.field("end", this.endTime);
            builder.field("aggregateValue", this.overallAggValue);
            if (this.entities != null && !this.entities.isEmpty()) {
                builder.field("entities", this.entities);
            }
            builder.endObject();
            return builder;
        }
    }

    /**
     * Localized result.
     */
    @Data
    public static class Result implements ToXContent {

        private List<Bucket> buckets = new ArrayList<>(); // localized results are bucketized by time

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field("buckets", this.buckets.toArray());
            builder.endObject();
            return builder;
        }
    }

    @Override
    @SneakyThrows
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
        builder.startObject();
        builder.startArray("results");
        for (Map.Entry<String, Result> entry : this.results.entrySet()) {
            builder.startObject();
            builder.field("name", entry.getKey());
            builder.field("result", entry.getValue());
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }
}
