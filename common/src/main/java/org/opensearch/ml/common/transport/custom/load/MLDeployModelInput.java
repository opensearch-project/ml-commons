/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.load;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import java.io.IOException;
import java.util.Objects;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLDeployModelInput implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";

    private String name;
    private Integer version;

    @Builder(toBuilder = true)
    public MLDeployModelInput(String name, Integer version) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        this.name = name;
        this.version = version;
    }


    public MLDeployModelInput(StreamInput in) throws IOException {
        this.name = in.readString();
        this.version = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeInt(version);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(VERSION_FIELD, version);
        builder.endObject();
        return builder;
    }

    public static MLDeployModelInput parse(XContentParser parser) throws IOException {
        String name = null;
        Integer version = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLDeployModelInput(name, version);
    }


    public FunctionName getFunctionName() {
        return FunctionName.CUSTOM;
    }

}
