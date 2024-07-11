/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk.client;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class TestDataObject implements ToXContentObject {
    private static final String DATA_FIELD = "data";

    private final String data;

    public TestDataObject(String data) {
        this.data = data;
    }

    public String data() {
        return this.data;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(DATA_FIELD, this.data);
        return xContentBuilder.endObject();
    }

    public static TestDataObject parse(XContentParser parser) throws IOException {
        String data = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if (DATA_FIELD.equals(fieldName)) {
                data = parser.text();
            }
        }
        return new TestDataObject(data);
    }

    public String toJson() throws IOException {
        return this.toXContent(JsonXContent.contentBuilder(), EMPTY_PARAMS).toString();
    }
}
