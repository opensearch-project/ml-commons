/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.isValidActionInModelPrediction;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class ConnectorActionTest {

    // Shared test data for the class
    private static final ConnectorAction.ActionType TEST_ACTION_TYPE = ConnectorAction.ActionType.PREDICT;
    private static final String TEST_METHOD_POST = "post";
    private static final String TEST_METHOD_HTTP = "http";
    private static final String TEST_REQUEST_BODY = "{\"input\": \"${parameters.input}\"}";
    private static final String URL = "https://test.com";

    @Test
    public void constructor_NullActionType() {
        Throwable exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ConnectorAction(null, TEST_METHOD_POST, URL, null, TEST_REQUEST_BODY, null, null)
        );
        assertEquals("action type can't be null", exception.getMessage());

    }

    @Test
    public void constructor_NullUrl() {
        Throwable exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ConnectorAction(TEST_ACTION_TYPE, TEST_METHOD_POST, null, null, TEST_REQUEST_BODY, null, null)
        );
        assertEquals("url can't be null", exception.getMessage());
    }

    @Test
    public void constructor_NullMethod() {
        Throwable exception = assertThrows(
            IllegalArgumentException.class,
            () -> new ConnectorAction(TEST_ACTION_TYPE, null, URL, null, TEST_REQUEST_BODY, null, null)
        );
        assertEquals("method can't be null", exception.getMessage());
    }

    @Test
    public void writeTo_NullValue() throws IOException {
        ConnectorAction action = new ConnectorAction(TEST_ACTION_TYPE, TEST_METHOD_HTTP, URL, null, TEST_REQUEST_BODY, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        action.writeTo(output);
        ConnectorAction action2 = new ConnectorAction(output.bytes().streamInput());
        assertEquals(action, action2);
    }

    @Test
    public void writeTo() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            URL,
            headers,
            TEST_REQUEST_BODY,
            preProcessFunction,
            postProcessFunction
        );
        BytesStreamOutput output = new BytesStreamOutput();
        action.writeTo(output);
        ConnectorAction action2 = new ConnectorAction(output.bytes().streamInput());
        assertEquals(action, action2);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        ConnectorAction action = new ConnectorAction(TEST_ACTION_TYPE, TEST_METHOD_HTTP, URL, null, TEST_REQUEST_BODY, null, null);

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        action.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"action_type\":\"PREDICT\",\"method\":\"http\",\"url\":\"https://test.com\","
                + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"}",
            content
        );
    }

    @Test
    public void toXContent() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            URL,
            headers,
            TEST_REQUEST_BODY,
            preProcessFunction,
            postProcessFunction
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        action.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"action_type\":\"PREDICT\",\"method\":\"http\",\"url\":\"https://test.com\","
                + "\"headers\":{\"key1\":\"value1\"},\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
                + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
                + "\"post_process_function\":\"connector.post_process.openai.embedding\"}",
            content
        );
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"action_type\":\"PREDICT\",\"method\":\"http\",\"url\":\"https://test.com\","
            + "\"headers\":{\"key1\":\"value1\"},\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
            + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
            + "\"post_process_function\":\"connector.post_process.openai.embedding\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        ConnectorAction action = ConnectorAction.parse(parser);
        assertEquals(TEST_METHOD_HTTP, action.getMethod());
        assertEquals(ConnectorAction.ActionType.PREDICT, action.getActionType());
        assertEquals(URL, action.getUrl());
        assertEquals(TEST_REQUEST_BODY, action.getRequestBody());
        assertEquals("connector.pre_process.openai.embedding", action.getPreProcessFunction());
        assertEquals("connector.post_process.openai.embedding", action.getPostProcessFunction());
    }

    @Test
    public void test_wrongActionType() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> { ConnectorAction.ActionType.from("badAction"); });
        assertEquals("Wrong Action Type of badAction", exception.getMessage());
    }

    @Test
    public void test_invalidActionInModelPrediction() {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.from("execute");
        assertEquals(isValidActionInModelPrediction(actionType), false);
    }
}
