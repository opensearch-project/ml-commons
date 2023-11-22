/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
@Builder
@RequiredArgsConstructor
@ToString
public class ColumnMeta implements Writeable, ToXContentObject {
    private static final String NAME_FIELD = "name";
    private static final String COLUMN_TYPE_FIELD = "column_type";
    String name;
    ColumnType columnType;

    ColumnMeta(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        this.columnType = in.readEnum(ColumnType.class);
    }

    public static ColumnMeta parse(XContentParser parser) throws IOException {
        String name = null;
        ColumnType columnType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case COLUMN_TYPE_FIELD:
                    columnType = ColumnType.valueOf(parser.text().toUpperCase(Locale.ROOT));
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new ColumnMeta(name, columnType);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeEnum(columnType);
    }

    public void toXContent(final XContentBuilder builder) throws IOException {
        toXContent(builder, EMPTY_PARAMS);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(COLUMN_TYPE_FIELD, columnType);
        builder.endObject();
        return builder;
    }
}
