/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;

public class HttpJsonConnectorExecutorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    ThreadPool threadPool;

    @Mock
    ScriptService scriptService;

    Settings settings;

    ThreadContext threadContext;

    @Mock
    private ActionListener<MLTaskResponse> actionListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void invokeRemoteModel_WrongHttpMethod() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported http method");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("wrong_method")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null, null, null);
    }

    @Test
    public void executePredict_RemoteInferenceInput() throws IOException {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(httpClient.execute(any())).thenReturn(response);
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInput_NoPreprocessFunction() throws IOException {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInput_LimitExceed() throws IOException {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule.expectMessage("{\"message\": \"Too many requests\"}");
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("{\"message\": \"Too many requests\"}");
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 429, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInput() throws IOException {
        String preprocessResult1 = "{\"parameters\": { \"input\": \"test doc1\" } }";
        String preprocessResult2 = "{\"parameters\": { \"input\": \"test doc2\" } }";
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult1))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult2));

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        executor.setScriptService(scriptService);
        when(httpClient.execute(any())).thenReturn(response);
        String modelResponse = "{\n"
            + "    \"object\": \"list\",\n"
            + "    \"data\": [\n"
            + "        {\n"
            + "            \"object\": \"embedding\",\n"
            + "            \"index\": 0,\n"
            + "            \"embedding\": [\n"
            + "                -0.014555434,\n"
            + "                -0.002135904,\n"
            + "                0.0035105038\n"
            + "            ]\n"
            + "        },\n"
            + "        {\n"
            + "            \"object\": \"embedding\",\n"
            + "            \"index\": 1,\n"
            + "            \"embedding\": [\n"
            + "                -0.014555434,\n"
            + "                -0.002135904,\n"
            + "                0.0035105038\n"
            + "            ]\n"
            + "        }\n"
            + "    ],\n"
            + "    \"model\": \"text-embedding-ada-002-v2\",\n"
            + "    \"usage\": {\n"
            + "        \"prompt_tokens\": 5,\n"
            + "        \"total_tokens\": 5\n"
            + "    }\n"
            + "}";
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        HttpEntity entity = new StringEntity(modelResponse);
        when(response.getEntity()).thenReturn(entity);
        when(executor.getConnector()).thenReturn(connector);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInput_LessEmbeddingThanInputDocs() throws IOException {
        String preprocessResult1 = "{\"parameters\": { \"input\": \"test doc1\" } }";
        String preprocessResult2 = "{\"parameters\": { \"input\": \"test doc2\" } }";
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult1))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult2));

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        Map<String, String> parameters = ImmutableMap.of("input_docs_processed_step_size", "2");
        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        executor.setScriptService(scriptService);
        when(httpClient.execute(any())).thenReturn(response);
        // model takes 2 input docs, but only output 1 embedding
        String modelResponse = "{\n"
            + "    \"object\": \"list\",\n"
            + "    \"data\": [\n"
            + "        {\n"
            + "            \"object\": \"embedding\",\n"
            + "            \"index\": 0,\n"
            + "            \"embedding\": [\n"
            + "                -0.014555434,\n"
            + "                -0.002135904,\n"
            + "                0.0035105038\n"
            + "            ]\n"
            + "        }    ],\n"
            + "    \"model\": \"text-embedding-ada-002-v2\",\n"
            + "    \"usage\": {\n"
            + "        \"prompt_tokens\": 5,\n"
            + "        \"total_tokens\": 5\n"
            + "    }\n"
            + "}";
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        HttpEntity entity = new StringEntity(modelResponse);
        when(response.getEntity()).thenReturn(entity);
        when(executor.getConnector()).thenReturn(connector);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
       executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }

    @Test
    public void executePredict_TextDocsInput_LessEmbeddingThanInputDocs_InvalidStepSize() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Invalid parameter: input_docs_processed_step_size. It must be positive integer.");
        String preprocessResult1 = "{\"parameters\": { \"input\": \"test doc1\" } }";
        String preprocessResult2 = "{\"parameters\": { \"input\": \"test doc2\" } }";
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult1))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult2));

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://openai.com/mock")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        // step size must be positive integer, here we set it as -1, should trigger IllegalArgumentException
        Map<String, String> parameters = ImmutableMap.of("input_docs_processed_step_size", "-1");
        HttpConnector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(executor.getClient()).thenReturn(client);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        executor.setScriptService(scriptService);
        when(httpClient.execute(any())).thenReturn(response);
        // model takes 2 input docs, but only output 1 embedding
        String modelResponse = "{\n"
            + "    \"object\": \"list\",\n"
            + "    \"data\": [\n"
            + "        {\n"
            + "            \"object\": \"embedding\",\n"
            + "            \"index\": 0,\n"
            + "            \"embedding\": [\n"
            + "                -0.014555434,\n"
            + "                -0.002135904,\n"
            + "                0.0035105038\n"
            + "            ]\n"
            + "        }    ],\n"
            + "    \"model\": \"text-embedding-ada-002-v2\",\n"
            + "    \"usage\": {\n"
            + "        \"prompt_tokens\": 5,\n"
            + "        \"total_tokens\": 5\n"
            + "    }\n"
            + "}";
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        HttpEntity entity = new StringEntity(modelResponse);
        when(response.getEntity()).thenReturn(entity);
        when(executor.getConnector()).thenReturn(connector);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build(), actionListener);
    }
}
