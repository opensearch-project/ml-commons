/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.anomalylocalization;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 * Output of localized results.
 */
@ExecuteOutput(algorithms = { FunctionName.ANOMALY_LOCALIZATION })
@Data
@NoArgsConstructor
public class AnomalyLocalizationOutput implements Output {

    public static final String FIELD_RESULTS = "results";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_RESULT = "result";

    private Map<String, Result> results = new HashMap<>(); // aggregation name to result.

    public AnomalyLocalizationOutput(StreamInput in) throws IOException {
        this.results = in.readMap(StreamInput::readString, Result::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(results, StreamOutput::writeString, (o, r) -> r.writeTo(o));
    }

    /**
     * Localized entity.
     */
    @Data
    @NoArgsConstructor
    public static class Entity implements Output {

        public static final String FIELD_KEY = "key";
        public static final String FIELD_CONTRIBUTION_VALUE = "contribution_value";
        public static final String FIELD_BASE_VALUE = "base_value";
        public static final String FIELD_NEW_VALUE = "new_value";

        private List<String> key; // key of the entity
        private double contributionValue; // computed contribution of the entity
        private double baseValue; // base value of the entity
        private double newValue;  // new value of the entity

        public Entity(StreamInput in) throws IOException {
            this.key = in.readList(StreamInput::readString);
            this.contributionValue = in.readDouble();
            this.baseValue = in.readDouble();
            this.newValue = in.readDouble();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeStringCollection(key);
            out.writeDouble(contributionValue);
            out.writeDouble(baseValue);
            out.writeDouble(newValue);
        }

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field(FIELD_KEY, this.key);
            builder.field(FIELD_CONTRIBUTION_VALUE, this.contributionValue);
            builder.field(FIELD_BASE_VALUE, this.baseValue);
            builder.field(FIELD_NEW_VALUE, this.newValue);
            builder.endObject();
            return builder;
        }
    }

    /**
     * Localized entities are bucketized by time.
     */
    @Data
    @NoArgsConstructor
    @ToString(exclude = { "base", "counter", "completed" })
    @EqualsAndHashCode(exclude = { "base", "counter", "completed" })
    public static class Bucket implements Output {

        public static final String FIELD_START_TIME = "start_time";
        public static final String FIELD_END_TIME = "end_time";
        public static final String FIELD_OVERALL_VALUE = "overall_aggregate_value";
        public static final String FIELD_ENTITIES = "entities";

        private long startTime; // start time of the bucket
        private long endTime;   // end time of the bucket
        private double overallAggValue; // overall value of the bucket
        private List<Entity> entities = null; // localized entities of the bucket

        private Optional<Bucket> base = Optional.empty();
        private Optional<Counter> counter = Optional.empty();
        private AtomicBoolean completed = null;

        public Bucket(StreamInput in) throws IOException {
            this.startTime = in.readLong();
            this.endTime = in.readLong();
            this.overallAggValue = in.readDouble();
            if (in.readBoolean()) {
                this.entities = in.readList(Entity::new);
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeLong(startTime);
            out.writeLong(endTime);
            out.writeDouble(overallAggValue);
            if (entities == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeList(entities);
            }
        }

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field(FIELD_START_TIME, this.startTime);
            builder.field(FIELD_END_TIME, this.endTime);
            builder.field(FIELD_OVERALL_VALUE, this.overallAggValue);
            if (this.entities != null && !this.entities.isEmpty()) {
                builder.field(FIELD_ENTITIES, this.entities);
            }
            builder.endObject();
            return builder;
        }
    }

    /**
     * Localized result.
     */
    @Data
    @NoArgsConstructor
    public static class Result implements Output {

        public static final String FIELD_BUCKETS = "buckets";

        private List<Bucket> buckets = new ArrayList<>(); // localized results are bucketized by time

