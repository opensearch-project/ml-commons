/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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

public class ImageEmbeddingPreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    ImageEmbeddingPreProcessFunction function;

    TextSimilarityInputDataSet textSimilarityInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;
    RemoteInferenceInputDataSet remoteInferenceInputDataSet;

    MLInput textEmbeddingInput;
    MLInput textSimilarityInput;
    MLInput remoteInferenceInput;

    @Before
    public void setUp() {
        function = new ImageEmbeddingPreProcessFunction();
        textSimilarityInputDataSet = TextSimilarityInputDataSet.builder().queryText("test").textDocs(Arrays.asList("hello")).build();
        textDocsInputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("hello", "world")).build();
        remoteInferenceInputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("key1", "value1", "key2", "value2")).build();

        textEmbeddingInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        textSimilarityInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(textSimilarityInputDataSet).build();
        remoteInferenceInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build();
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
        exceptionRule.expectMessage("This pre_process_function can only support TextDocsInputDataSet");
        function.apply(textSimilarityInput);
    }

    @Test
    public void process_CorrectInput() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("hello", dataSet.getParameters().get("inputImage"));
    }

    @Test
    public void process_EmptyDocs() {
        TextDocsInputDataSet mockDataSet = mock(TextDocsInputDataSet.class);
        when(mockDataSet.getDocs()).thenReturn(Collections.emptyList());
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(mockDataSet).build();

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No input image provided");
        function.apply(mlInput);
    }

    @Test
    public void process_RemoteInferenceInput() {
        RemoteInferenceInputDataSet dataSet = function.apply(remoteInferenceInput);
        assertEquals(remoteInferenceInputDataSet, dataSet);
    }
}
