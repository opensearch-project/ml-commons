/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.unload;

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
    private static final String NAME_FIELD = "model_names";
    private static final String VERSION_FIELD = "versions";
    private String[] modelNames;
    private int[] versions;

    public UnloadModelInput(StreamInput in) throws IOException {
        this.modelNames = in.readStringArray();
        this.versions = in.readIntArray();
    }

    @Builder
    public UnloadModelInput(String[] modelNames, int[] versions) {
        this.modelNames = modelNames;
        this.versions = versions;
    }

    public UnloadModelInput() {

    }

    public static UnloadModelInput parse(XContentParser parser) throws IOException {
        List<String> modelNames = new ArrayList<>();
        List<Integer> versions = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelNames.add(parser.text());
                    }
                    break;
                case VERSION_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        versions.add(parser.intValue());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        int[] modelVersions = versions.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        return new UnloadModelInput(modelNames.toArray(new String[0]), modelVersions);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeStringArray(modelNames);
        out.writeIntArray(versions);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("model_names", modelNames);
        builder.field("versions", versions);
        builder.endObject();
        return builder;
    }
}
