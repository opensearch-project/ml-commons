/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.annotation.MLAlgoParameter;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
@MLAlgoParameter(algorithms={FunctionName.ANOMALY_DETECTION})
public class AnomalyDetectionParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.ANOMALY_DETECTION.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String GAMMA_FIELD = "gamma";
    public static final String NU_FIELD = "nu";
    private Double gamma;
    private Double nu;

    @Builder
    public AnomalyDetectionParams(Double gamma, Double nu) {
        this.gamma = gamma;
        this.nu = nu;
    }

    public AnomalyDetectionParams(StreamInput in) throws IOException {
        this.gamma = in.readOptionalDouble();
        this.nu = in.readOptionalDouble();
    }

    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        Double gamma = null;
        Double nu = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case GAMMA_FIELD:
                    gamma = parser.doubleValue();
                    break;
                case NU_FIELD:
                    nu = parser.doubleValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AnomalyDetectionParams(gamma, nu);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalDouble(gamma);
        out.writeOptionalDouble(nu);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(GAMMA_FIELD, gamma);
        builder.field(NU_FIELD, nu);
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
