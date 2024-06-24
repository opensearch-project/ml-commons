/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.ml.sdkclient;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
@Getter
public class ComplexDataObject implements ToXContentObject {
    private String testString;
    private long testNumber;
    private boolean testBool;
    private List<String> testList;
    private TestDataObject testObject;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field("testString", this.testString);
        xContentBuilder.field("testNumber", this.testNumber);
        xContentBuilder.field("testBool", this.testBool);
        xContentBuilder.field("testList", this.testList);
        xContentBuilder.field("testObject", this.testObject);
        return xContentBuilder.endObject();
    }

    public static ComplexDataObject parse(XContentParser parser) throws IOException {
        ComplexDataObjectBuilder builder = ComplexDataObject.builder();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("testString".equals(fieldName)) {
                builder.testString(parser.text());
            } else if ("testNumber".equals(fieldName)) {
                builder.testNumber(parser.longValue());
            } else if ("testBool".equals(fieldName)) {
                builder.testBool(parser.booleanValue());
            } else if ("testList".equals(fieldName)) {
                ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                List<String> list = new ArrayList<>();
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    list.add(parser.text());
                }
                builder.testList(list);
            } else if ("testObject".equals(fieldName)) {
                builder.testObject(TestDataObject.parse(parser));
            }
        }
        return builder.build();
    }
}
