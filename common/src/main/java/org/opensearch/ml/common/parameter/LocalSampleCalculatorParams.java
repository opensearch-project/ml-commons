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
import lombok.NonNull;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class LocalSampleCalculatorParams implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = "local_sample_calculator";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String OPERATION_FIELD = "operation";
    private String operation;

    @Builder
    public LocalSampleCalculatorParams(@NonNull String operation) {
        this.operation = operation;
    }

    public LocalSampleCalculatorParams(StreamInput in) throws IOException {
        this.operation = in.readString();
    }

    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        String operation = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "operation":
                    operation = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new LocalSampleCalculatorParams(operation);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(operation);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(OPERATION_FIELD, operation);
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
