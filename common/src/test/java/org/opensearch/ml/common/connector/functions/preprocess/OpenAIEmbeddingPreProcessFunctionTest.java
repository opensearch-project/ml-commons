/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class OpenAIEmbeddingPreProcessFunctionTest {
    OpenAIEmbeddingPreProcessFunction function;

    TextSimilarityInputDataSet textSimilarityInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;

    @Before
    public void setUp() {
        function = new OpenAIEmbeddingPreProcessFunction();
        textSimilarityInputDataSet = TextSimilarityInputDataSet.builder().queryText("test").textDocs(Arrays.asList("hello")).build();
        textDocsInputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("hello", "world")).build();
    }

    @Test
    public void process_NullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply(null));
        assertEquals("Preprocess function input can't be null", exception.getMessage());
    }

    @Test
    public void process_WrongInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(textSimilarityInputDataSet).build();
            function.apply(mlInput);
        });
        assertEquals(
            "This pre_process_function can only support TextDocsInputDataSet which including a list of string with key 'text_docs'",
            exception.getMessage()
        );
    }

    @Test
    public void process_CorrectInput() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(1, dataSet.getParameters().size());
        assertEquals("[\"hello\",\"world\"]", dataSet.getParameters().get("input"));
    }
}
