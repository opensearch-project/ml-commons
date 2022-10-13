/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.unload;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class UnloadModelInput implements ToXContentObject, Writeable {
    private static final String MODEL_IDS_FIELD = "model_ids";
    private static final String NODE_IDS_FIELD = "node_ids";

    private String[] modelIds;
    private String[] nodeIds;

    public UnloadModelInput(StreamInput in) throws IOException {
        this.modelIds = in.readOptionalStringArray();
        this.nodeIds = in.readOptionalStringArray();
    }

    @Builder
    public UnloadModelInput(String[] modelIds, String[] nodeIds) {
        this.modelIds = modelIds;
        this.nodeIds = nodeIds;
    }

    public UnloadModelInput() {

    }

    public static UnloadModelInput parse(XContentParser parser) throws IOException {
        List<String> modelIds = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelIds.add(parser.text());
                    }
                    break;
                case NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        nodeIds.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new UnloadModelInput(modelIds.toArray(new String[0]), nodeIds.toArray(new String[0]));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalStringArray(modelIds);
        out.writeOptionalStringArray(nodeIds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_IDS_FIELD, modelIds);
        builder.field(NODE_IDS_FIELD, nodeIds);
        builder.endObject();
        return builder;
    }
}
