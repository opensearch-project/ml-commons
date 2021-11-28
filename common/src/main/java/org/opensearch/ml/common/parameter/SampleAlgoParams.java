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

import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class SampleAlgoParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.SAMPLE_ALGO.getName();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String SAMPLE_PARAM_FIELD = "sample_param";
    private Integer sampleParam;

    public SampleAlgoParams(Integer sampleParam) {
        this.sampleParam = sampleParam;
    }

    public SampleAlgoParams(StreamInput in) throws IOException {
        this.sampleParam = in.readOptionalInt();
    }

    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        Integer sampleParam = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case SAMPLE_PARAM_FIELD:
                    sampleParam = parser.intValue();
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
        builder.field(SAMPLE_PARAM_FIELD, sampleParam);
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
