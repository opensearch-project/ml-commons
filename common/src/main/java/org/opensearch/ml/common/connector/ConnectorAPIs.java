/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

public class ConnectorAPIs implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "Open_AI_APIs";

    public static final String PREDICT_FIELD = "Predict";
    public static final String META_DARA_FIELD = "Metadata";

    private PredictSchema predictSchema;

    public ConnectorAPIs(PredictSchema predictSchema) {
        this.predictSchema = predictSchema;

    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public static ConnectorAPIs parse(XContentParser parser) throws IOException {
        PredictSchema predictSchema = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PREDICT_FIELD:
                    predictSchema = PredictSchema.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new ConnectorAPIs(predictSchema);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (predictSchema != null) {
            builder.field(PREDICT_FIELD, predictSchema);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        if (predictSchema != null) {
            output.writeBoolean(true);
            predictSchema.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
    }

    public ConnectorAPIs(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            this.predictSchema = new PredictSchema(input);
        }
    }
}
