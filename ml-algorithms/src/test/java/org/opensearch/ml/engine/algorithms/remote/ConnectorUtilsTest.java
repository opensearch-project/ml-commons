/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_PREDICT_STATUS;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.CANCEL_BATCH_PREDICT;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

import com.google.common.collect.ImmutableMap;

import okhttp3.Request;

public class ConnectorUtilsTest {

    @Mock
    ScriptService scriptService;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void processInput_NullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input is null");
        ConnectorUtils.processInput(PREDICT.name(), null, null, new HashMap<>(), null);
    }

    @Test
    public void processInput_TextDocsInputDataSet_NoPreprocessFunction() {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test1", "test2")).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        ConnectorUtils.processInput(PREDICT.name(), mlInput, connector, new HashMap<>(), scriptService);
    }

    @Test
    public void processInput_RemoteInferenceInputDataSet_EscapeString() {
        String input = "hello \"world\" \n \t";
        String expectedInput = "hello \\\"world\\\" \\n \\t";
        processInput_RemoteInferenceInputDataSet(input, expectedInput);
    }

    @Test
    public void processInput_RemoteInferenceInputDataSet_NotEscapeStringValue() {
        String input = "test value";
        processInput_RemoteInferenceInputDataSet(input, input);
    }

    @Test
    public void processInput_RemoteInferenceInputDataSet_NotEscapeArrayString() {
        String input = "[\"test value1\"]";
        processInput_RemoteInferenceInputDataSet(input, input);
    }

    @Test
    public void processInput_RemoteInferenceInputDataSet_NotEscapeJsonString() {
        String input = "{\"key1\": \"value\", \"key2\": 123}";
        processInput_RemoteInferenceInputDataSet(input, input);
    }

    @Test
    public void processInput_RemoteInferenceInputDataSet_NullParam() {
        String input = null;
        processInput_RemoteInferenceInputDataSet(input, input);
    }

    private void processInput_RemoteInferenceInputDataSet(String input, String expectedInput) {
        Map<String, String> params = new HashMap<>();
        params.put("input", input);
        RemoteInferenceInputDataSet dataSet = RemoteInferenceInputDataSet.builder().parameters(params).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        ConnectorUtils.processInput(PREDICT.name(), mlInput, connector, new HashMap<>(), scriptService);
        assertEquals(expectedInput, ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getParameters().get("input"));
    }

    @Test
    public void processInput_TextDocsInputDataSet_PreprocessFunction_OneTextDoc() {
        List<String> input = Collections.singletonList("test_value");
        String inputJson = gson.toJson(input);
        processInput_TextDocsInputDataSet_PreprocessFunction(
            "{\"input\": \"${parameters.input}\"}",
            input,
            inputJson,
            MLPreProcessFunction.TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT,
            "texts"
        );
    }

    @Test
    public void processInput_TextDocsInputDataSet_PreprocessFunction_MultiTextDoc() {
        List<String> input = new ArrayList<>();
        input.add("test_value1");
        input.add("test_value2");
        String inputJson = gson.toJson(input);
        processInput_TextDocsInputDataSet_PreprocessFunction(
            "{\"input\": ${parameters.input}}",
            input,
            inputJson,
            MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT,
            "input"
        );
    }

    @Test
    public void processOutput_NullResponse() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model response is null");
        ConnectorUtils.processOutput(PREDICT.name(), null, null, null, null, null);
    }

    @Test
    public void processOutput_NoPostprocessFunction_jsonResponse() throws IOException {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
        String modelResponse =
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[-0.014555434,-0.0002135904,0.0035105038]}],\"model\":\"text-embedding-ada-002-v2\",\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}";
        ModelTensors tensors = ConnectorUtils
            .processOutput(PREDICT.name(), modelResponse, connector, scriptService, ImmutableMap.of(), null);
        assertEquals(1, tensors.getMlModelTensors().size());
        assertEquals("response", tensors.getMlModelTensors().get(0).getName());
        assertEquals(4, tensors.getMlModelTensors().get(0).getDataAsMap().size());
    }

    @Test
    public void processOutput_PostprocessFunction() throws IOException {
        String postprocessResult =
            "{\"name\":\"sentence_embedding\",\"data_type\":\"FLOAT32\",\"shape\":[1536],\"data\":[-0.014555434, -2.135904E-4, 0.0035105038]}";
        when(scriptService.compile(any(), any())).then(invocation -> new TestTemplateService.MockTemplateScript.Factory(postprocessResult));

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
            .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
        String modelResponse =
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[-0.014555434,-0.0002135904,0.0035105038]}],\"model\":\"text-embedding-ada-002-v2\",\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}";
        ModelTensors tensors = ConnectorUtils
            .processOutput(PREDICT.name(), modelResponse, connector, scriptService, ImmutableMap.of(), null);
        assertEquals(1, tensors.getMlModelTensors().size());
        assertEquals("sentence_embedding", tensors.getMlModelTensors().get(0).getName());
        assertNull(tensors.getMlModelTensors().get(0).getDataAsMap());
        assertEquals(3, tensors.getMlModelTensors().get(0).getData().length);
        assertEquals(-0.014555434, tensors.getMlModelTensors().get(0).getData()[0]);
        assertEquals(-0.0002135904, tensors.getMlModelTensors().get(0).getData()[1]);
        assertEquals(0.0035105038, tensors.getMlModelTensors().get(0).getData()[2]);
    }

    private void processInput_TextDocsInputDataSet_PreprocessFunction(
        String requestBody,
        List<String> inputs,
        String expectedProcessedInput,
        String preProcessName,
        String resultKey
    ) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(inputs).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody(requestBody)
            .preProcessFunction(preProcessName)
            .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .parameters(parameters)
            .actions(Arrays.asList(predictAction))
            .build();
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = ConnectorUtils
            .processInput(PREDICT.name(), mlInput, connector, new HashMap<>(), scriptService);
        assertNotNull(remoteInferenceInputDataSet.getParameters());
        assertEquals(1, remoteInferenceInputDataSet.getParameters().size());
        assertEquals(expectedProcessedInput, remoteInferenceInputDataSet.getParameters().get(resultKey));
    }

    @Test
    public void testGetTask_createBatchStatusActionForSageMaker() {
        Connector connector1 = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                new ArrayList<>(
                    Arrays
                        .asList(
                            ConnectorAction
                                .builder()
                                .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
                                .method("POST")
                                .url("https://api.sagemaker.us-east-1.amazonaws.com/CreateTransformJob")
                                .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                                .requestBody("{ \"TransformJobName\" : \"${parameters.TransformJobName}\"}")
                                .build()
                        )
                )
            )
            .build();

        ConnectorAction result = ConnectorUtils.createConnectorAction(connector1, BATCH_PREDICT_STATUS);

        assertEquals(ConnectorAction.ActionType.BATCH_PREDICT_STATUS, result.getActionType());
        assertEquals("POST", result.getMethod());
        assertEquals("https://api.sagemaker.us-east-1.amazonaws.com/DescribeTransformJob", result.getUrl());
        assertEquals("{ \"TransformJobName\" : \"${parameters.TransformJobName}\"}", result.getRequestBody());
        assertTrue(result.getHeaders().containsKey("Authorization"));

    }

    @Test
    public void testGetTask_createBatchStatusActionForOpenAI() {
        Connector connector1 = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                new ArrayList<>(
                    Arrays
                        .asList(
                            ConnectorAction
                                .builder()
                                .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
                                .method("POST")
                                .url("https://api.openai.com/v1/batches")
                                .headers(Map.of("Authorization", "Bearer ${credential.openAI_key}"))
                                .requestBody("{ \\\"input_file_id\\\": \\\"${parameters.input_file_id}\\\" }")
                                .build()
                        )
                )
            )
            .build();

        ConnectorAction result = ConnectorUtils.createConnectorAction(connector1, BATCH_PREDICT_STATUS);

        assertEquals(ConnectorAction.ActionType.BATCH_PREDICT_STATUS, result.getActionType());
        assertEquals("GET", result.getMethod());
        assertEquals("https://api.openai.com/v1/batches/${parameters.id}", result.getUrl());
        assertNull(result.getRequestBody());
        assertTrue(result.getHeaders().containsKey("Authorization"));
    }

    @Test
    public void testGetTask_createCancelBatchActionForBedrock() {
        Connector connector1 = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                new ArrayList<>(
                    Arrays
                        .asList(
                            ConnectorAction
                                .builder()
                                .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
                                .method("POST")
                                .url("https://bedrock.${parameters.region}.amazonaws.com/model-invocation-job")
                                .requestBody(
                                    "{\\\"inputDataConfig\\\":{\\\"s3InputDataConfig\\\":{\\\"s3Uri\\\":\\\"${parameters.input_s3Uri}\\\"}},\\\"jobName\\\":\\\"${parameters.job_name}\\\",\\\"modelId\\\":\\\"${parameters.model}\\\",\\\"outputDataConfig\\\":{\\\"s3OutputDataConfig\\\":{\\\"s3Uri\\\":\\\"${parameters.output_s3Uri}\\\"}},\\\"roleArn\\\":\\\"${parameters.role_arn}\\\"}"
                                )
                                .postProcessFunction("connector.post_process.bedrock.batch_job_arn")
                                .build()
                        )
                )
            )
            .build();

        ConnectorAction result = ConnectorUtils.createConnectorAction(connector1, CANCEL_BATCH_PREDICT);

        assertEquals(ConnectorAction.ActionType.CANCEL_BATCH_PREDICT, result.getActionType());
        assertEquals("POST", result.getMethod());
        assertEquals(
            "https://bedrock.${parameters.region}.amazonaws.com/model-invocation-job/${parameters.processedJobArn}/stop",
            result.getUrl()
        );
        assertNull(result.getRequestBody());
    }

    @Test
    public void testEscapeRemoteInferenceInputData_WithSpecialCharacters() {
        Map<String, String> params = new HashMap<>();
        params.put("key1", "hello \"world\" \n \t");
        params.put("key2", "test value");

        RemoteInferenceInputDataSet inputData = RemoteInferenceInputDataSet.builder().parameters(params).build();

        ConnectorUtils.escapeRemoteInferenceInputData(inputData);

        assertEquals("hello \\\"world\\\" \\n \\t", inputData.getParameters().get("key1"));
        assertEquals("test value", inputData.getParameters().get("key2"));
    }

    @Test
    public void testEscapeRemoteInferenceInputData_WithJsonValues() {
        Map<String, String> params = new HashMap<>();
        params.put("key1", "{\"name\": \"test\", \"value\": 123}");
        params.put("key2", "[\"item1\", \"item2\"]");

        RemoteInferenceInputDataSet inputData = RemoteInferenceInputDataSet.builder().parameters(params).build();

        ConnectorUtils.escapeRemoteInferenceInputData(inputData);

        assertEquals("{\"name\": \"test\", \"value\": 123}", inputData.getParameters().get("key1"));
        assertEquals("[\"item1\", \"item2\"]", inputData.getParameters().get("key2"));
    }

    @Test
    public void testEscapeRemoteInferenceInputData_WithNoEscapeParams() {
        Map<String, String> params = new HashMap<>();
        String inputKey1 = "hello \"world\"";
        String inputKey3 = "special \"chars\"";
        params.put("key1", inputKey1);
        params.put("key2", "test value");
        params.put("key3", inputKey3);
        params.put("NO_ESCAPE_PARAMS", "key1,key3");

        RemoteInferenceInputDataSet inputData = RemoteInferenceInputDataSet.builder().parameters(params).build();

        ConnectorUtils.escapeRemoteInferenceInputData(inputData);

        String expectedKey1 = "hello \\\"world\\\"";
        String expectedKey3 = "special \\\"chars\\\"";
        assertEquals(expectedKey1, inputData.getParameters().get("key1"));
        assertEquals("test value", inputData.getParameters().get("key2"));
        assertEquals(expectedKey3, inputData.getParameters().get("key3"));
    }

    @Test
    public void buildSdkRequest_InvalidEndpoint_ThrowException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage(
                "Encountered error when trying to create uri from endpoint in ml connector. Please update the endpoint in connection configuration:"
            );
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("invalid-endpoint")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .actions(Arrays.asList(predictAction))
            .build();
        ConnectorUtils.buildSdkRequest("PREDICT", connector, Collections.emptyMap(), "{}", software.amazon.awssdk.http.SdkHttpMethod.POST);
    }

    @Test
    public void testBuildOKHttpRequestPOST_WithPayload() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .headers(ImmutableMap.of("Authorization", "Bearer token123"))
            .build();

        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();

        connector = spy(connector);
        when(connector.getDecryptedHeaders()).thenReturn(ImmutableMap.of("Authorization", "Bearer token123"));

        Map<String, String> parameters = ImmutableMap.of("input", "test input");
        String payload = "{\"input\": \"test input\"}";

        Request request = ConnectorUtils.buildOKHttpRequestPOST(PREDICT.name(), connector, parameters, payload);

        assertEquals("POST", request.method());
        assertEquals("http://test.com/mock", request.url().toString());
        assertEquals("Bearer token123", request.header("Authorization"));
        assertEquals("", request.header("Accept-Encoding"));
        assertEquals("text/event-stream", request.header("Accept"));
        assertEquals("no-cache", request.header("Cache-Control"));
        assertNotNull(request.body());
    }

    @Test
    public void testBuildOKHttpRequestPOST_NullPayload() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Content length is 0. Aborting request to remote model");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();

        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();

        Map<String, String> parameters = new HashMap<>();
        ConnectorUtils.buildOKHttpRequestPOST(PREDICT.name(), connector, parameters, null);
    }

    @Test
    public void testBuildOKHttpRequestPOST_NoHeaders() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();

        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();

        Map<String, String> parameters = new HashMap<>();
        String payload = "{\"input\": \"test input\"}";

        Request request = ConnectorUtils.buildOKHttpRequestPOST(PREDICT.name(), connector, parameters, payload);

        assertEquals("POST", request.method());
        assertEquals("http://test.com/mock", request.url().toString());
        assertNull(request.header("Authorization"));
        assertEquals("", request.header("Accept-Encoding"));
        assertEquals("text/event-stream", request.header("Accept"));
        assertEquals("no-cache", request.header("Cache-Control"));
    }

    @Test
    public void testBuildOKHttpRequestPOST_WithParameters() {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http://test.com/mock/${parameters.model}")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();

        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();

        Map<String, String> parameters = ImmutableMap.of("model", "gpt-3.5", "input", "test input");
        String payload = "{\"input\": \"test input\"}";

        Request request = ConnectorUtils.buildOKHttpRequestPOST(PREDICT.name(), connector, parameters, payload);

        assertEquals("POST", request.method());
        assertEquals("http://test.com/mock/gpt-3.5", request.url().toString());
    }
}
