/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.io.IOException;
import java.util.function.Function;

import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class TestHelper {

    public static <T> void testParse(ToXContentObject obj, Function<XContentParser, T> function) throws IOException {
        testParse(obj, function, false);
    }

    public static <T> void testParse(ToXContentObject obj, Function<XContentParser, T> function, boolean wrapWithObject)
        throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        if (wrapWithObject) {
            builder.startObject();
        }
        obj.toXContent(builder, ToXContent.EMPTY_PARAMS);
        if (wrapWithObject) {
            builder.endObject();
        }
        String jsonStr = builder.toString();
        testParseFromString(obj, jsonStr, function);
    }

    public static <T> void testParseFromString(ToXContentObject obj, String jsonStr, Function<XContentParser, T> function)
        throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        T parsedObj = function.apply(parser);
        obj.equals(parsedObj);
    }

    public static String contentObjectToString(ToXContentObject obj) throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        obj.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return xContentBuilderToString(builder);
    }

    public static String xContentBuilderToString(XContentBuilder builder) {
        return BytesReference.bytes(builder).utf8ToString();
    }
}
