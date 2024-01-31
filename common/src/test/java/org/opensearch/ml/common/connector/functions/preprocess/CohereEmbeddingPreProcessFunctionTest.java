/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CohereEmbeddingPreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    CohereEmbeddingPreProcessFunction function;

    TextSimilarityInputDataSet textSimilarityInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;

    @Before
    public void setUp() {
        function = new CohereEmbeddingPreProcessFunction();
        textSimilarityInputDataSet = TextSimilarityInputDataSet.builder().queryText("test").textDocs(Arrays.asList("hello")).build();
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
        exceptionRule.expectMessage("This pre_process_function can only support TextDocsInputDataSet");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(textSimilarityInputDataSet).build();
        function.apply(mlInput);
    }

    @Test
    public void process_CorrectInput() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("[\"hello\",\"world\"]", dataSet.getParameters().get("texts"));
    }
}
