/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.prompt;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Data;

@Data
public class PromptExtraConfig implements ToXContentObject, Writeable {

    public static final String LANGFUSE_PROMPT_TYPE_FIELD = "type";
    public static final String LANGFUSE_PROMPT_PUBLIC_KEY_FIELD = "public_key";
    public static final String LANGFUSE_PROMPT_ACCESS_KEY_FIELD = "access_key";
    public static final String LANGFUSE_PROMPT_LABELS_FIELD = "labels";

    private String type; // required
    private String publicKey; // required
    private String accessKey; // required
    private List<String> labels; // optional

    @Builder(toBuilder = true)
    public PromptExtraConfig(String type, String publicKey, String accessKey, List<String> labels) {
        this.type = type;
        this.publicKey = publicKey;
        this.accessKey = accessKey;
        this.labels = labels;
    }

    public PromptExtraConfig(StreamInput input) throws IOException {
        this.type = input.readOptionalString();
        this.publicKey = input.readOptionalString();
        this.accessKey = input.readOptionalString();
        this.labels = input.readList(StreamInput::readString);
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        output.writeOptionalString(type);
        output.writeOptionalString(publicKey);
        output.writeOptionalString(accessKey);
        output.writeCollection(labels, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(LANGFUSE_PROMPT_TYPE_FIELD, type);
        }
        if (publicKey != null) {
            builder.field(LANGFUSE_PROMPT_PUBLIC_KEY_FIELD, publicKey);
        }
        if (accessKey != null) {
            builder.field(LANGFUSE_PROMPT_ACCESS_KEY_FIELD, accessKey);
        }
        if (labels != null) {
            builder.field(LANGFUSE_PROMPT_LABELS_FIELD, labels);
        }
        return builder.endObject();
    }

    public static PromptExtraConfig parse(XContentParser parser) throws IOException {
        String type = null;
        String publicKey = null;
        String accessKey = null;
        List<String> labels = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case LANGFUSE_PROMPT_TYPE_FIELD:
                    type = parser.text();
                    break;
                case LANGFUSE_PROMPT_PUBLIC_KEY_FIELD:
                    publicKey = parser.text();
                    break;
                case LANGFUSE_PROMPT_ACCESS_KEY_FIELD:
                    accessKey = parser.text();
                    break;
                case LANGFUSE_PROMPT_LABELS_FIELD:
                    labels = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        labels.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return PromptExtraConfig.builder().type(type).publicKey(publicKey).accessKey(accessKey).labels(labels).build();
    }
}