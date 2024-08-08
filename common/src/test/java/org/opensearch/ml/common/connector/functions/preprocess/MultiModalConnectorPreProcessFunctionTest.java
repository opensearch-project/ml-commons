/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class MultiModalConnectorPreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MultiModalConnectorPreProcessFunction function;

    TextSimilarityInputDataSet textSimilarityInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;
    RemoteInferenceInputDataSet remoteInferenceInputDataSet;

    MLInput textEmbeddingInput;
    MLInput textSimilarityInput;
    MLInput remoteInferenceInput;

    @Before
    public void setUp() {
        function = new MultiModalConnectorPreProcessFunction();
        textSimilarityInputDataSet = TextSimilarityInputDataSet.builder().queryText("test").textDocs(Arrays.asList("hello")).build();
        textDocsInputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("hello", "world")).build();
        remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(Map.of("inputText", "value1", "inputImage", "value2"))
            .build();

        textEmbeddingInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        textSimilarityInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(textSimilarityInputDataSet).build();
        remoteInferenceInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
    }

    @Test
    public void testProcess_whenNullInput_expectIllegalArgumentException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Preprocess function input can't be null");
        function.apply(null);
    }

    @Test
    public void testProcess_whenWrongInput_expectIllegalArgumentException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("This pre_process_function can only support TextDocsInputDataSet");
        function.apply(textSimilarityInput);
    }

    @Test
    public void testProcess_whenCorrectInput_expectCorrectOutput() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(2, dataSet.getParameters().size());
        assertEquals("hello", dataSet.getParameters().get("inputText"));
        assertEquals("world", dataSet.getParameters().get("inputImage"));
    }

    @Test
    public void testProcess_whenInputTextOnly_expectInputTextShowUp() {
        TextDocsInputDataSet textDocsInputDataSet1 = TextDocsInputDataSet.builder().docs(Arrays.asList("hello")).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet1).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("hello", dataSet.getParameters().get("inputText"));
    }

    @Test
    public void testProcess_whenInputTextIsnull_expectIllegalArgumentException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No input text or image provided");
        List<String> docs = new ArrayList<>();
        docs.add(null);
        TextDocsInputDataSet textDocsInputDataSet1 = TextDocsInputDataSet.builder().docs(docs).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet1).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
    }

    @Test
    public void testProcess_whenRemoteInferenceInput_expectRemoteInferenceInputDataSet() {
        RemoteInferenceInputDataSet dataSet = function.apply(remoteInferenceInput);
        assertEquals(remoteInferenceInputDataSet, dataSet);
    }
}
