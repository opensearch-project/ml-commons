/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Data;

@Data
public class MLUndeployModelInput implements ToXContentObject, Writeable {
    private static final String MODEL_IDS_FIELD = "model_ids";
    private static final String NODE_IDS_FIELD = "node_ids";

    private String[] modelIds;
    private String[] nodeIds;

    public MLUndeployModelInput(StreamInput in) throws IOException {
        this.modelIds = in.readOptionalStringArray();
        this.nodeIds = in.readOptionalStringArray();
    }

    @Builder
    public MLUndeployModelInput(String[] modelIds, String[] nodeIds) {
        this.modelIds = modelIds;
        this.nodeIds = nodeIds;
    }

    public MLUndeployModelInput() {

    }

    public static MLUndeployModelInput parse(XContentParser parser) throws IOException {
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
        return new MLUndeployModelInput(modelIds.toArray(new String[0]), nodeIds.toArray(new String[0]));
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalStringArray(modelIds);
        out.writeOptionalStringArray(nodeIds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_IDS_FIELD, modelIds);
        builder.field(NODE_IDS_FIELD, nodeIds);
        builder.endObject();
        return builder;
    }
}
