/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
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
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.script.ScriptService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HttpJsonConnectorExecutorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    ScriptService scriptService;

    @Mock
    CloseableHttpClient httpClient;

    @Mock
    CloseableHttpResponse response;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void invokeRemoteModel_WrongHttpMethod() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported http method");
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("wrong_method")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null);
    }

    @Test
    public void executePredict_RemoteInferenceInput() throws IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("{\"response\": \"test result\"}");
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(ImmutableMap.of("input", "test input data")).build();
        ModelTensorOutput modelTensorOutput = executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals("test result", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response"));
    }

    @Test
    public void executePredict_TextDocsInput_NoPreprocessFunction() throws IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": ${parameters.input}}")
                .build();
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("[{\"response\": \"test result1\"}, {\"response\": \"test result2\"}]");
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        ModelTensorOutput modelTensorOutput = executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        // If TextDocsInputDataSet has no preprocess function, the preprocess function will be set to MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT.
        // This default preprocess function will process the input into a list of strings format and for now all TextDocsInputDataSet is for text embedding
        // including dense embedding and sparse embedding which both case accepts list of string format. For this input format, the result will
        // always be a single MLModelOutput with a single MLModelTensor with a dataAsMap with key "response" and value is the original result from
        // remote interface including a list of embeddings or a list of objects(sparse embedding).
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals(2, ((List)modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response")).size());
        Assert.assertEquals("test result1",
            Optional.of(modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response"))
                .map(x -> ((List) x).get(0))
                .map(x -> ((Map) x).get("response"))
                .get()
        );
        Assert.assertEquals("test result2",
            Optional.of(modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response"))
                .map(x -> ((List) x).get(1))
                .map(x -> ((Map) x).get("response"))
                .get()
        );
    }

    @Test
    public void executePredict_TextDocsInput_LimitExceed() throws IOException {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule.expectMessage("{\"message\": \"Too many requests\"}");
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": ${parameters.input}}")
                .build();
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("{\"message\": \"Too many requests\"}");
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 429, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
    }

    @Test
    public void executePredict_TextDocsInput() throws IOException {
        String preprocessResult1 = "{\"parameters\": { \"input\": \"test doc1\" } }";
        String preprocessResult2 = "{\"parameters\": { \"input\": \"test doc2\" } }";
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult1))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult2));

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
                .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
                .requestBody("{\"input\": ${parameters.input}}")
                .build();
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        executor.setScriptService(scriptService);
        when(httpClient.execute(any())).thenReturn(response);
        String modelResponse = "{\n" + "    \"object\": \"list\",\n" + "    \"data\": [\n" + "        {\n"
            + "            \"object\": \"embedding\",\n" + "            \"index\": 0,\n" + "            \"embedding\": [\n"
            + "                -0.014555434,\n" + "                -0.002135904,\n" + "                0.0035105038\n" + "            ]\n"
            + "        },\n" + "        {\n" + "            \"object\": \"embedding\",\n" + "            \"index\": 1,\n"
            + "            \"embedding\": [\n" + "                -0.014555434,\n" + "                -0.002135904,\n"
            + "                0.0035105038\n" + "            ]\n" + "        }\n" + "    ],\n"
            + "    \"model\": \"text-embedding-ada-002-v2\",\n" + "    \"usage\": {\n" + "        \"prompt_tokens\": 5,\n"
            + "        \"total_tokens\": 5\n" + "    }\n" + "}";
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        HttpEntity entity = new StringEntity(modelResponse);
        when(response.getEntity()).thenReturn(entity);
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        ModelTensorOutput modelTensorOutput = executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals(2, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("sentence_embedding", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertArrayEquals(new Number[] {-0.014555434, -0.002135904, 0.0035105038}, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getData());
        Assert.assertArrayEquals(new Number[] {-0.014555434, -0.002135904, 0.0035105038}, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(1).getData());
    }
}
