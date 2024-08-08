/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.clustering;

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
@MLAlgoParameter(algorithms = { FunctionName.RCF_SUMMARIZE })
public class RCFSummarizeParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.RCF_SUMMARIZE.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String MAX_K_FIELD = "max_k";
    public static final String INITIAL_K_FIELD = "initial_k";
    public static final String DISTANCE_TYPE_FIELD = "distance_type";
    public static final String PHASE1_REASSIGN_FIELD = "phase1_reassign";
    public static final String PARALLEL__FIELD = "parallel";

    // The max of K allowed
    private Integer maxK;
    // The initial K used
    private Integer initialK;
    // The distance function
    private DistanceType distanceType;
    // Whether to also use Reassign in Phase1
    private Boolean phase1Reassign;
    // Whether to train in parallel
    private Boolean parallel;
    // TODO: expose seed?

    @Builder(toBuilder = true)
    public RCFSummarizeParams(Integer maxK, Integer initialK, DistanceType distanceType, Boolean phase1Reassign, Boolean parallel) {

        this.maxK = maxK;
        this.initialK = initialK;
        this.distanceType = distanceType;
        this.phase1Reassign = phase1Reassign;
        this.parallel = parallel;
    }

    public RCFSummarizeParams(StreamInput in) throws IOException {
        this.maxK = in.readOptionalInt();
        this.initialK = in.readOptionalInt();
        this.phase1Reassign = in.readOptionalBoolean();
        this.parallel = in.readOptionalBoolean();

        if (in.readBoolean()) {
            this.distanceType = in.readEnum(DistanceType.class);
        }
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        Integer maxK = null;
        Integer initialK = null;
        Boolean phase1Reassign = null;
        Boolean parallel = null;
        DistanceType distanceType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MAX_K_FIELD:
                    maxK = parser.intValue(false);
                    break;
                case INITIAL_K_FIELD:
                    initialK = parser.intValue(false);
                    break;
                case PHASE1_REASSIGN_FIELD:
                    phase1Reassign = parser.booleanValue();
                    break;
                case PARALLEL__FIELD:
                    parallel = parser.booleanValue();
                    break;
                case DISTANCE_TYPE_FIELD:
                    distanceType = DistanceType.from(parser.text().toUpperCase());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new RCFSummarizeParams(maxK, initialK, distanceType, phase1Reassign, parallel);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(maxK);
        out.writeOptionalInt(initialK);
        out.writeOptionalBoolean(phase1Reassign);
        out.writeOptionalBoolean(parallel);
        if (distanceType != null) {
            out.writeBoolean(true);
            out.writeEnum(distanceType);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (maxK != null) {
            builder.field(MAX_K_FIELD, maxK);
        }

        if (initialK != null) {
            builder.field(INITIAL_K_FIELD, initialK);
        }

        if (phase1Reassign != null) {
            builder.field(PHASE1_REASSIGN_FIELD, phase1Reassign);
        }

        if (parallel != null) {
            builder.field(PARALLEL__FIELD, parallel);
        }

        if (distanceType != null) {
            builder.field(DISTANCE_TYPE_FIELD, distanceType.name());
        }
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public enum DistanceType {
        L1,
        L2,
        LInfinity;

        public static DistanceType from(String value) {
            try {
                return DistanceType.valueOf(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong distance type");
            }
        }
    }
}
