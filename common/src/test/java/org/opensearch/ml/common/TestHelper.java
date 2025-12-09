/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.opensearch.common.TriConsumer;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;

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

    public static List<Map<String, Object>> createTestContent(String input) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("type", "text");
        message.put("text", input);
        content.add(message);
        return content;
    }

    public static void endecryptCredentials(
        Connector connector,
        TriConsumer<List<String>, String, ActionListener<List<String>>> function,
        boolean encrypt
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { latch.countDown(); }, e -> { latch.countDown(); });
        if (encrypt) {
            connector.encrypt(function, null, listener);
        } else {
            if (connector instanceof McpConnector || connector instanceof McpStreamableHttpConnector) {
                connector.decrypt(null, function, null, listener);
            } else {
                connector
                    .decrypt(
                        Optional
                            .ofNullable(connector.getActions())
                            .map(List::getFirst)
                            .map(ConnectorAction::getActionType)
                            .map(ConnectorAction.ActionType::name)
                            .orElse(null),
                        function,
                        null,
                        listener
                    );
            }
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            fail("Failed to encrypt credentials in connector, " + e.getMessage());
        }
    }
}
