/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;

public class BedrockEmbeddingPostProcessFunctionTest {
    BedrockEmbeddingPostProcessFunction function;

    @Before
    public void setUp() {
        function = new BedrockEmbeddingPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply("abc", null));
        assertEquals("Post process function input is not a List.", exception.getMessage());
    }

    @Test
    public void process_WrongInput_NotNumberList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply(Arrays.asList("abc"), null));
        assertEquals("The embedding should be a non-empty List containing Float values.", exception.getMessage());
    }

    @Test
    public void process_CorrectInput() {
        List<ModelTensor> result = function.apply(Arrays.asList(1.1, 1.2, 1.3), null);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getData().length);
        assertEquals(1.1, result.get(0).getData()[0]);
        assertEquals(1.2, result.get(0).getData()[1]);
        assertEquals(1.3, result.get(0).getData()[2]);
    }
}
