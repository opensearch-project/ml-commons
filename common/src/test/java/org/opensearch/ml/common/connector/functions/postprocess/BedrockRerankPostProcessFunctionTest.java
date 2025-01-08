/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.output.model.ModelTensor;

public class BedrockRerankPostProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    BedrockRerankPostProcessFunction function;

    @Before
    public void setUp() {
        function = new BedrockRerankPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is not a List.");
        function.apply("abc");
    }

    @Test
    public void process_EmptyInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is empty.");
        function.apply(Arrays.asList());
    }

    @Test
    public void process_WrongInput_NotCorrectList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Rerank result is not a Map.");
        function.apply(Arrays.asList("abc"));
    }

    @Test
    public void process_EmptyMapInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Rerank result is empty.");
        function.apply(Arrays.asList(Map.of()));
    }

    @Test
    public void process_WrongInput_NotCorrectMap() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Rerank result should have both index and relevanceScore.");
        List<Map<String, Object>> rerankResults = List
            .of(
                Map.of("index", 2, "relevanceScore", 0.7711548805236816),
                Map.of("index", 0, "relevanceScore", 0.0025114635936915874),
                Map.of("index", 1, "relevanceScore", 2.4876489987946115e-05),
                Map.of("test1", "value1")
            );
        function.apply(rerankResults);
    }

    @Test
    public void process_WrongInput_NotCorrectRelevanceScore() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("relevanceScore is not BigDecimal or Double.");
        List<Map<String, Object>> rerankResults = List
            .of(
                Map.of("index", 2, "relevanceScore", 0.7711548805236816),
                Map.of("index", 0, "relevanceScore", 0.0025114635936915874),
                Map.of("index", 1, "relevanceScore", 2.4876489987946115e-05),
                Map.of("index", 3, "relevanceScore", "value1")
            );
        function.apply(rerankResults);
    }

    @Test
    public void process_CorrectInput() {
        List<Map<String, Object>> rerankResults = List
            .of(
                Map.of("index", 2, "relevanceScore", 0.7711548805236816),
                Map.of("index", 0, "relevanceScore", 0.0025114635936915874),
                Map.of("index", 1, "relevanceScore", 2.4876489987946115e-05),
                Map.of("index", 3, "relevanceScore", 6.339210358419223e-06)
            );
        List<ModelTensor> result = function.apply(rerankResults);
        assertEquals(4, result.size());
        assertEquals(1, result.get(0).getData().length);
        assertEquals(0.0025114635936915874, result.get(0).getData()[0]);
        assertEquals(2.4876489987946115e-05, result.get(1).getData()[0]);
        assertEquals(0.7711548805236816, result.get(2).getData()[0]);
        assertEquals(6.339210358419223e-06, result.get(3).getData()[0]);
    }
}
