/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.upload;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLUploadInput implements ToXContentObject, Writeable {

    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String URL_FIELD = "url";

    private String name;
    private Integer version;
    private String url;

    @Builder(toBuilder = true)
    public MLUploadInput(String name, Integer version, String url) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        Objects.requireNonNull(url);
        if (name.contains(":")) {
            throw new IllegalArgumentException("Model name can't contain \":\"");
        }
        this.name = name;
        this.version = version;
        this.url = url;
    }


    public MLUploadInput(StreamInput in) throws IOException {
        this.name = in.readString();
        this.version = in.readInt();
        this.url = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeInt(version);
        out.writeString(url);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(VERSION_FIELD, version);
        builder.field(URL_FIELD, url);
        builder.endObject();
        return builder;
    }

    public static MLUploadInput parse(XContentParser parser) throws IOException {
        String name = null;
        Integer version = null;
        String url = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.intValue();
                    break;
                case URL_FIELD:
                    url = parser.text();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLUploadInput(name, version, url);
    }


}
