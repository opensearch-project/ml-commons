/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.isValidActionInModelPrediction;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_BATCH_JOB_ARN;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.DEFAULT_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.DEFAULT_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.OPENAI_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_DEFAULT_INPUT;

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
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String COHERE_URL = "https://api.cohere.ai/v1/embed";
    private static final String BEDROCK_URL = "https://bedrock-runtime.us-east-1.amazonaws.com/model/amazon.titan-embed-text-v1/invoke";
    private static final String SAGEMAKER_URL =
        "https://runtime.sagemaker.us-west-2.amazonaws.com/endpoints/lmi-model-2023-06-24-01-35-32-275/invocations";

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
    public void connectorWithNullPreProcessFunction() {
        ConnectorAction action = new ConnectorAction(TEST_ACTION_TYPE, TEST_METHOD_HTTP, OPENAI_URL, null, TEST_REQUEST_BODY, null, null);
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
    }

    @Test
    public void connectorWithCustomPainlessScriptPreProcessFunction() {
        String preProcessFunction =
            "\"\\n    StringBuilder builder = new StringBuilder();\\n    builder.append(\\\"\\\\\\\"\\\");\\n    String first = params.text_docs[0];\\n    builder.append(first);\\n    builder.append(\\\"\\\\\\\"\\\");\\n    def parameters = \\\"{\\\" +\\\"\\\\\\\"text_inputs\\\\\\\":\\\" + builder + \\\"}\\\";\\n    return  \\\"{\\\" +\\\"\\\\\\\"parameters\\\\\\\":\\\" + parameters + \\\"}\\\";\"";
        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            OPENAI_URL,
            null,
            TEST_REQUEST_BODY,
            preProcessFunction,
            null
        );
        action.validatePrePostProcessFunctions(null);
        assertNotNull(action);
    }

    @Test
    public void openAIConnectorWithCorrectInBuiltPrePostProcessFunction() {
        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            "https://${parameters.endpoint}/v1/chat/completions",
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT,
            OPENAI_EMBEDDING
        );
        action.validatePrePostProcessFunctions(Map.of("endpoint", "api.openai.com"));
        assertNotNull(action);
    }

    @Test
    public void openAIConnectorWithWrongInBuiltPrePostProcessFunction() {
        ConnectorAction action1 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            OPENAI_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT,
            OPENAI_EMBEDDING
        );
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> action1.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is openai, so PreProcessFunction should be connector.pre_process.openai.embedding",
            exception.getMessage()
        );
        ConnectorAction action2 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            OPENAI_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT,
            COHERE_EMBEDDING
        );
        exception = assertThrows(IllegalArgumentException.class, () -> action2.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is openai, so PostProcessFunction should be connector.post_process.openai.embedding",
            exception.getMessage()
        );
    }

    @Test
    public void cohereConnectorWithCorrectInBuiltPrePostProcessFunction() {
        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            COHERE_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT,
            COHERE_EMBEDDING
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
        action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            COHERE_URL,
            null,
            TEST_REQUEST_BODY,
            IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT,
            COHERE_EMBEDDING
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
        action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            COHERE_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT,
            COHERE_RERANK
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
    }

    @Test
    public void cohereConnectorWithWrongInBuiltPrePostProcessFunction() {
        ConnectorAction action1 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            COHERE_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT,
            COHERE_EMBEDDING
        );
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> action1.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is cohere, so PreProcessFunction should be connector.pre_process.cohere.embedding"
                + " or connector.pre_process.cohere.multimodal_embedding or connector.pre_process.cohere.rerank",
            exception.getMessage()
        );
        ConnectorAction action2 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            COHERE_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT,
            OPENAI_EMBEDDING
        );
        exception = assertThrows(IllegalArgumentException.class, () -> action2.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is cohere, so PostProcessFunction should be connector.post_process.cohere.embedding"
                + " or connector.post_process.cohere.rerank",
            exception.getMessage()
        );
    }

    @Test
    public void bedrockConnectorWithCorrectInBuiltPrePostProcessFunction() {
        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            BEDROCK_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT,
            BEDROCK_EMBEDDING
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
        action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            BEDROCK_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT,
            BEDROCK_BATCH_JOB_ARN
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
        action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            BEDROCK_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT,
            BEDROCK_RERANK
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
    }

    @Test
    public void bedrockConnectorWithWrongInBuiltPrePostProcessFunction() {
        ConnectorAction action1 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            BEDROCK_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT,
            BEDROCK_EMBEDDING
        );
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> action1.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is bedrock, so PreProcessFunction should be connector.pre_process.bedrock.embedding"
                + " or connector.pre_process.bedrock.multimodal_embedding or connector.pre_process.bedrock.rerank",
            exception.getMessage()
        );
        ConnectorAction action2 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            BEDROCK_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT,
            COHERE_EMBEDDING
        );
        exception = assertThrows(IllegalArgumentException.class, () -> action2.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is bedrock, so PostProcessFunction should be connector.post_process.bedrock.embedding"
                + " or connector.post_process.bedrock.batch_job_arn or connector.post_process.bedrock.rerank",
            exception.getMessage()
        );
    }

    @Test
    public void sagemakerConnectorWithCorrectInBuiltPrePostProcessFunction() {
        ConnectorAction action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            SAGEMAKER_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT,
            DEFAULT_EMBEDDING
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
        action = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            SAGEMAKER_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_SIMILARITY_TO_DEFAULT_INPUT,
            DEFAULT_RERANK
        );
        action.validatePrePostProcessFunctions(Map.of());
        assertNotNull(action);
    }

    @Test
    public void sagemakerConnectorWithWrongInBuiltPrePostProcessFunction() {
        ConnectorAction action1 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            SAGEMAKER_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT,
            DEFAULT_EMBEDDING
        );
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> action1.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is sagemaker, so PreProcessFunction should be connector.pre_process.default.embedding"
                + " or connector.pre_process.default.rerank",
            exception.getMessage()
        );
        ConnectorAction action2 = new ConnectorAction(
            TEST_ACTION_TYPE,
            TEST_METHOD_HTTP,
            SAGEMAKER_URL,
            null,
            TEST_REQUEST_BODY,
            TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT,
            BEDROCK_EMBEDDING
        );
        exception = assertThrows(IllegalArgumentException.class, () -> action2.validatePrePostProcessFunctions(Map.of()));
        assertEquals(
            "LLM service is sagemaker, so PostProcessFunction should be connector.post_process.default.embedding"
                + " or connector.post_process.default.rerank",
            exception.getMessage()
        );
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
