/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.anomalylocalization;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.input.Input;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregatorFactories;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Information about aggregate, time, etc to localize.
 */
@ExecuteInput(algorithms = { FunctionName.ANOMALY_LOCALIZATION })
@Data
@AllArgsConstructor
public class AnomalyLocalizationInput implements Input {

    public static final String FIELD_INDEX_NAME = "index_name";
    public static final String FIELD_ATTTRIBUTE_FIELD_NAMES = "attribute_field_names";
    public static final String FIELD_AGGREGATIONS = "aggregations";
    public static final String FIELD_TIME_FIELD_NAME = "time_field_name";
    public static final String FIELD_START_TIME = "start_time";
    public static final String FIELD_END_TIME = "end_time";
    public static final String FIELD_MIN_TIME_INTERVAL = "min_time_interval";
    public static final String FIELD_NUM_OUTPUTS = "num_outputs";
    public static final String FIELD_ANOMALY_START_TIME = "anomaly_start_time";
    public static final String FIELD_FILTER_QUERY = "filter_query";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_ENTRY = new NamedXContentRegistry.Entry(
        Input.class,
        new ParseField(FunctionName.ANOMALY_LOCALIZATION.name()),
        parser -> parse(parser)
    );

    public static AnomalyLocalizationInput parse(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        String indexName = null;
        List<String> attributeFieldNames = new ArrayList<>();
        List<AggregationBuilder> aggregations = new ArrayList<>();
        String timeFieldName = null;
        long startTime = 0;
        long endTime = 0;
        long minTimeInterval = 0;
        int numOutputs = 0;
        Optional<Long> anomalyStartTime = Optional.empty();
        Optional<QueryBuilder> filterQuery = Optional.empty();

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            switch (parser.currentName()) {
                case FIELD_INDEX_NAME:
                    parser.nextToken();
                    indexName = parser.text();
                    break;
                case FIELD_ATTTRIBUTE_FIELD_NAMES:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        attributeFieldNames.add(parser.text());
                    }
                    ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.currentToken(), parser);
                    break;
                case FIELD_AGGREGATIONS:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                        aggregations.addAll(AggregatorFactories.parseAggregators(parser).getAggregatorFactories());
                        ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
                    }
                    ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.currentToken(), parser);
                    break;
                case FIELD_TIME_FIELD_NAME:
                    parser.nextToken();
                    timeFieldName = parser.text();
                    break;
                case FIELD_START_TIME:
                    parser.nextToken();
                    startTime = parser.longValue(false);
                    break;
                case FIELD_END_TIME:
                    parser.nextToken();
                    endTime = parser.longValue(false);
                    break;
                case FIELD_MIN_TIME_INTERVAL:
                    parser.nextToken();
                    minTimeInterval = parser.longValue(false);
                    break;
                case FIELD_NUM_OUTPUTS:
                    parser.nextToken();
                    numOutputs = parser.intValue(false);
                    break;
                case FIELD_ANOMALY_START_TIME:
                    parser.nextToken();
                    anomalyStartTime = Optional.of(parser.longValue(false));
                    break;
                case FIELD_FILTER_QUERY:
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    filterQuery = Optional.of(parseInnerQueryBuilder(parser));
                    ensureExpectedToken(XContentParser.Token.END_OBJECT, parser.currentToken(), parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AnomalyLocalizationInput(
            indexName,
            attributeFieldNames,
            aggregations,
            timeFieldName,
            startTime,
            endTime,
            minTimeInterval,
            numOutputs,
            anomalyStartTime,
            filterQuery
        );
    }

    private final String indexName; // name pattern of the data index
    private final List<String> attributeFieldNames; // name of the field to localize/slice with
    private final List<AggregationBuilder> aggregations; // aggregate data to localize/slice on
    private final String timeFieldName; // name of the timestamp field
    private final long startTime; // start of entire time range, including normal and anomaly
    private final long endTime; // end of entire time range, including normal and anomaly
    private final long minTimeInterval; // minimal time interval/bucket
    private final int numOutputs; // max number of values from localization/slicing
    private final Optional<Long> anomalyStartTime; // time when anomaly change starts
    private final Optional<QueryBuilder> filterQuery; // filter of data

    public AnomalyLocalizationInput(StreamInput in) throws IOException {
        this.indexName = in.readString();
        this.attributeFieldNames = Arrays.asList(in.readStringArray());
        this.aggregations = in.readNamedWriteableList(AggregationBuilder.class);
        this.timeFieldName = in.readString();
        this.startTime = in.readLong();
        this.endTime = in.readLong();
        this.minTimeInterval = in.readLong();
        this.numOutputs = in.readInt();
        this.anomalyStartTime = Optional.ofNullable(in.readOptionalLong());
        this.filterQuery = Optional.ofNullable(in.readOptionalNamedWriteable(QueryBuilder.class));
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.ANOMALY_LOCALIZATION;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(FIELD_INDEX_NAME, indexName);
        builder.field(FIELD_ATTTRIBUTE_FIELD_NAMES, attributeFieldNames);
        builder.startArray(FIELD_AGGREGATIONS);
        for (AggregationBuilder agg : aggregations) {
            builder.startObject();
            builder.value(agg);
            builder.endObject();
        }
        builder.endArray();
        builder.field(FIELD_TIME_FIELD_NAME, timeFieldName);
        builder.field(FIELD_START_TIME, startTime);
        builder.field(FIELD_END_TIME, endTime);
        builder.field(FIELD_MIN_TIME_INTERVAL, minTimeInterval);
        builder.field(FIELD_NUM_OUTPUTS, numOutputs);
        if (anomalyStartTime.isPresent()) {
            builder.field(FIELD_ANOMALY_START_TIME, anomalyStartTime.get());
        }
        if (filterQuery.isPresent()) {
            builder.field(FIELD_FILTER_QUERY, filterQuery.get());
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(indexName);
        out.writeStringArray(attributeFieldNames.toArray(new String[0]));
        out.writeNamedWriteableList(aggregations);
        out.writeString(timeFieldName);
        out.writeLong(startTime);
        out.writeLong(endTime);
        out.writeLong(minTimeInterval);
        out.writeInt(numOutputs);
        out.writeOptionalLong(anomalyStartTime.orElse(null));
        out.writeOptionalNamedWriteable(filterQuery.orElse(null));
    }
}
