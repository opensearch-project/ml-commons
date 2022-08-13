/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.search;

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

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLKNNSearchInput implements ToXContentObject, Writeable {

    public static final String INDEX_FIELD = "index";
    public static final String VECTOR_FIELD = "vector_field";
    public static final String QUERY_FIELD = "query";
    public static final String K_FIELD = "k";
    public static final String SOURCE_FIELD = "source_fields";

    private String index;
    private String vectorField;
    private String query;
    private Integer k;
    private List<String> sourceFields;

    public MLKNNSearchInput(String name, String vectorField, String query, Integer k, List<String> sourceFields) {
        this.index = name;
        this.vectorField = vectorField;
        this.query = query;
        this.k = k;
        this.sourceFields = sourceFields;
    }


    public MLKNNSearchInput(StreamInput in) throws IOException {
        this.index = in.readString();
        this.vectorField = in.readString();
        this.query = in.readString();
        this.k = in.readInt();
        this.sourceFields = in.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        out.writeString(vectorField);
        out.writeString(query);
        out.writeInt(k);
        out.writeStringCollection(sourceFields);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(INDEX_FIELD, index);
        builder.field(VECTOR_FIELD, vectorField);
        builder.field(QUERY_FIELD, query);
        builder.field(K_FIELD, k);
        builder.field(SOURCE_FIELD, sourceFields);
        builder.endObject();
        return builder;
    }

    public static MLKNNSearchInput parse(XContentParser parser) throws IOException {
        String index = null;
        String vectorField = null;
        String query = null;
        Integer k = null;
        List<String> sourceFields = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_FIELD:
                    index = parser.text();
                    break;
                case VECTOR_FIELD:
                    vectorField = parser.text();
                    break;
                case QUERY_FIELD:
                    query = parser.text();
                    break;
                case K_FIELD:
                    k = parser.intValue();
                    break;
                case SOURCE_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        sourceFields.add(parser.text());
                    }
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLKNNSearchInput(index, vectorField, query, k, sourceFields);
    }


}
