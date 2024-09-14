/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.rcf;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Builder;
import lombok.Data;

@Data
@MLAlgoParameter(algorithms = { FunctionName.FIT_RCF })
public class FitRCFParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.FIT_RCF.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String NUMBER_OF_TREES = "number_of_trees";
    public static final String SHINGLE_SIZE = "shingle_size";
    public static final String SAMPLE_SIZE = "sample_size";
    public static final String OUTPUT_AFTER = "output_after";
    public static final String TIME_DECAY = "time_decay";
    public static final String ANOMALY_RATE = "anomaly_rate";
    public static final String TIME_FIELD = "time_field";
    public static final String DATE_FORMAT = "date_format";
    public static final String TIME_ZONE = "time_zone";
    private Integer numberOfTrees;
    private Integer shingleSize;
    private Integer sampleSize;
    private Integer outputAfter;
    private Double timeDecay;
    private Double anomalyRate;
    private String timeField;
    private String dateFormat;
    private String timeZone;

    @Builder
    public FitRCFParams(
        Integer numberOfTrees,
        Integer shingleSize,
        Integer sampleSize,
        Integer outputAfter,
        Double timeDecay,
        Double anomalyRate,
        String timeField,
        String dateFormat,
        String timeZone
    ) {
        this.numberOfTrees = numberOfTrees;
        this.shingleSize = shingleSize;
        this.sampleSize = sampleSize;
        this.outputAfter = outputAfter;
        this.timeDecay = timeDecay;
        this.anomalyRate = anomalyRate;
        this.timeField = timeField;
        this.dateFormat = dateFormat;
        this.timeZone = timeZone;
    }

    public FitRCFParams(StreamInput in) throws IOException {
        this.numberOfTrees = in.readOptionalInt();
        this.shingleSize = in.readOptionalInt();
        this.sampleSize = in.readOptionalInt();
        this.outputAfter = in.readOptionalInt();
        this.timeDecay = in.readOptionalDouble();
        this.anomalyRate = in.readOptionalDouble();
        this.timeField = in.readOptionalString();
        this.dateFormat = in.readOptionalString();
        this.timeZone = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(numberOfTrees);
        out.writeOptionalInt(shingleSize);
        out.writeOptionalInt(sampleSize);
        out.writeOptionalInt(outputAfter);
        out.writeOptionalDouble(timeDecay);
        out.writeOptionalDouble(anomalyRate);
        out.writeOptionalString(timeField);
        out.writeOptionalString(dateFormat);
        out.writeOptionalString(timeZone);
    }

    public static FitRCFParams parse(XContentParser parser) throws IOException {
        Integer numberOfTrees = null;
        Integer shingleSize = null;
        Integer sampleSize = null;
        Integer outputAfter = null;
        Double timeDecay = null;
        Double anomalyRate = null;
        String timeField = null;
        String dateFormat = null;
        String timeZone = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NUMBER_OF_TREES:
                    numberOfTrees = parser.intValue(false);
                    break;
                case SHINGLE_SIZE:
                    shingleSize = parser.intValue(false);
                    break;
                case SAMPLE_SIZE:
                    sampleSize = parser.intValue(false);
                    break;
                case OUTPUT_AFTER:
                    outputAfter = parser.intValue(false);
                    break;
                case TIME_DECAY:
                    timeDecay = parser.doubleValue(false);
                    break;
                case ANOMALY_RATE:
                    anomalyRate = parser.doubleValue(false);
                    break;
                case TIME_FIELD:
                    timeField = parser.text();
                    break;
                case DATE_FORMAT:
                    dateFormat = parser.text();
                    break;
                case TIME_ZONE:
                    timeZone = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new FitRCFParams(
            numberOfTrees,
            shingleSize,
            sampleSize,
            outputAfter,
            timeDecay,
            anomalyRate,
            timeField,
            dateFormat,
            timeZone
        );
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (numberOfTrees != null) {
            builder.field(NUMBER_OF_TREES, numberOfTrees);
        }
        if (shingleSize != null) {
            builder.field(SHINGLE_SIZE, shingleSize);
        }
        if (sampleSize != null) {
            builder.field(SAMPLE_SIZE, sampleSize);
        }
        if (outputAfter != null) {
            builder.field(OUTPUT_AFTER, outputAfter);
        }
        if (timeDecay != null) {
            builder.field(TIME_DECAY, timeDecay);
        }
        if (anomalyRate != null) {
            builder.field(ANOMALY_RATE, anomalyRate);
        }
        if (timeField != null) {
            builder.field(TIME_FIELD, timeField);
        }
        if (dateFormat != null) {
            builder.field(DATE_FORMAT, dateFormat);
        }
        if (timeZone != null) {
            builder.field(TIME_ZONE, timeZone);
        }
        builder.endObject();
        return builder;
    }
}
