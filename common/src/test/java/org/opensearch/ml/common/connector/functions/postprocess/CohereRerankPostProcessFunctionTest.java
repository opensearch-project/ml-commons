/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;

public class CohereRerankPostProcessFunctionTest {
    CohereRerankPostProcessFunction function;

    @Before
    public void setUp() {
        function = new CohereRerankPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply("abc", null));
        assertEquals("Post process function input is not a List.", exception.getMessage());
    }

    @Test
    public void process_WrongInput_NotCorrectList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply(Arrays.asList("abc"), null));
        assertEquals("Post process function input is not a List of Map.", exception.getMessage());
    }

    @Test
    public void process_WrongInput_NotCorrectMap() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> function.apply(Arrays.asList(Map.of("test1", "value1")), null)
        );
        assertEquals("The rerank result should contain index and relevance_score.", exception.getMessage());
    }

    @Test
    public void process_CorrectInput() {
        List<Map<String, Object>> rerankResults = List
            .of(
                Map.of("index", 2, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.4),
                Map.of("index", 0, "relevance_score", 0.3)
            );
        List<ModelTensor> result = function.apply(rerankResults, null);
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).getData().length);
        assertEquals(0.3, result.get(0).getData()[0]);
        assertEquals(0.4, result.get(1).getData()[0]);
        assertEquals(0.5, result.get(2).getData()[0]);
    }
}
