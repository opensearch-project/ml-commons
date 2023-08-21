/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
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
import org.opensearch.ml.common.utils.GsonUtil;
import org.opensearch.script.ScriptService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        ConnectorUtils.processInput(null, null, new HashMap<>(), null);
    }

    @Test
    public void processInput_TextDocsInputDataSet_NoPreprocessFunction() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Must provide pre_process_function for predict action to process text docs input.");
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("test1", "test2")).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        ConnectorUtils.processInput(mlInput, connector, new HashMap<>(), scriptService);
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

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").actions(Arrays.asList(predictAction)).build();
        ConnectorUtils.processInput(mlInput, connector, new HashMap<>(), scriptService);
        Assert.assertEquals(expectedInput, ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getParameters().get("input"));
    }

    @Test
    public void processInput_TextDocsInputDataSet_PreprocessFunction_OneTextDoc() {
        List<String> input = Collections.singletonList("test_value");
        String inputJson = GsonUtil.toJson(input);
        processInput_TextDocsInputDataSet_PreprocessFunction(
                "{\"input\": \"${parameters.input}\"}", input, inputJson, MLPreProcessFunction.TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT, "texts");
    }

    @Test
    public void processInput_TextDocsInputDataSet_PreprocessFunction_MultiTextDoc() {
        List<String> input = new ArrayList<>();
        input.add("test_value1");
        input.add("test_value2");
        String inputJson = GsonUtil.toJson(input);
        processInput_TextDocsInputDataSet_PreprocessFunction(
                "{\"input\": ${parameters.input}}", input, inputJson, MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT, "input");
    }

    @Test
    public void processOutput_NullResponse() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model response is null");
        ConnectorUtils.processOutput(null, null, null, null);
    }

    @Test
    public void processOutput_NoPostprocessFunction_jsonResponse() throws IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").parameters(parameters).actions(Arrays.asList(predictAction)).build();
        ModelTensors tensors = ConnectorUtils.processOutput("{\"response\": \"test response\"}", connector, scriptService, ImmutableMap.of());
        Assert.assertEquals(1, tensors.getMlModelTensors().size());
        Assert.assertEquals("response", tensors.getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, tensors.getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals("test response", tensors.getMlModelTensors().get(0).getDataAsMap().get("response"));
    }

    @Test
    public void processOutput_noPostProcessFunction_nonJsonResponse() throws IOException {
        ConnectorAction predictAction = ConnectorAction.builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").parameters(parameters).actions(Arrays.asList(predictAction)).build();
        ModelTensors tensors = ConnectorUtils.processOutput("test response", connector, scriptService, ImmutableMap.of());
        Assert.assertEquals(1, tensors.getMlModelTensors().size());
        Assert.assertEquals("response", tensors.getMlModelTensors().get(0).getName());
        Assert.assertEquals(1, tensors.getMlModelTensors().get(0).getDataAsMap().size());
        Assert.assertEquals("test response", tensors.getMlModelTensors().get(0).getDataAsMap().get("response"));
    }

    @Test
    public void processOutput_PostprocessFunction() throws IOException {
        String postprocessResult = "{\"name\":\"sentence_embedding\",\"data_type\":\"FLOAT32\",\"shape\":[1536],\"data\":[-0.014555434, -2.135904E-4, 0.0035105038]}";
        when(scriptService.compile(any(), any())).then(invocation -> new TestTemplateService.MockTemplateScript.Factory(postprocessResult));

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .postProcessFunction(MLPostProcessFunction.OPENAI_EMBEDDING)
                .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").parameters(parameters).actions(Arrays.asList(predictAction)).build();
        String modelResponse = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[-0.014555434,-0.0002135904,0.0035105038]}],\"model\":\"text-embedding-ada-002-v2\",\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}";
        ModelTensors tensors = ConnectorUtils.processOutput(modelResponse, connector, scriptService, ImmutableMap.of());
        Assert.assertEquals(1, tensors.getMlModelTensors().size());
        Assert.assertEquals("sentence_embedding", tensors.getMlModelTensors().get(0).getName());
        Assert.assertNull(tensors.getMlModelTensors().get(0).getDataAsMap());
        Assert.assertEquals(3, tensors.getMlModelTensors().get(0).getData().length);
        Assert.assertEquals(-0.014555434, tensors.getMlModelTensors().get(0).getData()[0]);
        Assert.assertEquals(-0.0002135904, tensors.getMlModelTensors().get(0).getData()[1]);
        Assert.assertEquals(0.0035105038, tensors.getMlModelTensors().get(0).getData()[2]);
    }

    private void processInput_TextDocsInputDataSet_PreprocessFunction(String requestBody, List<String> inputs, String expectedProcessedInput, String preProcessName, String resultKey) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(inputs).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("POST")
                .url("http://test.com/mock")
                .requestBody(requestBody)
                .preProcessFunction(preProcessName)
                .build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").parameters(parameters).actions(Arrays.asList(predictAction)).build();
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = ConnectorUtils.processInput(mlInput, connector, new HashMap<>(), scriptService);
        Assert.assertNotNull(remoteInferenceInputDataSet.getParameters());
        Assert.assertEquals(1, remoteInferenceInputDataSet.getParameters().size());
        Assert.assertEquals(expectedProcessedInput, remoteInferenceInputDataSet.getParameters().get(resultKey));
    }
}
