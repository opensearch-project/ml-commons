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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DefaultPreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DefaultPreProcessFunction functionWithConvertToJsonString;
    DefaultPreProcessFunction functionWithoutConvertToJsonString;

    @Mock
    ScriptService scriptService;

    String preProcessFunction;

    TextDocsInputDataSet textDocsInputDataSet;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        preProcessFunction = "";
        functionWithConvertToJsonString = new DefaultPreProcessFunction(scriptService, preProcessFunction, true);
        functionWithoutConvertToJsonString = new DefaultPreProcessFunction(scriptService, preProcessFunction, false);
        textDocsInputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("hello", "world")).build();
    }

    @Test
    public void process_NullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function input can't be null");
        functionWithConvertToJsonString.apply(null);
    }

    @Test
    public void process_CorrectInput_WrongProcessedResult() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function output is null");
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(null));
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        functionWithConvertToJsonString.apply(mlInput);
    }

    @Test
    public void process_CorrectInput_WrongProcessedResult_WithoutConvertToJsonString() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function output is null");
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(null));
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        functionWithoutConvertToJsonString.apply(mlInput);
    }

    @Test
    public void process_CorrectInput() {
        String preprocessResult = "{\"parameters\": { \"input\": \"test doc1\" } }";
        when(scriptService.compile(any(), any()))
                .then(invocation -> new TestTemplateService.MockTemplateScript.Factory(preprocessResult));
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = functionWithConvertToJsonString.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("test doc1", dataSet.getParameters().get("input"));
    }
}
