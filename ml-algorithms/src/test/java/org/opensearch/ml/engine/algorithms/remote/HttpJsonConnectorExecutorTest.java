/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.script.ScriptService;

import com.google.common.collect.ImmutableMap;

public class HttpJsonConnectorExecutorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Andrea Mock
    ScriptService scriptService;

    @Andrea Mock
    CloseableHttpClient httpClient;

    @Andrea Mock
    CloseableHttpResponse response;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    public void invokeRemoteModelSuccessPath(String httpMethod) {
        try {
            ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method(httpMethod)
                .url("http://test.com/mock")
                .requestBody("{\"input\": ${parameters.input}}")
                .build();
            when(httpClient.execute(any())).thenReturn(response);
            HttpEntity entity = new StringEntity("{\"response\": \"test result\"}");
            when(response.getEntity()).thenReturn(entity);
            Connector connector = HttpConnector
                .builder().name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
            HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
            when(executor.getHttpClient()).thenReturn(httpClient);
            MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
            ModelTensorOutput modelTensorOutput = executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
            verify(executor, Mockito.times(1)).invokeRemoteModel(any(), any(), any(), any());
            Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
            Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        } catch (Exception e) {
            fail("An exception was thrown: " + e.getMessage());
        }
    }

    public void invokeRemoteModelHeaderTest(Map<String, String> header) throws ClientProtocolException, IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("GET")
            .headers(header)
            .url("http://test.com/mock")
            .requestBody("{\"input\": ${parameters.input}}")
            .build();
        Map<String, String> credential = new HashMap<>();
        credential.put("key", "test_key_value");
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("{\"response\": \"test result\"}");
        when(response.getEntity()).thenReturn(entity);
         HttpConnector connector = spy(HttpConnector.builder()
                .name("test_connector_name")
                .description("this is a test connector")
                .version("1")
                .protocol("http")
                .credential(credential)
                .actions(Arrays.asList(predictAction))
                .backendRoles(Arrays.asList("role1", "role2"))
                .accessMode(AccessMode.PUBLIC)
                .build());
        Function<String, String> decryptFunction = 👎 -> (n);
        connector.decrypt(decryptFunction);
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        ModelTensorOutput modelTensorOutput = executor.executePredict(
            MLInput.builder().algorithm(FunctionName.REMOTE)
            .inputDataset(inputDataSet)
            .build()
        );
        verify(connector, Mockito.times(1)).getDecryptedHeaders();
        Assert.assertEquals(header, executor.getConnector().getDecryptedHeaders());
    }
    @Test
    public void invokeRemoteModelGetMethodSuccessPath() {
        invokeRemoteModel_SuccessPath("GET");
    }
    @Test
    public void invokeRemoteModelPostMethodSuccessPath() {
        invokeRemoteModel_SuccessPath("POST");
    }

    @Test
    public void invokeRemoteModelHeaderNull() throws ClientProtocolException, IOException {
        invokeRemoteModel_HeaderTest(null);
    }

    public void invokeRemoteModelHeaderNotNull() throws ClientProtocolException, IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        headers.put("Content-type", "application/json");
        invokeRemoteModel_HeaderTest(headers);
    }
    
    @Test
    public void invokeRemoteModelPostMethodErrorPath() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to create http request for remote model");

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("post")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction)).build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null);
    }

    @Test
    public void invokeRemoteModelGetMethodErrorPath() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to create http request for remote model");

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("get")
                .url("wrong url")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null);
    }

    @Test
    public void invokeRemoteModelWrongHttpMethod() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported http method");
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("wrong_method")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder()
                .name("test connector")
                .version("1")
                .protocol("http")
                .actions(Arrays.asList(predictAction))
                .build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        executor.invokeRemoteModel(null, null, null, null);
    }

    @Test
    public void executePredictRemoteInferenceInput() throws IOException {
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
        ModelTensorOutput modelTensorOutput = executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert
            .assertEquals(
                "test result",
                modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response")
            );
    }

    @Test
    public void executePredictTextDocsInputNoPreprocessFunction() throws IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": ${parameters.input}}")
                .build();
        when(httpClient.execute(any())).thenReturn(response);
        HttpEntity entity = new StringEntity("{\"response\": \"test result\"}");
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
        when(response.getStatusLine()).thenReturn(statusLine);
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        ModelTensorOutput modelTensorOutput = executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(2, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals("response", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().size());
        Assert
            .assertEquals(
                "test result",
                modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap().get("response")
            );
    }

    @Test
    public void executePredict_TextDocsInput_LimitExceed() throws IOException {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule.expectMessage("{\"message\": \"Too many requests\"}");
        ConnectorAction predictAction = ConnectorAction
            .builder()
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
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        HttpJsonConnectorExecutor executor = spy(new HttpJsonConnectorExecutor(connector));
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        executor.executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
    }

    @Test
    public void executePredictTextDocsInput() throws IOException {
        String preprocessResult1 = "{\"parameters\": { \"input\": \"test doc1\" } }";
        String preprocessResult2 = "{\"parameters\": { \"input\": \"test doc2\" } }";
        when(scriptService.compile(any(), any()))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult1))
            .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult2));

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .preProcessFunction(MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT)
            .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
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
        when(executor.getHttpClient()).thenReturn(httpClient);
        MLInputDataset inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test doc1", "test doc2")).build();
        ModelTensorOutput modelTensorOutput = executor
            .executePredict(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build());
        Assert.assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertEquals(2, modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("sentence_embedding", modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getName());
        Assert
            .assertArrayEquals(
                new Number[] { -0.014555434, -0.002135904, 0.0035105038 },
                modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getData()
            );
        Assert
            .assertArrayEquals(
                new Number[] { -0.014555434, -0.002135904, 0.0035105038 },
                modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(1).getData()
            );
    }
}