        public Result(StreamInput in) throws IOException {
            this.buckets = in.readList(Bucket::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeList(this.buckets);
        }

        @SneakyThrows
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
            builder.startObject();
            builder.field(FIELD_BUCKETS, this.buckets.toArray());
            builder.endObject();
            return builder;
        }
    }

    @Override
    @SneakyThrows
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) {
        builder.startArray(FIELD_RESULTS);
        for (Map.Entry<String, Result> entry : this.results.entrySet()) {
            builder.startObject();
            builder.field(FIELD_NAME, entry.getKey());
            builder.field(FIELD_RESULT, entry.getValue());
            builder.endObject();
        }
        builder.endArray();
        return builder;
    }

    public static AnomalyLocalizationOutput parse(XContentParser parser) throws IOException {
        AnomalyLocalizationOutput output = new AnomalyLocalizationOutput();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            switch (parser.currentName()) {
                case FIELD_RESULTS:
                    parseResultMapEntry(parser, output);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
        return output;
    }

    private static void parseResultMapEntry(XContentParser parser, AnomalyLocalizationOutput output) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            String key = null;
            AnomalyLocalizationOutput.Result result = new AnomalyLocalizationOutput.Result();
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                switch (parser.currentName()) {
                    case FIELD_NAME:
                        parser.nextToken();
                        key = parser.text();
                        break;
                    case FIELD_RESULT:
                        parseResult(parser, result);
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
            ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
            output.getResults().put(key, result);
        }
        ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.currentToken(), parser);
    }

    private static void parseResult(XContentParser parser, AnomalyLocalizationOutput.Result result) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            switch (parser.currentName()) {
                case AnomalyLocalizationOutput.Result.FIELD_BUCKETS:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        AnomalyLocalizationOutput.Bucket bucket = new AnomalyLocalizationOutput.Bucket();
                        parseBucket(parser, bucket);
                        result.getBuckets().add(bucket);
                    }
                    ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.currentToken(), parser);
                    break;
            }
        }
        ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
    }

    private static void parseBucket(XContentParser parser, AnomalyLocalizationOutput.Bucket bucket) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            switch (parser.currentName()) {
                case AnomalyLocalizationOutput.Bucket.FIELD_START_TIME:
                    parser.nextToken();
                    bucket.setStartTime(parser.longValue());
                    break;
                case AnomalyLocalizationOutput.Bucket.FIELD_END_TIME:
                    parser.nextToken();
                    bucket.setEndTime(parser.longValue());
                    break;
                case AnomalyLocalizationOutput.Bucket.FIELD_OVERALL_VALUE:
                    parser.nextToken();
                    bucket.setOverallAggValue(parser.doubleValue());
                    break;
                case AnomalyLocalizationOutput.Bucket.FIELD_ENTITIES:
                    parseEntities(parser, bucket);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
    }

    private static void parseEntities(XContentParser parser, AnomalyLocalizationOutput.Bucket bucket) throws IOException {
        List<AnomalyLocalizationOutput.Entity> entities = new ArrayList<>();
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            AnomalyLocalizationOutput.Entity entity = new AnomalyLocalizationOutput.Entity();
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                switch (parser.currentName()) {
                    case AnomalyLocalizationOutput.Entity.FIELD_KEY:
                        List<String> key = new ArrayList<>();
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            key.add(parser.text());
                        }
                        entity.setKey(key);
                        break;
                    case AnomalyLocalizationOutput.Entity.FIELD_CONTRIBUTION_VALUE:
                        parser.nextToken();
                        entity.setContributionValue(parser.doubleValue());
                        break;
                    case AnomalyLocalizationOutput.Entity.FIELD_BASE_VALUE:
                        parser.nextToken();
                        entity.setBaseValue(parser.doubleValue());
                        break;
                    case AnomalyLocalizationOutput.Entity.FIELD_NEW_VALUE:
                        parser.nextToken();
                        entity.setNewValue(parser.doubleValue());
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
            entities.add(entity);
        }
        bucket.setEntities(entities);
        ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.currentToken(), parser);
    }
}
