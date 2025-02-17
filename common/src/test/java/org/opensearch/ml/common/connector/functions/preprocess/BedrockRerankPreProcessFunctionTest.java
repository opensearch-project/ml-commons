/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class BedrockRerankPreProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    BedrockRerankPreProcessFunction function;

    TextSimilarityInputDataSet textSimilarityInputDataSet;
    TextDocsInputDataSet textDocsInputDataSet;

    @Before
    public void setUp() {
        function = new BedrockRerankPreProcessFunction();
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
        exceptionRule.expectMessage("This pre_process_function can only support TextSimilarityInputDataSet");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build();
        function.apply(mlInput);
    }

    @Test
    public void process_CorrectInput() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.TEXT_SIMILARITY).inputDataset(textSimilarityInputDataSet).build();
        RemoteInferenceInputDataSet dataSet = function.apply(mlInput);
        assertEquals(3, dataSet.getParameters().size());

        JSONArray expectedSources = new JSONArray(
            "[{\"type\": \"INLINE\", \"inlineDocumentSource\": {\"type\": \"TEXT\", \"textDocument\": {\"text\": \"hello\"}}}]"
        );
        JSONArray actualSources = new JSONArray(dataSet.getParameters().get("sources"));
        assertTrue(expectedSources.getJSONObject(0).similar(actualSources.getJSONObject(0)));

        JSONArray expectedQueries = new JSONArray("[{\"textQuery\": {\"text\": \"test\"}, \"type\": \"TEXT\"}]");
        JSONArray actualQueries = new JSONArray(dataSet.getParameters().get("queries"));
        assertTrue(expectedQueries.getJSONObject(0).similar(actualQueries.getJSONObject(0)));

        assertEquals("1", dataSet.getParameters().get("numberOfResults"));
    }
}
