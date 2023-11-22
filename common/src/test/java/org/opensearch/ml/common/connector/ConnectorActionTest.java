/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NullActionType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("action type can't null");
        ConnectorAction.ActionType actionType = null;
        String method = "post";
        String url = "https://test.com";
        new ConnectorAction(actionType, method, url, null, null, null, null);
    }

    @Test
    public void constructor_NullUrl() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("url can't null");
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "post";
        String url = null;
        new ConnectorAction(actionType, method, url, null, null, null, null);
    }

    @Test
    public void constructor_NullMethod() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("method can't null");
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = null;
        String url = "https://test.com";
        new ConnectorAction(actionType, method, url, null, null, null, null);
    }

    @Test
    public void writeTo_NullValue() throws IOException {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        ConnectorAction action = new ConnectorAction(actionType, method, url, null, null, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        action.writeTo(output);
        ConnectorAction action2 = new ConnectorAction(output.bytes().streamInput());
        Assert.assertEquals(action, action2);
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
        Assert.assertEquals(action, action2);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "http";
        String url = "https://test.com";
        ConnectorAction action = new ConnectorAction(actionType, method, url, null, null, null, null);

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        action.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        Assert.assertEquals("{\"action_type\":\"PREDICT\",\"method\":\"http\",\"url\":\"https://test.com\"}", content);
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
        Assert
            .assertEquals(
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
        Assert.assertEquals("http", action.getMethod());
        Assert.assertEquals(ConnectorAction.ActionType.PREDICT, action.getActionType());
        Assert.assertEquals("https://test.com", action.getUrl());
        Assert.assertEquals("{\"input\": \"${parameters.input}\"}", action.getRequestBody());
        Assert.assertEquals("connector.pre_process.openai.embedding", action.getPreProcessFunction());
        Assert.assertEquals("connector.post_process.openai.embedding", action.getPostProcessFunction());
    }
}
