/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.ScriptService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RemoteInferencePreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RemoteInferencePreProcessFunction function;

    @Mock
    ScriptService scriptService;

    String preProcessFunction;

    RemoteInferenceInputDataSet remoteInferenceInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;
    Map<String, String> predictParameter;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        preProcessFunction = "";
        predictParameter = new HashMap<>();
        function = new RemoteInferencePreProcessFunction(scriptService, preProcessFunction, predictParameter);
        remoteInferenceInputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("key1", "value1", "key2", "value2")).build();
        textDocsInputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("hello", "world")).build();
    }

    @Test
    public void process_NullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function input can't be null");
        function.apply(null);
    }

    @Test
    public void process_WrongInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("This pre_process_function can only support RemoteInferenceInputDataSet");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        function.apply(mlInput);
    }

    @Test
    public void process_CorrectInput_WrongProcessedResult() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function output is null");
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(null));
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
        function.apply(mlInput);
    }

    @Test
    public void process_CorrectInput() {
        String preprocessResult = "{\"parameters\": { \"input\": \"test doc1\" } }";
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult));
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("test doc1", dataSet.getParameters().get("input"));
    }
}
