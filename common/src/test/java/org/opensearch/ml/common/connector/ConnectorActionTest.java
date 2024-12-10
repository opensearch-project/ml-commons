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

    @Test
    public void constructor_NullActionType() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ConnectorAction.ActionType actionType = null;
            String method = "post";
            String url = "https://test.com";
            String requestBody = "{\"input\": \"${parameters.input}\"}";
            new ConnectorAction(actionType, method, url, null, requestBody, null, null);
        });
        assertEquals("action type can't null", exception.getMessage());

    }

    @Test
    public void constructor_NullUrl() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
            String method = "post";
            String url = null;
            String requestBody = "{\"input\": \"${parameters.input}\"}";
            new ConnectorAction(actionType, method, url, null, requestBody, null, null);
        });
        assertEquals("url can't null", exception.getMessage());
    }

    @Test
    public void constructor_NullMethod() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
            String method = null;
            String url = "https://test.com";
            String requestBody = "{\"input\": \"${parameters.input}\"}";
            new ConnectorAction(actionType, method, url, null, requestBody, null, null);
        });
        assertEquals("method can't null", exception.getMessage());
    }

    @Test
    public void constructor_NullRequestBody() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
            String method = "post";
            String url = "https://test.com";
            String requestBody = null;
            new ConnectorAction(actionType, method, url, null, requestBody, null, null);
        });
        assertEquals("request body can't null", exception.getMessage());
    }

    @Test
    public void writeTo_NullValue() throws IOException {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        ConnectorAction action = new ConnectorAction(actionType, method, url, null, requestBody, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        action.writeTo(output);
        ConnectorAction action2 = new ConnectorAction(output.bytes().streamInput());
        assertEquals(action, action2);
    }

    @Test
    public void writeTo() throws IOException {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            requestBody,
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
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        ConnectorAction action = new ConnectorAction(actionType, method, url, null, requestBody, null, null);

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        action.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        String expctedContent = """
            {"action_type":"PREDICT","method":"http","url":"https://test.com",\
            "request_body":"{\\"input\\": \\"${parameters.input}\\"}"}\
            """;
        assertEquals(expctedContent, content);
    }

    @Test
    public void toXContent() throws IOException {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value1");
        String requestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;

        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            requestBody,
            preProcessFunction,
            postProcessFunction
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        action.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        String expctedContent = """
            {"action_type":"PREDICT","method":"http","url":"https://test.com","headers":{"key1":"value1"},\
            "request_body":"{\\"input\\": \\"${parameters.input}\\"}",\
            "pre_process_function":"connector.pre_process.openai.embedding",\
            "post_process_function":"connector.post_process.openai.embedding"}\
            """;
        assertEquals(expctedContent, content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = """
            {"action_type":"PREDICT","method":"http","url":"https://test.com","headers":{"key1":"value1"},\
            "request_body":"{\\"input\\": \\"${parameters.input}\\"}",\
            "pre_process_function":"connector.pre_process.openai.embedding",\
            "post_process_function":"connector.post_process.openai.embedding"}"\
            """;
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        ConnectorAction action = ConnectorAction.parse(parser);
        assertEquals("http", action.getMethod());
        assertEquals(ConnectorAction.ActionType.PREDICT, action.getActionType());
        assertEquals("https://test.com", action.getUrl());
        assertEquals("{\"input\": \"${parameters.input}\"}", action.getRequestBody());
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
