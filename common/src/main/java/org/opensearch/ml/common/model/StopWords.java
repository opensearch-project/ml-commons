/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@EqualsAndHashCode
@Getter
public class StopWords implements ToXContentObject {
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String SOURCE_FIELDS_FIELD = "source_fields";

    private String index;
    private String[] sourceFields;

    @Builder(toBuilder = true)
    public StopWords(String index, String[] sourceFields) {
        this.index = index;
        this.sourceFields = sourceFields;
    }

    public StopWords(@NonNull Map<String, Object> params) {
        List<String> fields = (List<String>) params.get(SOURCE_FIELDS_FIELD);
        this.index = (String) params.get(INDEX_NAME_FIELD);
        this.sourceFields = fields == null ? null : fields.toArray(new String[0]);
    }

    public StopWords(StreamInput input) throws IOException {
        index = input.readString();
        sourceFields = input.readStringArray();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        out.writeStringArray(sourceFields);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (index != null) {
            builder.field(INDEX_NAME_FIELD, index);
        }
        if (sourceFields != null) {
            builder.field(SOURCE_FIELDS_FIELD, sourceFields);
        }
        builder.endObject();
        return builder;
    }

    public static StopWords parse(XContentParser parser) throws IOException {
        String index = null;
        String[] sourceFields = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    index = parser.text();
                    break;
                case SOURCE_FIELDS_FIELD:
                    sourceFields = parser.list().toArray(new String[0]);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return StopWords.builder().index(index).sourceFields(sourceFields).build();
    }
}
