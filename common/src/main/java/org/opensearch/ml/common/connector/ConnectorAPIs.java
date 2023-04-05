/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Getter;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
public class ConnectorAPIs implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "Connector_APIs";

    public static final String PREDICT_FIELD = "Predict";
    public static final String META_DARA_FIELD = "Metadata";

    private APISchema predictSchema;
    private APISchema metadataSchema;

    public ConnectorAPIs(APISchema predictSchema, APISchema metadataSchema) {
        this.predictSchema = predictSchema;
        this.metadataSchema = metadataSchema;
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public static ConnectorAPIs parse(XContentParser parser) throws IOException {
        APISchema predictSchema = null;
        APISchema metadataSchema = null;

        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case PREDICT_FIELD:
                        predictSchema = APISchema.parse(parser);
                        break;
                    case META_DARA_FIELD:
                        metadataSchema = APISchema.parse(parser);
                    // Todo: add more APIs here
                    default:
                        parser.skipChildren();
                        break;
                }
            }
        }
        return new ConnectorAPIs(predictSchema, metadataSchema);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (predictSchema != null) {
            builder.field(PREDICT_FIELD, predictSchema);
        }
        if (metadataSchema != null) {
            builder.field(META_DARA_FIELD, metadataSchema);
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
        if (metadataSchema != null) {
            output.writeBoolean(true);
            metadataSchema.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
    }

    public ConnectorAPIs(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            this.predictSchema = new APISchema(input);
        }
        if (input.readBoolean()) {
            this.metadataSchema = new APISchema(input);
        }
    }
}
