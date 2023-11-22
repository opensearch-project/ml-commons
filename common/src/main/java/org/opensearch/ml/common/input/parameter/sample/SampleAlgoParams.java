/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.sample;

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
@MLAlgoParameter(algorithms = { FunctionName.SAMPLE_ALGO })
public class SampleAlgoParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.SAMPLE_ALGO.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String SAMPLE_PARAM_FIELD = "sample_param";
    private Integer sampleParam;

    @Builder
    public SampleAlgoParams(Integer sampleParam) {
        this.sampleParam = sampleParam;
    }

    public SampleAlgoParams(StreamInput in) throws IOException {
        this.sampleParam = in.readOptionalInt();
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        Integer sampleParam = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case SAMPLE_PARAM_FIELD:
                    sampleParam = parser.intValue(false);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new SampleAlgoParams(sampleParam);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(sampleParam);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (sampleParam != null) {
            builder.field(SAMPLE_PARAM_FIELD, sampleParam);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
