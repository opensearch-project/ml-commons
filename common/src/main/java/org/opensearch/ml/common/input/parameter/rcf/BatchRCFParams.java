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
@MLAlgoParameter(algorithms = { FunctionName.BATCH_RCF })
public class BatchRCFParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.BATCH_RCF.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String NUMBER_OF_TREES = "number_of_trees";
    public static final String SHINGLE_SIZE = "shingle_size";
    public static final String SAMPLE_SIZE = "sample_size";
    public static final String OUTPUT_AFTER = "output_after";
    public static final String TRAINING_DATA_SIZE = "training_data_size";
    public static final String ANOMALY_SCORE_THRESHOLD = "anomaly_score_threshold";
    private Integer numberOfTrees;
    private Integer shingleSize;
    private Integer sampleSize;
    private Integer outputAfter;
    private Integer trainingDataSize;
    private Double anomalyScoreThreshold;

    @Builder
    public BatchRCFParams(
        Integer numberOfTrees,
        Integer shingleSize,
        Integer sampleSize,
        Integer outputAfter,
        Integer trainingDataSize,
        Double anomalyScoreThreshold
    ) {
        this.numberOfTrees = numberOfTrees;
        this.shingleSize = shingleSize;
        this.sampleSize = sampleSize;
        this.outputAfter = outputAfter;
        this.trainingDataSize = trainingDataSize;
        this.anomalyScoreThreshold = anomalyScoreThreshold;
    }

    public BatchRCFParams(StreamInput in) throws IOException {
        this.numberOfTrees = in.readOptionalInt();
        this.shingleSize = in.readOptionalInt();
        this.sampleSize = in.readOptionalInt();
        this.outputAfter = in.readOptionalInt();
        this.trainingDataSize = in.readOptionalInt();
        this.anomalyScoreThreshold = in.readOptionalDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(numberOfTrees);
        out.writeOptionalInt(shingleSize);
        out.writeOptionalInt(sampleSize);
        out.writeOptionalInt(outputAfter);
        out.writeOptionalInt(trainingDataSize);
        out.writeOptionalDouble(anomalyScoreThreshold);
    }

    public static BatchRCFParams parse(XContentParser parser) throws IOException {
        Integer numberOfTrees = null;
        Integer shingleSize = null;
        Integer sampleSize = null;
        Integer outputAfter = null;
        Integer trainingDataSize = null;
        Double anomalyScoreThreshold = null;

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
                case TRAINING_DATA_SIZE:
                    trainingDataSize = parser.intValue(false);
                    break;
                case ANOMALY_SCORE_THRESHOLD:
                    anomalyScoreThreshold = parser.doubleValue(false);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BatchRCFParams(numberOfTrees, shingleSize, sampleSize, outputAfter, trainingDataSize, anomalyScoreThreshold);
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
        if (trainingDataSize != null) {
            builder.field(TRAINING_DATA_SIZE, trainingDataSize);
        }
        if (anomalyScoreThreshold != null) {
            builder.field(ANOMALY_SCORE_THRESHOLD, anomalyScoreThreshold);
        }
        builder.endObject();
        return builder;
    }
}
