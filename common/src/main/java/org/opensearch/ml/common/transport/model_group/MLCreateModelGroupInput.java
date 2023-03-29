/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLCreateModelGroupInput implements ToXContentObject, Writeable{

    public static final String NAME_FIELD = "name"; //mandatory
    public static final String DESCRIPTION_FIELD = "description";
    public static final String TAGS_FIELD = "tags"; //mandatory
    public static final String MODEL_IDS_FIELD = "model_ids";

    private String name;
    private String description;
    private List<String> tags;
    private List<String> models;

    @Builder(toBuilder = true)
    public MLCreateModelGroupInput(String name, String description, List<String> tags, List<String> models) {
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.models = models;
    }

    public MLCreateModelGroupInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            tags = in.readStringList();
        }
        if (in.readBoolean()) {
            models = in.readStringList();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        if (tags != null) {
            out.writeBoolean(true);
            out.writeStringCollection(tags);
        } else {
            out.writeBoolean(false);
        }
        if (models != null) {
            out.writeBoolean(true);
            out.writeStringCollection(models);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (tags != null && tags.size() > 0) {
            builder.field(TAGS_FIELD, tags);
        }
        if (models != null && models.size() > 0) {
            builder.field(MODEL_IDS_FIELD, models);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateModelGroupInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        List<String> tags = null;
        List<String> models = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case TAGS_FIELD:
                    tags = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        tags.add(parser.text());
                    }
                    break;
                case MODEL_IDS_FIELD:
                    models = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        models.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreateModelGroupInput(name, description, tags, models);
    }

}
